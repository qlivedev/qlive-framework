# QueryDocumentService (design)

Status: built. Written and implemented 2026-09-07.

Rebuild of the service on `feat/redo-query-document-service`. The old
implementation was deleted first, on purpose: this is a fresh design, and
pieces of the old code get adopted where they solved a problem we hit
again, not by default.

## Problem

An application's query logic exposes generic `[T]` document queries over
GraphQL:

```java
return queryDocumentService.buildQuery(type, env, config)
    .selectByFilter(true)
    .execute();
```

Three inputs, one result:

- `Class<T> type` -- the jOOQ POJO type, and through
  `domainQL.lookupType()` the root table.
- `DataFetchingEnvironment env` -- the *shape*: which columns, which
  relations, how deep.
- `QueryConfig config` -- the *parameters*: condition, sort fields,
  offset, page size.

The result is a `QueryDocument<T>`: `rows` with their relations filled
in, `rowCount` ignoring paging, and the effective `config`. The client
echoes that config straight back into `update()`, so whatever the server
normalises becomes the client's next baseline.

Scope for now is database-backed types only. `T` is always a jOOQ POJO.

## What already exists, and why it decides the design

DomainQL already resolves relations during GraphQL execution:
`ReferenceFetcher` and `BackReferenceFetcher` run one query per parent
object. Both of them start like this:

```java
if (source instanceof DomainObject &&
    (fetcherContext = source.lookupFetcherContext()) != null)
{
    return fetcherContext.getProperty(relationModel.getLeftSideObjectName());
}
```

So the whole job is: fetch everything the selection asks for in as few
queries as possible, then hand every POJO a `FetcherContext`, and the
fetchers never touch the database. `QueryLogic.testQueryDocument` is a
hand-built example of the target shape.

That check has a sharp edge. It is **all or nothing**: once a context is
attached, a relation *missing* from it resolves to `null`, with no
fallback to a query. Therefore:

> Every relation edge in the GraphQL selection must be covered by the
> plan, and anything the planner cannot handle must fail loudly at plan
> time rather than silently null out a field.

This is the load-bearing invariant of the service.

A backwards-compatible fix in DomainQL is available if we ever need it --
distinguish "property absent" from "property is null" and fall back to
the query in the first case -- but the invariant above means we should
not need it.

## Pipeline

### Stage A -- plan

One tree, built from two sources in a strict order.

**1. The selection.** Walk `env.getSelectionSet()` below `rows` against
the schema.

- A scalar field is a column. `domainQL.lookupField(domainType,
  property)` gives the jOOQ `Field`, keyed off the POJO's `@Column` and
  its JSON name.
- An object field is a relation edge: the `RelationModel` where
  `sourceType == current && leftSideObjectName == fieldName` (forward,
  to-one), or `targetType == current && rightSideObjectName ==
  fieldName` (backward: to-many, or to-one when `TargetField.ONE`).

The same relation reached twice under different aliases (`owner { id }`
and `o2: owner { login }`) is one node with the union of the columns --
the fetcher hands the same POJO to both.

**2. The filter and sort paths**, resolved *against the finished
selection tree* (see "Filter and sort validation" below).

Nodes carry: aliased table, relation to the parent, selected columns,
children.

**Aliases** are built from the names the application already uses, so
that a framework user reading the SQL log recognizes their own query.
They follow database naming conventions: snake_case, single underscores
only.

- the root node is named after the domain type: `Foo` -> `foo`
- every other node is named after the relation field that reaches it,
  which is the name the GraphQL selection and the filter paths use as
  well: `Foo.owner` -> `owner`, not `app_user`; `Foo.fooType` ->
  `foo_type`
- a deeper path joins its segments with a single underscore:
  `bazLinks.baz` -> `baz_links_baz`

Identifiers convert the way the schema converts them: `fooId` ->
`foo_id`, `FooType` -> `foo_type`.

