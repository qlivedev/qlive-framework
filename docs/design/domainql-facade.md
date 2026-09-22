# A facade for DomainQL

Status: planned, step 0 landed. Written 2026-09-21, revised 2026-09-22.

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
- `TypeRegistry` already answers the Java side. `OutputType.getJavaType()`
  returns a `Class<?>`, and `TableLookup.getPojoType()` returns a `Class<?>`
  for the same type name out of a different map. For table-backed types
  these are very likely the same class, which would make `getPojoType()`
  redundant rather than merely relocatable -- to be confirmed before the
  merge, not assumed.
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

**Step 0 -- remove `@full`. Done.** Commit `5663d6f`. The directive let a
query return its result outside GraphQL field selection, via a
`DomainQLExecutionContext` the caller retrieved afterwards. It was enabled
in exactly one place in the tree, its own test, and the runtime never put
such a context into the GraphQL context, so it would have thrown on
execution in any application that declared it. 271 lines, and it took
`isFullSupported()` off the facade's surface.

**Step 1 -- delete what has no callers.** The seven unused accessors and
`TypeRegistry`'s dead `domainQL` field. No design content; doing it first
keeps it out of the later diffs.

**Step 2 -- narrow `LogicBeanAnalyzer`.** Point its four
`domainQL.getTypeRegistry()` calls at its own field. `DomainQL` is then
only passed through it, never dereferenced during construction.

**Step 3 -- split `TypeRegistry`.** A read interface for what the 19 call
sites use; the concrete class keeps the mutators and is what the build path
holds.

**Step 4 -- fold the domain mapping into the registry.** `jooqTables`,
`dbFieldLookup` and `relationModels` move out of `DomainQL` and become the
registry's, per the section above, with the two not-found conventions
reconciled and relations indexed rather than scanned. `TypeRegistry` is
constructed inside `DomainQL`'s constructor today, so this is reachable
without moving assembly first.

**Step 5 -- extract the facade.** An interface carrying
`getGraphQLSchema()`, `getTypeRegistry()` and `getMetaData()`, implemented
by `DomainQL`. Repoint `MetadataProvider`, `DomainQLAware` and the six
`QLiveConfiguration` beans at it. This is the step that changes what a
framework user sees.

It comes after the fold deliberately. Extracting first would mean a facade
of eight methods that loses five of them a step later -- two signature
changes where one will do.

**Step 6 -- move assembly out of the constructor.** Once the SPIs take the
narrow type, the ~1,800 lines of assembly move to a class that is not the
runtime object, and what `build()` returns becomes a small immutable holder
of schema, registry and metadata. The three `this`-escapes go away as a
consequence rather than needing individual fixes.

**Step 7 -- rename.** `DomainQL`, `DomainQLBuilder`, `DomainQLAware`,
`DomainQLException`. The facade means this costs one pass, not two: the
interface takes the name QLive wants, and the implementation behind it can
keep the old one until the rename is convenient. Deferred by decision;
listed here so the ordering is on record.

Steps 1 through 3 are small and mechanical. Step 4 is the first with real
design content in it. Each leaves the build green. Step 6 is the large one
and should not start until 5 is in.

## Naming

**The facade is `QLiveDomain`.** Decided 2026-09-21.

That name is currently held by a helper in
`io.github.qlivedev.runtime.domain`, which is a single static method with
no state: it pre-registers the eight QLive scalars and forwards metadata
providers to `DomainQL.newDomainQL()`. An application that wants a
different set replaces it rather than customizing it, and it has two call
sites in code. That is a weak claim on the name against the type every
application's SPI implementations and bean signatures will mention.

So it becomes `QLiveDefaultDomain`, which also says more accurately what
it is -- a default set of registrations, not the domain itself.

The rejected alternative was `QLiveSchema`, which overlaps `GraphQLSchema`
-- a thing the facade exposes rather than a thing it is.

The rename is a precondition for step 4 and independent of steps 1 to 3,
so it can land at any point before then. One knock-on: the
`QLiveDomainCustomizer` bean proposed in `module-distribution.md` is named
after the helper, and wants rereading once the name means the facade.

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

- **Whether `getPojoType()` survives the fold at all.** If
  `OutputType.getJavaType()` and `TableLookup.getPojoType()` agree for
  every table-backed type, it is a duplicate rather than a method needing a
  new home. Checking that is the first task of step 4.
- **What the merged not-found convention is.** Null for absent and an
  exception for genuinely unknown is one answer; `Optional` is another.
  Decided in step 4, against the call sites rather than in the abstract.
