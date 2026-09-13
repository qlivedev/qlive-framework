---
title: QLive in Java
description: How to use QLive in Java
sidebar:
  order: 3
---

QLive inherits the basic GraphQL flavor based on Spring Boot, Java, and JOOQ from DomainQL. It replaces DomainQLs rudimentary
data-fetching with a sophisticated Graph querying engine that analyses a query and splits it into the optimal amount
of nicely joined SQL queries.
                             

## GraphQL endpoints

Here we see an end point definition, that might very well serve all data requests in a simple application.

```java {6, 10, 16} title='Endpoints.java'
import de.quinscape.domainql.annotation.GraphQLLogic;
import de.quinscape.domainql.annotation.GraphQLQuery;
import de.quinscape.domainql.annotation.GraphQLMutation;
import jakarta.validation.constraints.NotNull;

@GraphQLLogic
public class QueryLogic
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
The most importing thing are the two main annotations. `@GraphQLLogic` is a spring meta annotation that declares that this
is a spring bean and that it contains GraphQL methods.

`@GraphQLQuery` declares that the given method is part of the GraphQL schema. The input types, output types, and scalars
it uses will be automatically added to the Schema. The logic definitions take precedence over the definitions coming from
the database POJOs, which means it is possible to replace an auto-generated type with your own implementation to add
GraphQL fields or define GraphQL types that are not clear from the database.

`@GraphQLMutation` is the write side. You should declare all modifications as mutations.


## QueryDocumentService

The query document service it the central data access service for QLive. It analyzes the currently executed GraphQL query
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
Also , each type of course needs its own query method returning it, named `queryFooDocument` and `queryBarDocument` in 
this example.   
                    
As implementation, we just need to invoke `buildQuery()` with the query defining parameters. `type` is the runtime row class
type, `env` is the GraphQL environment containing the current selection and `config` is our QueryConfig input.

## QueryDocuments

The QueryDocument returned is a container for the results that also contains the QueryConfig it was created with so the 
user can apply a QueryConfigDelta to it and update the query. Next page, different sorting, different filter etc.

On the client side, the query document implements its on storage, `useInjection()` returns a snapshot of the document
with an update method that can be used to receive the next snapshot etc. QueryDocument is mutable while the snapshots
enjoy all the benefits React has for immutable data.

```ts title="QueryDocumentService<T>"
interface QueryDocumentSnapshot<T> {
    type: string
    config: QueryConfig
    rows: T[]
    rowCount: number
    update(newConfig: QueryConfigDelta): Promise<QueryDocumentSnapshot<T>>
}
```

See [the QueryDocuments page](/qlive-framework/query-documents/) for more details on QueryDocuments.

## QueryDocumentService Patterns
                                                                              
You can have as many QueryDocumentService endpoints with as many configurations as you want. They can even share
row types. 

Note how you are in control before you invoke the service and after you received the result. We can use this for all kinds
of interesting ways to create specialized endpoint implementations.

### Pattern: Defaults

If you use an injection with default params, the handling of the QueryConfig parameter is special. We don't even
define the config level, and then we have no QueryConfig object which could be coerced into a valid object with normal
GraphQL.

Instead, we not only make sure that your query method is called with a proper QueryConfig object, this object also reflects
what the meta configuration suggests for that type. The default `pageSize`, default sorting, etc. 

The meaning of no object is normally: "Give me this object with default configuration", but you can redefine 
it to mean something else: "The latest edited favorites of the current user" or whatever satisfies your needs. You
just intercept the QueryConfig before you pass it into the service, detect that it is the default and replace e.g. the
condition with the complex condition of your liking and also a complex sortFields expression list.

You just replace the QueryConfig object, the query will execute with your config. The same config will be returned to 
the user who can then update the query starting from that point.

### Pattern: Manual Security

Starts out as the pattern before, but we just extend the incoming condition of all incoming queries 
with the necessary filters the security rules demand for the respective type. In this case it is usually preferable 
to restore the original condition so that the user can just continue to view "their" data with minimal filter.
