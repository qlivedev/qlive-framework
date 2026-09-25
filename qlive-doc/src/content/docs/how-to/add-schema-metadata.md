---
title: Add schema metadata
description: MetadataProvider beans, and typing what they add on the client.
sidebar:
  order: 8
---

Every `MetadataProvider` bean is picked up automatically and writes into
the `DomainMeta` the server embeds in the page, on two levels: an
addendum next to `types`, `genericTypes` and `relations`, and field meta
data on individual fields.

The client-side counterpart is declaration merging -- name your addenda
once and they are typed everywhere the application reads `config().meta`:

```ts
declare module "@qlivedev/qlive-ts" {
    interface DomainMeta {
        quickSearchTypes: string[]
    }
    interface DomainFieldMeta {
        quickSearch?: boolean
    }
}
```

Nothing but a test on the Java side notices when the two drift apart, so it
is worth having one.

## The query config a type suggests

`QueryConfigMetadataProvider` is the one QLive brings. It writes the page
size, the sort and the standing condition a row type gets when nothing
names one:

```java
@Bean
public MetadataProvider queryConfigMetadata()
{
    return QueryConfigMetadataProvider.newProvider()
        .forAllTypes()
            .pageSize(20)
        .andForType(Foo.class)
            .sortFields("name")
            .maxPageSize(100)
        .build();
}
```

`forAllTypes()` is the house rule for every row type the domain has a query
document for; `forType(Class)` and `forTypes(Class...)` are the departures
from it. Chain statements with `andForType()`, `andForTypes()` and
`andForAllTypes()`, and close the chain with `build()`. A type may only be
named once -- say it with `forAllTypes()` and depart from it per type
rather than declaring the same type twice.

### `maxPageSize`

A page size of 0 asks for every row there is, and a config comes from a
browser. `maxPageSize` is the ceiling.

Every query over rows of that type is held to it, whoever asked and however
the config got there -- an injection, an `update()`, a logic bean building
one by hand. A larger page, and an unlimited one, become that page size. A
type that declares none is unlimited, which is what every type is until an
application says otherwise.

The config that comes back on the document says the page size that was
applied, so a client that hit the maximum sees it: `rowCount` is still
everything the condition matches, and a cut page does not look like the
last page of a short table. The maximum itself travels to the client as
type meta data under `maxPageSize`, for the page size controls that would
rather not offer what the server will not give.
