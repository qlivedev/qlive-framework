---
title: Expose document queries
description: One generic method that becomes queryFooDocument, queryBarDocument and the rest.
sidebar:
  order: 2
---

An application exposes
[query documents](/qlive-framework/explanation/query-documents/) from a
logic bean. GraphQL knows no generics, so a concrete type has to exist for
every `QueryDocument<T>` you want -- but you write one Java method and let
QLive make them.

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

`@GraphQLTypeParam` is what turns the one method into `queryFooDocument`,
`queryBarDocument` and the rest: for every type in `types` it creates a type
that is `QueryDocument<T>` on the Java side and `FooDocument` or
`BarDocument` in GraphQL and TypeScript, plus the query method returning it.

**Adding a type to `types` adds a query.** That is the whole of extending
this, and it is also a schema change -- so
[regenerate](/qlive-framework/how-to/regenerate-from-the-schema/) afterwards.

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

`qlive-test` passes `true` because it is a test application exercising the
DSL. An application serving real data should have a reason before it does.

## Next

- A page size cap, a default sort or a standing condition per type:
  [Add schema metadata](/qlive-framework/how-to/add-schema-metadata/).
- Row-level security, or redefining what "no config" means:
  [Customize a document query](/qlive-framework/how-to/customize-a-document-query/).
