# A facade for DomainQL

Status: done. Written 2026-09-21, revised 2026-09-22 and 2026-09-25.

The analysis below is what the work was planned from and is left as it was
written, in the present tense of that day. The step list says what landed.

## Problem

`DomainQL` is 2,151 lines and is the type a framework user is handed.

Two of QLive's extension points take it as a parameter --
`MetadataProvider.provideMetaData(DomainQL, DomainQLMeta)` and
`DomainQLAware.setDomainQL(DomainQL)` -- and six Spring beans in
`QLiveConfiguration` are typed on it. So an application implementing either
SPI receives the whole schema assembler to use two getters, and nothing in
the wiring can be substituted, because there is no type to substitute.

That is the surface problem. The structural one underneath it is that
`DomainQL`'s constructor calls `buildGraphQLSchema()`. The class is a
builder wearing the result's name, which is why the interesting half of it
cannot be tested, mocked or reasoned about separately from the half that
gets used at runtime.

## What is actually used

Measured 2026-09-21 across `qlive`, `qlive-graphql`, `qlive-test` and their
tests.

`DomainQL` exposes 15 public accessors. Eight are reached from outside
`qlive-graphql`:

| Method | Called by |
| --- | --- |
| `getGraphQLSchema()` | the runtime, both SPIs, merge, injection, bootstrap |
| `getTypeRegistry()` | both SPIs, merge meta, query config meta |
| `getMetaData()` | `Util`, `DefaultBootstrapService` |
| `lookupField()` | `DefaultMergeService`, `QueryPlanBuilder` |
| `lookupType()` | `DefaultMergeService`, `QueryPlanBuilder` |
| `getPojoType()` | `QueryExecution`, `QueryPlanBuilder` |
| `getJooqTables()` | `DefaultMergeService` |
| `getRelationModels()` | `QueryPlanBuilder` |

Two more, `getJooqTable()` and `isNormalProperty()`, are used only within
`qlive-graphql`.

Seven have no caller anywhere: `getOptions()`, `getLogicBeans()`,
`getRelationModel(String)`, `getAdditionalQueries()`,
`getAdditionalMutations()`, `getAdditionalDirectives()` and
`getGenericTypes()`. The last is reachable but reached elsewhere: every
caller gets the generic types from `getMetaData().getGenericTypes()`,
where they live as a `DomainQLMeta` addendum.

**The SPI surface is two methods.** Every `MetadataProvider` in the tree --
`NameFieldProvider`, `ComputedMetadataProvider`,
`QueryConfigMetadataProvider`, `MergeMetadataProvider`, and
`ExampleMetadataProvider` in `qlive-test`, which is the structural template
an application copies -- calls `getGraphQLSchema()` and `getTypeRegistry()`
and nothing else. So does every one of the five `DomainQLAware` coercings.

Nothing subclasses `DomainQL`, and the constructor is package-private with
`DomainQLBuilder:169` as the single call site. So the type is already
sealed; giving it an interface is not a construction-path change.

## The class is a builder wearing the result's name

The public accessors begin at line 1981 of 2,151. Everything before them is
fields, the constructor, and schema assembly reachable only from it:
`registerTypes`, `defineInputTypes`, `defineOutputTypes`,
`defineQueriesAndMutations`, `defineEnumTypes`, `buildInputType`,
`buildEnumType`, `resolveOutputType`, `resolveInputType`, `degenerify` and
their helpers, across 70 private members.

Because assembly runs inside the constructor, `this` escapes three times
before the object is whole:

- `new TypeRegistry(this, additionalScalarTypes)` -- `TypeRegistry` stores
  it in a field and **never reads it**. Dead, removable on its own.
- `new LogicBeanAnalyzer(this, ...)`, constructed inside
  `buildGraphQLSchema()` while `graphQLSchema` is still null. Anything
  reached from there that calls `getGraphQLSchema()` gets null rather than
  an error.
- `relationModel.update(this)`, which wants `getTypeRegistry()` only.

