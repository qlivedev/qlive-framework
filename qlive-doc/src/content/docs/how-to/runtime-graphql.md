---
title: Use GraphQLQuery at runtime
description: Executing a named query that could not have been injected.
sidebar:
  order: 12
---

Most data a page needs is
[injected](/qlive-framework/explanation/injections/) and arrives in the HTML
document. Runtime execution is for what could not have been: a detail
record chosen by a click, a lookup whose parameters only exist once the
user has typed something.

Before reaching for it, check the two things that are not it:

- A page of the same rows, differently filtered or sorted, is
  [`update()`](/qlive-framework/explanation/query-documents/#updating-is-a-delta)
  on the document you already have.
- Data that changed because somebody else changed it is
  [`useDocumentWatch()`](/qlive-framework/api/push-and-subscriptions/#usedocumentwatch).

## Execute a named query

Declare the query the way you declare any other -- module scope, literal
source, [the same rules](/qlive-framework/reference/generated-artifacts/) --
and call `execute()` on it:

```typescript
import {field, value} from "@quinscape/qlive-ts/filter"
import Q_FooDetail from "./Q_FooDetail"

    // ... detailId is the id of the Foo you want to load in detail

    const doc = await Q_FooDetail.execute(
        {
            config: {
                condition: field("id").eq(value(detailId))
            }
        }
    )
```

`execute()` posts the query, converts the variables on the way out and the
result on the way in, and unwraps the single top-level selection -- so it
resolves to `T`, exactly what `useInjection()` would have handed you for
the same query. A query document that comes back this way is a live
document like any other: `update()` works on it.

It still gets its generated result type, because it is still a declared
constant with a literal source. Only the *injection* needs the call site to
be readable at build time; execution does not.

## The raw call

For the cases that do not fit -- several top-level selections, or you want
the wire format -- there is `graphql()`:

```ts
import graphql, {firstValue} from "@quinscape/qlive-ts";

const data = await graphql(query, params);   // the whole "data" object
```

It rejects on a transport error or on any GraphQL error in the response.
Nothing converts the result and nothing types it: this is the escape hatch,
not the normal path.

Both calls, and `firstValue()`, are
[Runtime GraphQL in the API reference](/qlive-framework/api/runtime-graphql/).
