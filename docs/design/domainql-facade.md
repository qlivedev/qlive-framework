# A facade for DomainQL

Status: planned, step 0 landed. Written 2026-09-21.

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

`DomainQL` exposes 15 public accessors. Nine are reached from outside
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
| `getGenericTypes()` | `Util` |

Two more, `getJooqTable()` and `isNormalProperty()`, are used only within
`qlive-graphql`.

Six have no caller anywhere: `getOptions()`, `getLogicBeans()`,
`getRelationModel(String)`, `getAdditionalQueries()`,
`getAdditionalMutations()`, `getAdditionalDirectives()`.

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

## Steps

**Step 0 -- remove `@full`. Done.** Commit `5663d6f`. The directive let a
query return its result outside GraphQL field selection, via a
`DomainQLExecutionContext` the caller retrieved afterwards. It was enabled
in exactly one place in the tree, its own test, and the runtime never put
such a context into the GraphQL context, so it would have thrown on
execution in any application that declared it. 271 lines, and it took
`isFullSupported()` off the facade's surface.

**Step 1 -- delete what has no callers.** The six unused accessors and
`TypeRegistry`'s dead `domainQL` field. No design content; doing it first
keeps it out of the later diffs.

**Step 2 -- narrow `LogicBeanAnalyzer`.** Point its four
`domainQL.getTypeRegistry()` calls at its own field. `DomainQL` is then
only passed through it, never dereferenced during construction.

**Step 3 -- split `TypeRegistry`.** A read interface for what the 19 call
sites use; the concrete class keeps the mutators and is what the build path
holds.

**Step 4 -- extract the facade.** An interface carrying the nine consumed
methods, implemented by `DomainQL`. Repoint `MetadataProvider`,
`DomainQLAware` and the six `QLiveConfiguration` beans at it. This is the
step that changes what a framework user sees.

**Step 5 -- move assembly out of the constructor.** Once the SPIs take the
narrow type, the ~1,800 lines of assembly move to a class that is not the
runtime object, and what `build()` returns becomes a small immutable holder
of schema, registry, table lookups and metadata. The three `this`-escapes
go away as a consequence rather than needing individual fixes.

**Step 6 -- rename.** `DomainQL`, `DomainQLBuilder`, `DomainQLAware`,
`DomainQLException`. The facade means this costs one pass, not two: the
interface takes the name QLive wants, and the implementation behind it can
keep the old one until the rename is convenient. Deferred by decision;
listed here so the ordering is on record.

Steps 1 through 4 are independently landable and each leaves the build
green. Step 5 is the large one and should not start until 4 is in.

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

- **What the facade is called.** `QLiveSchema` reads well but overlaps
  `GraphQLSchema`, which it exposes. `QLiveDomain` is taken by the builder
  entry point in the runtime. Undecided.
- **Whether `lookupField` should return `org.jooq.Field<?>`.** It puts jOOQ
  in the signature of the type applications are handed. Inherited, not
  chosen; worth re-asking rather than carrying forward by default.
- **Whether the domain-mapping half should be its own interface.**
  `lookupField`, `lookupType`, `getPojoType`, `getJooqTables`,
  `getRelationModels` serve merge and query planning; `getGraphQLSchema`,
  `getTypeRegistry`, `getMetaData` serve the SPIs. `QueryPlanBuilder` uses
  only the first group and `DefaultMergeService` uses both, so the seam is
  real but does not cut every consumer cleanly.