Removing `@full` (step 0) already took away the one place that read
`DomainQL` *state* mid-construction, `isFullSupported()`. What remains in
`LogicBeanAnalyzer` is four calls to `domainQL.getTypeRegistry()` returning
the same object the analyzer already holds as its own `typeRegistry` field,
plus pass-through of the reference into `Query` and `Mutation`, which need
it later at fetch time and never during the build.

## TypeRegistry splits cleanly

`TypeRegistry` is 382 lines and mixes reads with build-time mutation, and
the two sets do not overlap in their callers:

- All 19 calls made through `DomainQL.getTypeRegistry()` are reads:
  `lookup`, `lookupInput`, `getGraphQLScalarFor`, `getScalarTypes`.
- All seven calls to the mutators `register` and `registerInput` are direct
  field access inside `DomainQL` and `LogicBeanAnalyzer`. Both are build
  path.

So the facade can expose a read-only `TypeRegistry` view without a single
caller changing behavior, and the mutators stop being reachable from an SPI
that has no business calling them. This matters more than the `DomainQL`
interface itself for testing: a fake `DomainQL` is worth little while the
`TypeRegistry` it must return is a concrete class with a build-time
constructor.

## The domain mapping is the registry

Five of the eight consumed methods -- `lookupType`, `getPojoType`,
`getJooqTables`, `lookupField`, `getRelationModels`, plus the internal
`getJooqTable` -- are not a second concern beside `TypeRegistry`. They
answer the same question it answers, about the same types, keyed the same
way: what do we know about this type in the current domain. `TypeRegistry`
answers the GraphQL half; these answer the Java and jOOQ half.

The evidence that they are one thing:

- Both are keyed on the domain type name. `TypeRegistry.lookup(String)`
  and `DomainQL.lookupType(String)` take the same key and are backed by
  two maps that are populated over the same set of types.
- `TypeRegistry` already answers the Java side, and the build forces the
  two to agree. `getOutputOverride(pojoClass)` is
  `lookup(pojoClass.getSimpleName()).getJavaType()`, and
  `updateTableLookups()` writes that result back into the `TableLookup`.
  `registerTypes()` then registers the rewritten `getPojoType()` into the
  registry, and defines the GraphQL type from `OutputType.getJavaType()`.
  The round trip closes: the registry is authoritative and the table
  lookup is kept synchronized to it. `getPojoType()` is a duplicate, not a
  method needing a new home.
- `TableLookup.getDomainType()` is `pojoType.getSimpleName()`: a registry
  entry that already derives its own key.
- `getRelationModels()` is a flat list, but its only main-code caller uses
  it as an index. `QueryPlanBuilder.relation()` scans the whole list
  filtering on source type and field name -- doing by hand what the thing
  holding the types should do.

So the last open question does not want a second interface. It wants
`TypeRegistry` to become what a domain type registry should have been, and
the facade then carries three methods: `getGraphQLSchema()`,
`getTypeRegistry()` and `getMetaData()`. That is the SPI surface plus
metadata, and nothing else.

That synchronization is itself a reason to merge. `updateTableLookups()`
exists only to hold two maps in agreement about one fact, and it is where
the handwritten-POJO override resolves: a logic bean returning a
hand-written class registers an output type under that simple name, and
the table lookup is then rewritten to it, so the handwritten class wins
over the generated one. Given one map, the override is just what the
registry holds, and the synchronization pass has nothing left to do.

Two things the merge has to reconcile, neither a blocker:

- **Not-found conventions differ.** `TypeRegistry.lookup(String)` returns
  null and callers test for it; `DomainQL.lookupType(String)` throws
  `DomainQLException`. Both are reasonable and they cannot both survive on
  one type under one name.
- **Not every entry has a table.** Logic-bean return types, input types and
  enums are in the registry with no jOOQ table behind them, so the jOOQ
  half is optional per entry, where `lookupType` currently treats absence
  as an error.

Incidentally, `TypeRegistry.lookup(String)` is a linear scan over
`outputTypes.values()` where `jooqTables.get()` is a map read. Noted as a
fact about the merge, not as a reason for it.

## Steps