Names are truncated to fit the dialect's identifier limit -- 63
characters on Postgres -- and a numeric suffix is appended wherever
truncation, or a path collision, would otherwise produce the same alias
twice. Once double underscores are ruled out, `a_b` plus `c` and `a`
plus `b_c` both give `a_b_c`, so the collision case is real and not just
theoretical. Readability is the goal; uniqueness is the rule.

### Stage B -- SQL

- To-one edges become `LEFT JOIN` on the relation's source and target DB
  fields. Always LEFT, even for a `NOT NULL` foreign key: an inner join
  that silently drops rows is a worse failure than a null child.
- To-many edges are **never** joined into the main query. Row
  multiplication would break both `LIMIT` and `rowCount`.
- `WHERE` from the condition transformer, `ORDER BY` from the sort
  fields, `LIMIT`/`OFFSET` from the config.

Because only to-one joins are in play, one root row is one record. No
deduplication pass.

### Stage C -- rowCount

A second `SELECT count(*)` over the same WHERE, joining only what that
condition reads. Every join is a left join on a key, so none of them can
change a count and keeping them all would be correct -- it would just be
work nobody reads. What the planner records as it resolves the
condition's paths is the node each one ends at, or, for a path through a
to-many relation, the node above that relation, since everything from
there down is answered by the `EXISTS` subquery instead. Those nodes and
their ancestors are the joins; the rest are left out.

The count is skipped entirely when no limit is in play, where `rowCount`
is just `rows.size()`.

### Stage D -- materialise

Per plan node, a mapping from aliased column to POJO property, computed
**once per query**, not per row. A child whose primary key column came
back null (a LEFT join miss) becomes `null`, not an empty POJO. Each
parent gets a `FetcherContext` keyed by `leftSideObjectName` /
`rightSideObjectName`.

## Filter and sort validation

Every `Field` node reaching the planner -- in `config.condition` and in
`config.sortFields` alike, one rule and one code path -- is resolved
against the selection tree, not against the schema at large:

- `selectByFilter == false` (the default): the leaf must already be a
  selected column, and every segment on the way must already be a
  selected relation. Anything else is an error naming the path, never a
  silently added join.
- `selectByFilter == true`: the leaf column is added to the select list,
  and the edges it needs are added to the plan tree.

In the default mode the plan tree therefore comes entirely from the
GraphQL selection, and the config can only vary `WHERE`, `ORDER BY` and
`LIMIT` -- never the FROM/JOIN shape. That makes the query document,
which is static source text analyzed at build time, the security
boundary: a client cannot filter its way to a table the declared query
never mentioned. It is a stronger property than operator whitelisting,
and the two modes should differ only in whether a resolver miss throws
or extends the tree.

The generic `QueryLogic.queryDocument` passes `selectByFilter(true)`,
since it cannot know what a client will filter on. An application's own
logic bean gets the strict default. That means the strict path has no
caller yet and needs its own test rather than riding along on `Q_Foo`.

`Home.tsx`'s parked filter -- `field("name").eq(...)` or
`field("owner.login").eq(...)` against `Q_Foo`'s `name` and
`owner { id login }` -- satisfies the strict rule as written, which is
the evidence that the restrictive default is livable.

## To-many relations

The result shape mirrors the GraphQL selection exactly; nothing is
collapsed or invented. For an m:n relation reached through a link table,
`bazLinks { baz { name } }` produces a list of `BarLink` POJOs each
carrying its `Baz` -- which is also the only thing that can work, since
the GraphQL type of `bazLinks` is `[BarLink]` and the fetcher returns
whatever the context holds under that name. "Collapse to a simple list"
means each to-many level is a plain `List` of child POJOs, not a wrapper
or a nested record type.

m:n needs no special case in the planner: `Bar -> BarLink` is a to-many
back reference and `BarLink -> Baz` is a to-one foreign key. It falls
out of the two existing rules.

