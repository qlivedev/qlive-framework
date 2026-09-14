---
title: QLive in Java
description: How to use QLive in Java
sidebar:
  order: 7
---

QLive inherits the basic GraphQL flavor based on Spring Boot, Java, and jOOQ from DomainQL. It replaces DomainQL's rudimentary
data-fetching with a sophisticated Graph querying engine that analyzes a query and splits it into the optimal amount
of nicely joined SQL queries.
                             

## GraphQL endpoints

Here we see an end point definition, that might very well serve all data requests in a simple application.

```java {6, 10, 16} title='QueryAndMutationExample.java'
import de.quinscape.domainql.annotation.GraphQLLogic;
import de.quinscape.domainql.annotation.GraphQLQuery;
import de.quinscape.domainql.annotation.GraphQLMutation;
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
The most important thing are the two main annotations. `@GraphQLLogic` is a spring meta annotation that declares that this
is a spring bean and that it contains GraphQL methods.

`@GraphQLQuery` declares that the given method is part of the GraphQL schema. The input types, output types, and scalars
it uses will be automatically added to the Schema. The logic definitions take precedence over the definitions coming from
the database POJOs, which means it is possible to replace an auto-generated type with your own implementation to add
GraphQL fields or define GraphQL types that are not clear from the database.

`@GraphQLMutation` is the write side. You should declare all modifications as mutations.


## Hand-written types on the Java side

When a table's columns do not say everything about a type, replace the
generated POJO with a handwritten class that extends it, and register it
with `objectType()` after the schema's own types. DomainQL resolves a
domain type by simple name, so yours takes the generated one's place --
including for the query document service, which materializes whatever the
table lookup names.

Extending the generated POJO is what keeps it able to hold a row: the
columns, their JPA annotations and the fetcher context all come along.

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

A query selecting it should select the fields it is computed from as well
-- nothing fetches a column on its account. A filter is the other case: a
computed property cannot go into a `WHERE` clause, and a filter path naming
one is an error.

## QueryDocumentService

The query document service is the central data access service for QLive. It analyzes the currently executed GraphQL query
and creates a query plan spanning one or more queries. `com.dataciders.qlive.model.QueryConfig` instances control
how the query is executed. 

```typescript title="QueryConfig.ts"
export interface QueryConfig
{
    offset: number;
    pageSize: number;
    condition: FilterExpression | null;
    sortFields: FieldExpression[];
}
```
`offset` and `pageSize` control pagination, `condition` declares the condition (null = no condition), and sortFields
defines the table sorting. This queryConfig must be passed to runtime queries to execute them. Only for injections the
config can be implicitly derived.
                                 
Here we see the basic QueryDocumentService endpoint which could very well satisfy all data requests in a simple application.
The setup is a bit complicated to explain but really easy to use.

```java {31-37,44-46} title='QueryLogic.java'
import de.quinscape.domainql.annotation.GraphQLLogic;
import de.quinscape.domainql.annotation.GraphQLQuery;
import de.quinscape.domainql.annotation.GraphQLTypeParam;
import com.dataciders.qlive.model.QueryConfig;
import com.dataciders.qlive.model.QueryDocument;
import com.dataciders.qlive.runtime.query.QueryDocumentService;
import com.dataciders.qlivetest.domain.tables.pojos.Bar;
import com.dataciders.qlivetest.domain.tables.pojos.Foo;
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
We have the aforementioned annotations for the basic wiring plus some more DomainQL magic to define a GraphQL query based
on a generic Java method. Since GraphQL knows no generics, we need to create a concrete type for every QueryDocument<T>
we want to exist. 

`@GraphQLTypeParam` defines the details of the type creation. For every type T mentioned in `types`, we create a type that is 
QueryDocument<T> on the Java side and here `FooDocument` or `BarDocument` in GraphQL and TypeScript. 
Also, each type of course needs its own query method returning it, named `queryFooDocument` and `queryBarDocument` in 
this example.   
                    
As implementation, we just need to invoke `buildQuery()` with the query defining parameters. `type` is the runtime row class
type, `env` is the GraphQL environment containing the current selection and `config` is our QueryConfig input.

## QueryDocuments

The QueryDocument returned is a container for the results that also contains the QueryConfig it was created with so the 
user can apply a QueryConfigDelta to it and update the query. Next page, different sorting, different filter etc.

On the client side, the query document implements its own storage, `useInjection()` returns a snapshot of the document
with an update method that can be used to receive the next snapshot etc. QueryDocument is mutable while the snapshots
enjoy all the benefits React has for immutable data.

```ts title="QueryDocumentSnapshot<T>"
interface QueryDocumentSnapshot<T> {
    type: string
    config: QueryConfig
    rows: T[]
    rowCount: number
    update(newConfig: QueryConfigDelta): Promise<QueryDocumentSnapshot<T>>
}
```

See [the QueryDocuments page](/qlive-framework/reference/query-documents/) for more details on QueryDocuments.

## Customizing a query

You can have as many QueryDocumentService endpoints with as many
configurations as you want. They can even share row types. You are in
control before you invoke the service and after you received the result --
see
[Customize a document query](/qlive-framework/how-to/customize-a-document-query/).