**Step 0 -- remove `@full`. Done,** `5663d6f`. The directive let a query
return its result outside GraphQL field selection, via a
`DomainQLExecutionContext` the caller retrieved afterwards. It was enabled
in exactly one place in the tree, its own test, and the runtime never put
such a context into the GraphQL context, so it would have thrown on
execution in any application that declared it. 271 lines, and it took
`isFullSupported()` off the facade's surface.

**Step 1 -- delete what has no callers. Done,** `cc8273e`. The seven unused
accessors, `TypeRegistry`'s dead `domainQL` field, and the package-private
`getFieldLookup()`, which turned out to have no live caller either. No
design content; doing it first kept it out of the later diffs.

**Step 2 -- narrow `LogicBeanAnalyzer`. Done,** `aebdaea`. Its four
`domainQL.getTypeRegistry()` calls point at its own field. `DomainQL` is
now only passed through it, never dereferenced during construction.

**Step 3 -- split `TypeRegistry`. Done,** `2df81d3`. `TypeRegistry` is the
read interface and `MutableTypeRegistry` the concrete class the build path
holds. `RelationModel.update()` took the registry instead of `DomainQL`
along the way, which was the second `this`-escape. The
`lookup(Class, TypeContext)` overload had no callers and went too.

**Step 4 -- fold the domain mapping into the registry. Done,** `ca3a7aa`
and `2c2be83`. `jooqTables`, `dbFieldLookup` and `relationModels` are the
registry's. `registerTable()` creates the table lookup out of the output
type it just registered, so `updateTableLookups()` had nothing left to
synchronize and `getPojoType()` was a duplicate; both are gone. Not-found
is null everywhere, and the two callers that had relied on the throw say in
their own words what they wanted the name for. Relations are indexed by the
field reaching them, in a separate commit.

**Step 5 -- extract the facade. Done,** `db3302d`. `QLiveDomain` carries
`getGraphQLSchema()`, `getTypeRegistry()` and `getMetaData()`, implemented
by `DomainQL`. `MetadataProvider`, `DomainQLAware`, the `QLiveConfiguration`
beans and everything they reach take it -- which turned out to be every
reference to `DomainQL` in the runtime, because every call made on one was
already one of the three. The builder still returns `DomainQL`: what
assembles the schema has no reason to hold a narrower view of itself.

It came after the fold deliberately. Extracting first would have meant a
facade of eight methods that lost five of them a step later -- two
signature changes where one did.

**Step 6 -- move assembly out of the constructor. Done,** `fb6adde`.
`SchemaAssembler` holds the assembly; `DomainQL` is the immutable result,
160 lines of which most is the static naming conventions the builder and
the registry need before a domain exists.

The third `this`-escape is where the design had to say something new. A
query or mutation is built during assembly and reads the domain only at
fetch time, so it takes a `DeferredDomain` -- a one-shot cell the assembler
fills once there is a domain, and which refuses to answer before that. The
alternative, putting the domain into the GraphQL context per execution,
reaches across modules for a problem one cell states exactly. The
`DomainQLAware` scalars moved to after assembly for the same reason: they
were being handed a `DomainQL` whose `metaData` field was still null.

**Step 7 -- rename.** Done 2026-09-25, and it did cost one pass rather
than two: every reference the rename touched already went through the
facade, so nothing had to be re-decided on the way.

| was | is |
| --- | --- |
| `DomainQL` | `QLiveDomainImpl` |
| `DomainQLBuilder` | `QLiveDomainBuilder`, which now holds `newDomain()` |
| `DomainQLAware`, `setDomainQL` | `QLiveDomainAware`, `setDomain` |
| `DomainQLException` and its three subclasses | `QLiveDomainException`, `QLiveDomainBuilderException`, `QLiveDomainTypeException`, `QLiveDomainExecutionException` |
| `DomainQLMeta`, `DomainQLTypeMeta` | `DomainMeta`, `DomainTypeMeta`, with `DomainTypeMetaProps` and `DomainFieldMeta` in qlive-ts |
| `DomainQLMethod`, `DomainQLDataFetchingEnvironment` | `QLiveDomainMethod`, `QLiveDataFetchingEnvironment` |
| `domainQL` (474 fields, parameters, locals) | `domain` |

