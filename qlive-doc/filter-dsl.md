# Filter DSL

The Filter DSL builds the conditions and sort fields a `QueryConfig`
carries. The same DSL exists on both sides -- `FilterDSL` in TypeScript,
`com.dataciders.qlive.runtime.scalar.FilterDSL` in Java -- and a condition
travels between them as JSON.

It is exported as a namespace, because it claims short, common names that
would collide in an application's import list:

```ts
import {FilterDSL} from "@quinscape/qlive-ts";

const {field, value, values, and, or, not, component} = FilterDSL;
```

## Building a condition

Fluent, reading left to right off a field:

```ts
const filter = field("name").eq(value("Foo #1"))
```

or functional, which is the same graph:

```ts
const filter = and(
    field("name").eq(value("Foo #1")),
    field("num").greaterThan(value(10))
)
```

Field paths are dotted and follow relations: `field("owner.login")` filters
on the login of the related user. A path may cross a to-many relation --
`bazLinks.baz.name` -- and becomes a correlated `EXISTS` rather than a
join.

## Values

`value(v)` infers the scalar type from the JavaScript value; pass the type
explicitly where the inference cannot be right:

```ts
value("Foo #1")                       // String
value(42)                             // Int
value("2018-11-01T19:58:59.000Z", "Timestamp")
```

`values(type, ...vs)` is the collection form, for `in`:

```ts
field("id").in(values("Int", 1, 2, 3))
```

Two values are computed by the **database** rather than by the client:

```ts
field("created").lessThan(FilterDSL.now())
field("date").eq(FilterDSL.today())
```

Every row of one query then sees the same "now", on the same clock the rest
of the schema's defaults use.

## Logical composition

`and()` and `or()` **drop falsy operands**, and an empty one collapses to
`null`. That is the point of them rather than a convenience: it lets you
compose from helpers that may contribute nothing and let the final shape
fall out of whatever survived.

```ts
or(
    nameFilter,                     // may be null
    showAdmins && adminFilter,      // boolean guard
    ownerFilter,
)
```

The type is `LogicalOperand = FilterExpression | null | undefined | false`.
Note that `flag && cond` only lands in it when `flag` is a **boolean** --
for a truthy-narrowable value write `!!flag && cond` or
`flag ? cond : null`, because `"" | undefined | Condition` is not an
operand.

A `null` operand is dropped on the server too: a filter component that
currently filters nothing constrains nothing at all.

## Components

A component wraps a condition with an id, marking which part of a form it
came from:

```ts
component("nameFilter", field("name").contains(value(search)))
```

Logically it evaluates as the condition it wraps -- the database has no use
for the marker. `findComponentNode(condition, id)` finds one again, which
is how a form reads back the term it contributed.

## Operators

Every name below is a method on a field or value node, and also reachable
as `condition(name, operands)` / `operation(name, operands)` for building
graphs programmatically.

**Comparison and predicates**

`eq` `equal` `ne` `notEqual` `lt` `lessThan` `le` `lessOrEqual` `gt`
`greaterThan` `ge` `greaterOrEqual` `isNull` `isNotNull` `isTrue` `isFalse`
`isDistinctFrom` `isNotDistinctFrom` `equalIgnoreCase` `notEqualIgnoreCase`
`between` `notBetween` `betweenSymmetric` `notBetweenSymmetric` `in`

**String**

`contains` `notContains` `containsIgnoreCase` `notContainsIgnoreCase`
`startsWith` `endsWith` `likeRegex` `notLikeRegex`

**Logical** (on a condition)

`and` `or` `andNot` `orNot` `not`

**Arithmetic and bitwise** (on a field, producing an expression)

`add` `plus` `sub` `subtract` `minus` `mul` `times` `multiply` `div`
`divide` `mod` `modulo` `rem` `pow` `power` `neg` `unaryMinus` `unaryPlus`
`bitAnd` `bitOr` `bitXor` `bitNand` `bitNor` `bitXNor` `bitNot` `shl` `shr`

**Other**

`lower` `upper` `concat` `toString` (translated to a cast to string)

**Sort order**

`asc` `desc`

The names are not invented: they are methods of jOOQ's `Field`, dispatched
by name and operand count. That is also the security boundary -- conditions
arrive from browsers, and a name not on this list never reaches reflection.

## Sort fields

`sortFields` is an array of field expressions, each a bare name or a node:

```ts
foos.update({sortFields: ["name"]})
foos.update({sortFields: [field("created").desc()]})
```

With no sort fields the primary key is the sort, and the config that comes
back says so.

## Plain objects

The DSL produces nodes that are instances of its own classes. That works
almost everywhere, but not quite everywhere -- MobX, for one, ignores class
instances and will not make them observable, and making the whole DSL
observable would be a large cost for an exotic case.

```ts
const plain = FilterDSL.toJSON(condition);
```

converts the graph to plain objects and arrays. It is the same shape the
condition travels in.