**Materialisation: follow-up queries.** Fetch the root page first (at
most `pageSize` rows), collect the parent keys, then run one query per
to-many edge with `WHERE fk IN (keys)`, and stitch the results into the
fetcher contexts. The query count is a function of the plan tree's size,
not of the row count, so there is no N+1.

The alternative is jOOQ's `MULTISET`, which would make it one round trip
(3.19.30 supports it, emulated over JSON aggregation on Postgres). It
was not chosen for the first cut because the nested `Result` still needs
hand-mapping into POJOs with fetcher contexts, the generated SQL is hard
to read in a log, and it quietly makes dialect support a framework
requirement. A to-many collection is unpaged and unfiltered anyway, so a
second query concedes nothing on correctness. The shared plan tree means
switching later touches only the execution class -- which is also why
there is no strategy interface for it: that would be a framework
abstraction with exactly one implementation.

## Conditions that cross a to-many relation

A condition path through a to-many edge cannot use the join, because
there is no join. It becomes a correlated `EXISTS`:

```sql
EXISTS (SELECT 1 FROM bar_link
        WHERE bar_link.bar_id = bar.id AND <sub-condition>)
```

This is part of the first cut, not a later addition.

**Where the boundary goes.** The `EXISTS` wraps the individual condition
node -- the comparison -- so that

```
bazLinks.baz.name eq "X" and bazLinks.baz.num gt 5
```