Three decisions inside it:

- **The implementation stays public.** `build()` returns
  `QLiveDomainImpl`, not the interface. A test declares it and an
  application never needs to; narrowing the return type would churn the
  test suite to hide a type nothing reaches for.
- **The statics came off first.** `DomainQL` was a domain and a utility
  holder bolted together. `SchemaNames` took the naming rules --
  absorbing a duplicate `getInputTypeName` in `MutableTypeRegistry` and
  duplicate `QUERY_TYPE`/`MUTATION_TYPE` literals in `TypeDoc` -- and
  `PojoTypes` took the class introspection. What was left was three
  fields and their getters, which is what the name now describes.
- **Two exception roots stay.** `QLiveDomainException` for a domain that
  could not be assembled or a schema that could not answer;
  `QLiveException` in `qlive` for a request that could not be served.
  `QLiveDomainBuilderException` extended `RuntimeException` and now
  extends the root, so catching the domain exception catches
  misconfiguration too.

`docs/design` was left in the names it was written in. A design document
records what was decided when, and quoting a signature that did not exist
yet would make it a worse record. The user-facing site in `qlive-doc` was
updated.

Each step left the build green.

## Naming

**The facade is `QLiveDomain`.** Decided 2026-09-21.

That name was held by a helper in `io.github.qlivedev.runtime.domain`,
which is a single static method with no state: it pre-registers the eight
QLive scalars and forwards metadata providers to `DomainQL.newDomainQL()`.
An application that wants a different set replaces it rather than
customizing it, and it had two call sites in code. That is a weak claim on
the name against the type every application's SPI implementations and bean
signatures will mention.

So it is now `QLiveDefaultDomain`, which also says more accurately what it
is -- a default set of registrations, not the domain itself.

The rejected alternative was `QLiveSchema`, which overlaps `GraphQLSchema`
-- a thing the facade exposes rather than a thing it is.

The knock-on went with it: the bean proposed in `module-distribution.md`
is a `Consumer<DomainQLBuilder>` and was named after the helper, so it is
`QLiveDomainBuilderCustomizer` there now -- it customizes the builder, not
the facade.

## jOOQ stays in the signatures

`lookupField` keeps returning `org.jooq.Field<?>`, and `lookupType` and
`getJooqTables` keep returning `TableLookup`. Decided 2026-09-21.

Not carried forward by default -- re-asked, and the answer is that an
abstraction would hide a dependency that is not hidden anywhere else. jOOQ
is already in the public signatures of the builder, which is the first
thing an application touches: `objectTypes(Schema)` takes `Public.PUBLIC`,
`objectTypes(Table<?>...)` and both `configureRelation` overloads take
generated table fields, and the builder is handed a `DSLContext`. The
template configuration in `qlive-test` imports `org.jooq` directly and
names generated jOOQ constants throughout.

The call sites point the same way. `QueryPlanBuilder.column()` passes the
field straight to `PlanNode.addColumn()`, and `DefaultMergeService` uses it
to build conflict rows and updates. A wrapper type would be unwrapped at
every use.

The price, stated plainly: the facade cannot be implemented without jOOQ on
the classpath. That only costs something if QLive ever wants a non-jOOQ
backend, which is not a goal, and if it became one the schema assembly
behind the facade would be the larger obstacle by far.

## Placement

The facade stays in `qlive-graphql`. Putting it in `qlive-api`, which would
let the runtime depend on the API alone, requires `TypeRegistry`,
`DomainQLMeta` and `TableLookup` to move as well, and the dependency runs
`qlive-graphql` -> `qlive-api`, not the reverse. Not worth coupling to this
change; see `module-distribution.md` for where that question belongs.

## Non-goals

- No compatibility shims, deprecated overloads or migration paths. Nothing
  is published and QLive promises the old libraries nothing, so a wrong
  design gets cut rather than wrapped.
- Not rewriting schema assembly. Step 5 moves it; what it does stays the
  same.
