# Splitting the qlive-graphql schema tests

Status: sketched, not started, and judged not worth doing on its own.
Written 2026-09-25, out of a question asked during the DomainQL rename.

Recorded so the next person to open `AnotherQLiveDomainTest` finds the
reasoning instead of rediscovering it.

## Problem

`qlive-graphql` has two large tests of schema assembly, and one of them
is called `AnotherQLiveDomainTest`. The name says nothing, and the
obvious fix -- rename it after what it covers -- does not work, because
what it covers is schema assembly, and so does `QLiveDomainTest`. A name
like `SchemaAssemblyTest` would describe both and distinguish neither.

The difference between them is not subject matter. It is shape:

| | domains built | tests |
| --- | --- | --- |
| `QLiveDomainTest` | 1, as a field | 8 |
| `AnotherQLiveDomainTest` | 44 | 44 |

`QLiveDomainTest` configures a single domain with nine source/target
relation variants and then interrogates that one schema from eight
angles. `AnotherQLiveDomainTest` builds a throwaway domain per test and
asserts one thing about each.

That is almost certainly how the name happened. The two differ in how
they are built, not in what they are about, and a fixture is not
something a test should be named after.

## What a split would look like

The 44 cases fall into three groups. Two name themselves; the third took
a second pass to see, because its members look unrelated until the
question is phrased as "how does a Java thing become a GraphQL thing".

**Generic types -- 15.** `testLogicWithGenerics`, `testDegenerify`,
`testDegenerifyRename`, `testDegenerifiedDBObject`,
`testDegenerifiedListInput`, `testDegenerifiedInput`,
`testDegenerifiedContainerOutput`, `testDoubleDegenerification`,
`testGenericDomainObject`, `testGenericDomainObjectWithoutScalar`,
`testGenericDomainObjectOutput`, `testTypeParameters`,
`testTypeParametersForMutations`, `testTypeParameterWithPattern`,
`testGenericScalar`.

**Type naming -- 13.** Clashes, renames and overrides:
`testRelationRenaming`, `testRenamedObjectAndScalarSourceFields`,
`testFieldConflict`, `testFieldConflictResolution`, `testTypeRepeat`,
`testPojoAndObjNameConflict`, `testPojoAndBackObjNameConflict`,
`testNameClashWithTableBackedType`,
`testNameClashBetweenTwoLogicBeanTypes`, `testImplicitOverride`,
`testImplicitOverrideNonInputLogic`, `testOutputTypeOverride`,
`testOutputTypeOverrideByParam`.

**Type mapping -- 16.** What a Java type or property turns into:
`testInputMirrorCreation`, `testObjectAndScalarSourceFields`,
`testWrongTypeAsQueryInput`, `testRecordAsQueryInput`,
`testAnnotatedFields`, `testCustomParameterProvider`,
`testLogicWithEnums`, `testLogicWithEnums2`, `testListReturningLogic`,
`testFieldLookup`, `testNotNullQuery`, `testIgnoredProps`, `testDBView`,
`testBinaryData`, `testBigNumericTypes`, `testMetaTags`.

`QLiveDomainTest` is left as it is by the split, and its name only
becomes honest once the others have theirs: five of its eight cases are
relation configuration, so `RelationConfigurationTest` would fit them
and not the three that test queries, mutations and implicit input
types. Whether those three move or the name stays approximate is a
decision for whoever does the work.

## Why it is not being done

Nothing is wrong. Every one of the 44 passes, covers something real, and
is found by the same `mvn test` either way. Moving 1,391 lines between
files buys navigability and nothing else -- no coverage, no clarity
about what the framework does, no bug.

Against that: the move rewrites the blame on every one of those tests,
and it conflicts with any branch touching them, which during a period of
schema-assembly work is a real cost paid by someone other than the
person who chose to pay it.

## When to do it

When one of the three groups has to grow. A new cluster of generics
tests is a reason to give generics a file; adding the fifteenth naming
test to a class already holding forty-four is not.

Doing it opportunistically also works: if a change already moves most of
one group, finish the group rather than leaving it half-moved.

What does not justify it is the name alone. `AnotherQLiveDomainTest` is
ugly and costs nothing per day.
