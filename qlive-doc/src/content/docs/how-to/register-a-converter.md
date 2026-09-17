---
title: Register a converter
description: Overriding how a scalar crosses the wire, and when to do it.
sidebar:
  order: 11
---

Values do not arrive as they travel. A converter is registered per GraphQL
named type and runs in both directions -- QLive registers its own for
`Timestamp`, `Date` and the document types, and an application adds or
replaces one the same way.

```ts
import {registerConverter, Temporal} from "@quinscape/qlive-ts";

registerConverter("MyScalar", {
    fromServer: (value, type) => ...,
    toServer:   (value, type) => ...,   // optional for output-only types
});
```

## Do it from the `init` hook

Registering a second converter for a type replaces the first, so you can
override the ones QLive brings. The built-ins are registered while
`startup()` initializes the config, and `init` is the point after that and
before the first view renders:

```tsx
await startup({
    views: import.meta.glob("./app/**/*.tsx"),
    init: async config => {
        registerConverter("Timestamp", myTimestampConverter);
    },
});
```

Anywhere earlier and the built-in overwrites yours; anywhere later and a
view may already have read a value through the old one.

## Two rules

**A converter is never called with `null` or `undefined`.** QLive handles
those before dispatching, so a converter does not have to.

**Import `Temporal` from `@quinscape/qlive-ts`, never from
`temporal-polyfill` directly.** A second copy of the polyfill produces
instants that do not typecheck against the first -- which is also why an
application does not add `temporal-polyfill` to its own dependencies.

The `Converter` shape, the conversion calls the framework makes with it,
the list of built-ins, and the generic scalar types a value arrives in are
[Scalars and conversion in the API reference](/qlive-framework/api/scalars-and-conversion/).
