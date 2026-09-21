---
title: Filter DSL
description: One condition language, and what it means in each place it lands.
sidebar:
  order: 7
---

The FilterDSL is a fluent TypeScript API that produces JSON-like graphs. To
enable chaining, it creates instances with a prototype that allows further
operations or conditions.

It is a unified condition language: components express and compose
conditions, and those conditions are then evaluated in the right location.
The same DSL exists on both sides -- `FilterDSL` in TypeScript,
`io.github.qlivedev.runtime.scalar.FilterDSL` in Java -- and a condition
travels between them as JSON. Where that sits in the framework as a whole
is [Unified Domain](/qlive-framework/explanation/unified-domain/).

Here we focus on using it for database operations, but the same graphs
filter Java objects or JavaScript objects just as well.

## Two ways in

It is exported as a namespace, because it claims short, common names that
would collide in an application's import list:

```ts
import {FilterDSL} from "@quinscape/qlive-ts";

const {field, value, values, and, or, not, component} = FilterDSL;
```

A module that does little besides build conditions would rather not repeat
the namespace, and can take the names straight from a second entry point:

```ts
import {field, value, values, and, or, not} from "@quinscape/qlive-ts/filter";
```

Both routes reach the same module. Conditions built through one are the
same objects as conditions built through the other, so this is a choice to
make per module and not once for an application.

## Fluent and functional are one graph

Fluent, reading left to right off a field:

```ts
const filter = field("name")
    .eq(value("Foo #1"))
    .and(
        field("num").greaterThan(value(10))
    )
```

or functional, which is the same graph:

```ts
const filter = and(
    field("name").eq(value("Foo #1")),
    field("num").greaterThan(value(10))
)
```

Nothing downstream can tell which of the two wrote a condition.

## Semantic differences per technology

Field paths are dotted and follow relations: `field("owner.login")` filters
on the login of the related user. The form is always the same. What the
path *means* is not.

For a database query condition, a path may cross a to-many relation --
`bazLinks.baz.name` -- and becomes a correlated `EXISTS` rather than a
join.

For a Java object condition, the same path has memory graph semantics and
needs an index. `bazLinks.0.baz.name` refers to one concrete baz object
linked to by the first bazLink. `owner.login` happens to express the same
thing in both worlds.

## Values the database computes

`value(v)` infers the scalar type from the JavaScript value; pass the type
explicitly where the inference cannot be right. Two values are computed by
the **database** rather than by the client:

```ts
field("created").lessThan(FilterDSL.now())
field("date").eq(FilterDSL.today())
```

Every row of one query then sees the same "now", on the same clock the rest
of the schema's defaults use.

## Composition drops what contributed nothing

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

## Components mark where a term came from

A component wraps a condition with an id:

```ts
component("nameFilter", field("name").contains(value(search)))
```

Logically it evaluates as the condition it wraps -- the database has no use
for the marker. `findComponentNode(condition, id)` finds one again, which
is how a form reads back the term it contributed.

## Why the operators are not listed here

Every operator is a method on a field, value or condition node, and each is
also reachable as `condition(name, operands)` / `operation(name, operands)`
for building a graph programmatically.

The names are not invented: they are methods of jOOQ's `Field`, dispatched
by name and operand count. That is also the security boundary -- conditions
arrive from browsers, and a name the dispatch does not know never reaches
reflection.

They are installed on the node prototypes at runtime, so nothing in the
generated type declarations names one, and a copy maintained by hand beside
the map they come from is a copy that drifts. The list lives in
[Filter DSL in the API reference](/qlive-framework/api/filter-dsl/), which
is generated from that map and gives each operator the number of operands
it takes.

## Sort fields

`sortFields` is an array of field expressions, each a bare name or a node:

```ts
foos.update({sortFields: ["name"]})
foos.update({sortFields: ["!created"]})  // the same as the following line
foos.update({sortFields: [field("created").desc()]})
foos.update({sortFields: [field("a").plus(field("b"))]})
```

With no sort fields the primary key is the sort, and the config that comes
back says so.

## Nodes are class instances

The DSL produces nodes that are instances of its own classes. That works
almost everywhere, but not quite everywhere -- MobX, for example, ignores
class instances and will not make them observable, and making the whole DSL
observable would be a large cost for an exotic case.

So where you need the graph in its pure JSON form, call

```ts
const plain = FilterDSL.toJSON(condition);
```

which converts it to plain objects and arrays. It is the same shape the
condition travels in.
