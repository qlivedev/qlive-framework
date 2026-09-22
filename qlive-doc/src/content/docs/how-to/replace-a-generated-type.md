---
title: Replace a generated type
description: A handwritten class in place of the jOOQ POJO, with computed fields and pinned scalars.
sidebar:
  order: 6
---

When a table's columns do not say everything about a type, replace the
generated POJO with a handwritten class that **extends** it. DomainQL
resolves a domain type by simple name, so yours takes the generated one's
place -- including for the query document service, which materializes
whatever the table lookup names.

Extending the generated POJO is what keeps it able to hold a row: the
columns, their JPA annotations and the fetcher context all come along.

The handwritten class does not live in `domain/`. The code generator owns
that directory outright and deletes anything in it that it did not write --
see [qlive-test layout](/qlive-framework/reference/qlive-test-layout/).

## Register it

A class enters the schema by appearing in a logic bean's signature: as a
return type, as a parameter, or in the `types` of a `@GraphQLTypeParam`.
The last is the usual one, because a type worth replacing is usually a type
you already have a
[document query](/qlive-framework/how-to/expose-document-queries/) for.

```java {9-10} title='QueryLogic.java'
@GraphQLQuery
public <T> @NotNull QueryDocument<T> queryDocument(
    @GraphQLTypeParam(
        namePattern = "query*Document",
        typeNamePattern = "*Document",
        types = {
            Foo.class,
            Bar.class,
            // the handwritten Qux, which takes the generated one's place
            Qux.class
        }
    )
    Class<T> type,
    DataFetchingEnvironment env,
    @NotNull QueryConfig config
)
```

Once the class is registered, DomainQL points the table lookup for `Qux` at
it and keeps the generated jOOQ table behind it. The table, its columns and
its foreign keys are untouched, so relations configured on them go on
working.

**Do not register it with `objectType()`.** That builder method is for a
type with no table of its own, and it builds a table reference out of a
`@jakarta.persistence.Table` annotation. Handed a subclass it throws,
because that annotation is not inherited; given one redeclared to get past
that, it replaces the generated table with a bare reference that has no
foreign keys -- which is exactly what this type is supposed to keep.

## Add a computed field

A field no column backs is fetched from the object rather than selected:

```java
@GraphQLComputed
public String getSummary()
{
    return getName() + " / " + getStringValue();
}
```

A property has to be writable to become a field at all, so such a field
needs a setter even when nothing reads what it stores.

Two consequences to plan around:

- **A query selecting it should select the fields it is computed from as
  well.** Nothing fetches a column on a computed field's account.
- **A computed property cannot go into a `WHERE` clause.** A filter path
  naming one is an error, not a condition quietly dropped.

## Pin an ambiguous scalar

A column's Java type does not always decide which scalar it should be. A
currency amount is a `long` of 1/10000th units, and nothing about the
column says so. `@GraphQLField(type = ...)` names the scalar, on an
override of the generated getter that keeps its `@Column`:

```java
@Override
@Column(name = "currency_value")
@GraphQLField(type = "Currency")
public Long getCurrencyValue()
{
    return super.getCurrencyValue();
}
```

The default mapping runs both ways, so the annotation is what settles
either direction:

| Java type | Scalar without the annotation |
| --- | --- |
| `long` | `Currency` |
| `Long` | `Long` |

A nullable currency column arrives as `Long` and needs pinning to
`Currency`. A plain count that is not null arrives as `long` and needs
pinning to `Long`. Nullability is all the default has to go on, so a column
whose meaning does not follow from it says so here.

The name has to be a scalar the schema knows. `Currency`, `Long`,
`Timestamp`, `Date`, `Byte`, `BigDecimal` and `BigInteger` come with QLive;
a scalar of your own needs
[a converter](/qlive-framework/how-to/register-a-converter/) on the client
side to go with it.

Adding a field or changing its type is a schema change, so
[regenerate](/qlive-framework/how-to/regenerate-from-the-schema/) once the
backend is running with it.