- Not touching `DomainQLMethod`, `Query`, `Mutation` beyond what steps 2
  and 5 require. The fetcher path legitimately holds a `DomainQL` and uses
  it at fetch time, when the object is complete.

## Open items

Both were examined on 2026-09-22 and both are closed by step 4.

### Two registered classes sharing a simple name

**Closed.** The mechanism was not map iteration order: `TypeContext.equals`
and `hashCode` compare `typeName` alone (`TypeContext:206`, `:228`), so
`outputTypes` is keyed by GraphQL name, and `register()` returned the
incumbent when the name was taken. The first registration won and the
second class was silently aliased to it, in logic bean declaration order.

The damage was not a coin toss over which type gets exposed. Both queries
got the winner's type. A probe registering `beans.SumPerMonth` and
`beans.collision.SumPerMonth` built a schema without complaint in which
`getCollidingSumPerMonth` was declared to return `SumPerMonth{month, year,
sum}` while the bean returned an object whose only property is `total`.
Reverse the bean order and the same was true of the other query. The schema
stated something the resolver could not satisfy.

`b51f6d6` had closed this for names that reach `jooqTables`, which is where
the legitimate override lives. What was left was two hand-written classes
neither of which is table-backed -- no override to be, nothing to catch
them.

The check is now in `register()`, beside the existing-entry return, and it
had to be: step 4 removed `updateTableLookups()`, which is where
`b51f6d6`'s check lived. It carries a rule one step more general than that
one stated -- a class takes over another's name by extending it, and
unrelated classes are a collision -- because `register()` sees types with
no table behind them, where "the overridden one is a generated POJO" has
nothing to test. It fires on nothing that exists: the full reactor is
green, and the two cases have tests.

`registerInput()` has the same shape and no check. Input types are derived
from the output types that already went through `register()`, so a clash
that reaches only the input side has not been constructed; left as a known
gap rather than a fix on speculation.

### The merged not-found convention

**Closed: null, and no `Optional`.** The conventions were already split,
and not along a line worth keeping: `lookupField` returned null, while
`lookupType`, `getPojoType` and `getJooqTable` threw `DomainQLException`.
Everything on the registry side -- `lookup(String)`, `lookup(Class)`,
`lookupInput`, `getOutputOverride` -- returned null.

What the call sites want is not a safer return type but their own message,
and the merge already demonstrated it. `DefaultMergeService` wraps both
lookups: `requireType` asks `getJooqTables().containsKey(typeName)` and
`requireField` null-checks `lookupField`, each throwing a `QLiveException`
that says what the merge needs in the merge's own words.

Null also carries a meaning an exception would destroy. In
`QueryPlanBuilder.column()` a null from `lookupField` is the normal case,
not a failure: the property is a computed field that no `@Column` backs,
and the planner leaves it to be fetched from the object. Only `@Column`
properties enter `dbFieldLookup` (`DomainQLBuilder:227`), so "null means no
column" is true by construction. The filter path is strict for the same
reason, and says so.

The two callers that had not stated what they assume now do:

- **`QueryPlanBuilder`'s root type** produced `Could not find domain type
  'X'`, which did not say that the query document named a type the domain
  does not expose. It says that now, in the merge's phrasing.
- **`DomainObjectUtil`** passed `lookupField`'s result straight into
  `query.addValue(field, value)` with no null check, so a `DomainObject`
  carrying a computed property reached jOOQ with a null `Field`. jOOQ does
  not object: it renders the column as `"unknown field 0"` and the
  statement fails only once a database sees it. Fixed in `788a6f9` by
  skipping a property no column backs, which is what lets an object be read
  and written back; the class is kept -- it is the convenience path for a
  service with simple storage needs -- and now has the test it never had.
  Its table lookups got a message of their own here.

`getOutputOverride` did not survive the merge. It was `lookup(simpleName)`
plus a `getJavaType()`, its two callers were `updateTableLookups()` and
`RelationModel.update()`, and with the first gone the second does the
lookup itself. One less method on the type an application reads the domain
through.
