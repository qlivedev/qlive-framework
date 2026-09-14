---
title: Add schema metadata
description: MetadataProvider beans, and typing what they add on the client.
sidebar:
  order: 6
---

Every `MetadataProvider` bean is picked up automatically and writes into
the `DomainQLMeta` the server embeds in the page, on two levels: an
addendum next to `types`, `genericTypes` and `relations`, and field meta
data on individual fields.

The client-side counterpart is declaration merging -- name your addenda
once and they are typed everywhere the application reads `config().meta`:

```ts
declare module "@quinscape/qlive-ts" {
    interface DomainQLMeta {
        quickSearchTypes: string[]
    }
    interface DomainQLFieldMeta {
        quickSearch?: boolean
    }
}
```

Nothing but a test on the Java side notices when the two drift apart, so it
is worth having one.
