---
title: Expose document queries
description: How to setup QueryDocumentService endpoints
sidebar:
  order: 3
---
Each type of query document needs to be served by its own dedicated endpoint. Thanks to our GraphQL engine we don't have
to write them by hand, but we can instead use one generic endpoint that gets multiplied into one query per row type we 
declare. 

We use this general qlive-graphql pattern to serve out [query documents](/qlive-framework/explanation/query-documents/).

Here we see the setup for logic bean containing such a generic QueryDocumentService endpoint.

```java {31-37,44-46} title='QueryLogic.java'
import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.annotation.GraphQLTypeParam;
import io.github.qlivedev.model.QueryConfig;
import io.github.qlivedev.model.QueryDocument;
import io.github.qlivedev.runtime.query.QueryDocumentService;
import io.github.qlivedev.qlivetest.domain.tables.pojos.Bar;
import io.github.qlivedev.qlivetest.domain.tables.pojos.Foo;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Lazy;

/// Example logic
@GraphQLLogic
public class QueryLogic
{

    private final QueryDocumentService queryDocumentService;

    public QueryLogic(
        @Lazy QueryDocumentService queryDocumentService
    )
    {
        this.queryDocumentService = queryDocumentService;
    }


    /// Queries [T] objects based on the given query config
    @GraphQLQuery
    public <T> @NotNull QueryDocument<T> queryDocument(
        @GraphQLTypeParam(
            namePattern = "query*Document",
            typeNamePattern = "*Document",
            types = {
                Foo.class,
                Bar.class
            }
        )
        Class<T> type,
        DataFetchingEnvironment env,
        @NotNull QueryConfig config
    )
    {
        return queryDocumentService.buildQuery(type, env, config)
            .selectByFilter(true)
            .execute();
    }
}
```

`@GraphQLTypeParam` controls how the generic method is turned into many concrete methods:

 * `types` lists all rowTypes we want. 
 * `namePattern` declares the name pattern for the GraphQL method. 
 * `typeNamePattern` declares the pattern for the result type name

So here we declare `Foo.class` and `Bar.class` as the row types we want. The result schema corresponding to this will
be

```graphql
type QueryType {
  "Queries Foo objects based on the given query config"
  queryFooDocument(config: QueryConfig!): FooDocument!
  "Queries Bar objects based on the given query config"
  queryBarDocument(config: QueryConfig!): BarDocument!
  # ...
}
```
:::note

Remember to [regenerate the TypeScript types](/qlive-framework/how-to/regenerate-from-the-schema/) for any schema change on the server side.

:::

## `selectByFilter`

The one option worth understanding. It decides how far a `QueryConfig`
posted by a browser may reach.

- **`false` (the default) is the strict mode.** A filter or sort path may
  only name a field the query actually selects. That is what makes the
  query document itself the
  [security boundary](/qlive-framework/explanation/query-documents/#why-the-document-is-the-security-boundary).
- **`true` lets a path extend the plan**: relations it crosses get joined,
  and the field it ends at gets selected. More convenient, and a wider
  surface -- a client can then read through relations the query did not
  select.

`qlive-test` passes `true` because it is a test application that only has security examples and no real security. 

## Next
                                                                     
- Read about [why the document can be a security boundary](/qlive-framework/explanation/query-documents/#why-the-document-can-be-a-security-boundary)
- A page size cap, a default sort or a standing condition per type:
  [Define the domain](/qlive-framework/how-to/define-the-domain/#the-query-config-a-type-suggests).
- Row-level security, or redefining what "no config" means:
  [Customize a document query](/qlive-framework/how-to/customize-a-document-query/).
