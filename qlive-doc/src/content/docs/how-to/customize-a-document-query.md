---
title: Customize a document query
description: Intercepting the QueryConfig for defaults and for row-level security.
sidebar:
  order: 7
---

The config a document query executes with is the one you hand
`buildQuery()`, and nothing says it has to be the one that arrived. Both
recipes below are the same move: take the incoming `QueryConfig`, change
it, pass the changed one on.

You can have as many `QueryDocumentService` endpoints with as many
configurations as you want, and they can share row types -- so a
specialized endpoint does not cost the general one anything.

## Redefine what "no config" means

An injection with no config of its own still reaches your query method with
a complete `QueryConfig` object: QLive assembles one from what the meta
configuration suggests for that type -- the default `pageSize`, the default
sorting. There is no GraphQL coercion that could have done it, because
there was no config level in the query at all.

Its normal meaning is "give me this type with default configuration", and
you can redefine it. Detect that the config is the default before you pass
it into the service, and replace what you like -- the condition with one of
your own, the sort fields with a complex expression list:

> "The latest edited favorites of the current user", or whatever satisfies
> your needs.

The query then executes with your config, and that same config is what
comes back on the document -- so the client's next `update()` starts from
where you put it.

## Filter by security rules

Same move, on every query rather than the default one: extend the incoming
condition with the filters your security rules demand for that type.

Restore the original condition on the document you return. The user
continues from the filter they actually asked for, rather than from a
condition they did not write and would then be updating against.

This is a filter, not a boundary. What a config posted by a browser can
reach at all is decided by
[`selectByFilter`](/qlive-framework/how-to/expose-document-queries/#selectbyfilter) -- left at
its default, a path may only name a field the query already selects.
