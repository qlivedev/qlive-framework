---
title: The Java half
description: What QLive inherits from DomainQL, and the shape of a logic bean.
sidebar:
  order: 3
---

QLive inherits its basic GraphQL flavor -- Spring Boot, Java and jOOQ --
from [DomainQL](https://github.com/quinscape/domainql). What it replaces is
DomainQL's rudimentary data fetching: in its place sits a graph querying
engine that analyzes a query and splits it into the optimal number of
nicely joined SQL queries.

The wiring itself is
[Wire up a Spring application](/qlive-framework/how-to/wire-up-a-spring-application/).
This page is what the pieces are.

## Logic beans

A logic bean is where your GraphQL methods live. Two annotations carry
almost all of it:

```java {6, 10, 16} title='QueryAndMutationExample.java'
import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.annotation.GraphQLMutation;
import jakarta.validation.constraints.NotNull;

@GraphQLLogic
public class QueryAndMutationExample
{

    @GraphQLQuery
    public MyResult myQuery( … )
    {
        // …
    }

    @GraphQLMutation
    public MutationResult myAction( … )
    {
        // …
    }

}
```

`@GraphQLLogic` is a Spring meta annotation: it declares that this is a
Spring bean and that it contains GraphQL methods.

`@GraphQLQuery` declares that the given method is part of the GraphQL
schema. The input types, output types and scalars it uses are added to the
schema automatically. `@GraphQLMutation` is the write side -- declare all
modifications as mutations.

Logic definitions take precedence over the definitions coming from the
database POJOs. That is the seam an application widens: replacing an
auto-generated type with your own implementation adds GraphQL fields, or
defines GraphQL types the database cannot imply. See
[Replace a generated type](/qlive-framework/how-to/replace-a-generated-type/).

## QueryDocumentService

The query document service is the central data access service for QLive. It
analyzes the currently executed GraphQL query and builds a query plan
spanning one or more SQL queries. `QueryConfig` instances control how that
plan is executed -- offset and page size for paging, a condition, and the
sort fields.

`buildQuery(type, env, config).execute()` is the whole of the calling
convention: `type` is the runtime row class, `env` is the GraphQL
environment carrying the current selection, and `config` is the
`QueryConfig` input. What comes back is a
[query document](/qlive-framework/explanation/query-documents/) -- the rows,
plus the config they were fetched with, so that the client can apply a
delta to it and ask again.

An application can have as many endpoints over the service as it wants,
with as many configurations, and they may share row types. You are in
control before you invoke the service and after you receive the result:

- [Expose document queries](/qlive-framework/how-to/expose-document-queries/)
  -- one generic method that covers every type.
- [Customize a document query](/qlive-framework/how-to/customize-a-document-query/)
  -- intercepting the config for defaults and for row-level security.

## Where the domain comes from

The tables are the starting point. jOOQ generates POJOs that mirror them;
your logic beans and handwritten models sit alongside; and the schema is
what all of that adds up to. Nothing declares the schema separately --
see [Unified Domain](/qlive-framework/explanation/unified-domain/).
