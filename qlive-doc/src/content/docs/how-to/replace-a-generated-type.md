---
title: Replace a generated type
description: A handwritten class in place of the jOOQ POJO, and computed fields on it.
sidebar:
  order: 6
---

When a table's columns do not say everything about a type, replace the
generated POJO with a handwritten class that **extends** it, and register it
with `objectType()` after the schema's own types. DomainQL resolves a
domain type by simple name, so yours takes the generated one's place --
including for the query document service, which materializes whatever the
table lookup names.

Extending the generated POJO is what keeps it able to hold a row: the
columns, their JPA annotations and the fetcher context all come along.

The handwritten class does not live in `domain/`. The code generator owns
that directory outright and deletes anything in it that it did not write --
see [qlive-test layout](/qlive-framework/reference/qlive-test-layout/).

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

Adding a field is a schema change, so
[regenerate](/qlive-framework/how-to/regenerate-from-the-schema/) once the
backend is running with it.