means "some link matches X" *and* "some link matches num > 5", possibly
different links. That is what a UI filter over an m:n relation ("has tag
A and has tag B") actually means. Requiring both to hold for the *same*
link row is a different and harder semantic; if it is ever wanted it
needs its own syntax, not a different default.

Negation composes naturally: `not` applied to a wrapped node gives
`NOT EXISTS`, i.e. "no link matches", which is the reading people
expect.

The path still has to satisfy the `selectByFilter` rule: filtering on
`bazLinks.baz.name` requires `bazLinks { baz { name } }` in the
selection unless `selectByFilter` is on.

**Sort fields may not cross a to-many edge.** Ordering by a set needs an
aggregate (MIN/MAX/count) that we would have to invent a syntax for.
Rejected with a clear error.

This pushes some resolution work into the planner, ahead of the
transformer: for each `Field` path, the planner classifies it as either
a column on an already-joined to-one alias, or a column below a to-many
edge that needs an `EXISTS` scope at some prefix. The transformer then
only ever sees paths it can resolve, plus scope nodes whose correlated
subquery the planner builds.

## The condition sub-service

`runtime/query/condition/`, usable without the document machinery -- a
logic bean that just wants to filter one table is a first-class caller.

**`FieldResolver`** -- resolves a path to a jOOQ `Field`. Inside a
document query the plan tree implements it (and, in `selectByFilter`
mode, extends itself on demand, which is why resolution happens during
planning and not during rendering). Outside it, a trivial single-table
resolver is enough.

**Operator whitelist** -- one flat positive list of names, checked
before anything else happens with a node:

```
greaterOrEqual, lessOrEqual, lt, notBetweenSymmetric,
notEqualIgnoreCase, betweenSymmetric, lessThan, equalIgnoreCase,
isDistinctFrom, between, ge, greaterThan, isNotNull, notLikeRegex,
notBetween, notEqual, isFalse, containsIgnoreCase, eq, gt, equal,
likeRegex, isTrue, contains, notContainsIgnoreCase, notContains, ne,
isNull, endsWith, le, isNotDistinctFrom, startsWith, in, not, or,
orNot, and, andNot, bitNand, mod, div, neg, rem, add, subtract, plus,
bitAnd, bitXor, shl, unaryMinus, bitNor, shr, modulo, bitXNor, bitNot,
sub, minus, mul, bitOr, times, pow, divide, power, multiply,
unaryPlus, lower, upper, asc, desc
```

Adopted from the old implementation. It covers conditions, logic
operators, value operations and the two sort operations in one set; the
node kind (`Condition` vs `Operation`) decides how a name is dispatched
after the gate.

**Value coercion is not the transformer's.** A condition arrives as
JSON, where a timestamp is a string, and reading those is the business
of the scalar that owns them. `ConditionCoercing` is DomainQLAware, so
it can ask DomainQL for the coercing registered for the scalar type a
value node names, and it converts every value in the hierarchy as the
parse walks it. What reaches the transformer is a condition whose values
are the Java objects they claim to be, and all it does with one is bind
it as the type of the field it is compared to. Values are always bound,
never inlined.

**Node quirks.** `Component` unwraps to its child -- it is a client-side
composition marker with no server meaning. Null operands inside `and` /
`or` are dropped, and an `and` with nothing left means no condition at
all. The client's FilterDSL emits both.

**`ComputedValue`.** `now()` and `today()` are evaluated by the
database: `DSL.currentTimestamp()` and `DSL.currentDate()`. Any other
name is an error.

## Decided details

- **`pageSize: 0` returns all rows.** No cap for now.
- **No sort fields means sort by the primary key**, so that paging is
  deterministic out of the box. A client-supplied sort is used exactly
  as given; the primary key is not appended to it (see open items).
- **The effective config is what goes back into the document**,
  including the defaulted sort, since that is what the client echoes
  into its next `update()`.
- **No row-level security hook.** Policies attached through DomainQL
  metadata are the intended direction, and that is a separate design.
  Nothing in this service should pre-empt it.
- **DomainQL may be changed**, but only backwards compatibly, or where
  the current behavior is clearly an error.
- **A config that reaches the service is complete.** The GraphQL and
  TypeScript types both say so, and the service reads `pageSize` and
  `offset` without asking whether they are there. `QueryConfigDelta` is
  how the client says "these fields only", and the one place a partial
  config exists on this side is a `useInjection()` call, whose static
  parameters the bootstrap service completes before they reach GraphQL
  at all.

## Build order

All five steps are built; the code is in `runtime/query` and
`runtime/query/condition`.

1. **Condition transformer, resolver, whitelist.** Unit-tested by
   rendering SQL against `DSL.using(POSTGRES)`. No database, no GraphQL.
   Useful on its own.
2. **Plan builder.** Selection plus filter and sort paths to tree,
   tested through the SQL it produces. `qlive`'s own test domain builds
   a `DomainQL` with a null `DSLContext`, so this stays database-free
   too.
3. **Execution, to-one only** -- joins, where/order/limit, rowCount,
   POJO plus fetcher context.
4. **To-many edges and the `EXISTS` scopes**, covered against
   Bar/BarLink/Baz in qlive-test, which is the only place with an m:n
   relation. The qlive test domain has no link table.
5. **Hardening** -- error messages, the strict `selectByFilter` path,
   join pruning in the count query.

The operator dispatch turned out to want no table of lambdas at all.
Every name on the positive list is a method of JOOQ's `Field` taking as
many `Field` arguments as the node has operands beyond the first, and
two methods of one interface cannot share an erasure, so a name plus an
arity names exactly one method. The list gates the lookup; `in` is the
only name whose argument is not a field, and its caller handles it.

## Open items (not decided)

- **Appending the primary key to a client-supplied sort.** Without it,
  paging over a non-unique sort key can repeat or skip rows; with it,
  the echoed effective config grows a field the client did not ask for.
  Only the empty case is decided.
- **`MULTISET` instead of follow-up queries.** Deliberately deferred,
  not rejected. The plan tree is the seam.
- **Same-row semantics for multiple conditions on one to-many path.**
  Needs its own syntax if it is ever wanted.
- **A page size cap.** `pageSize: 0` from a browser is an unbounded
  table scan. Out of scope now, worth revisiting with the security
  policy design.
