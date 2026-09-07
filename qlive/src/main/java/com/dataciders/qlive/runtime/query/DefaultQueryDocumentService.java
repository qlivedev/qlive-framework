package com.dataciders.qlive.runtime.query;

import com.dataciders.qlive.model.QueryConfig;
import com.dataciders.qlive.model.QueryDocument;
import de.quinscape.domainql.DomainQL;
import graphql.GraphQL;
import graphql.schema.DataFetchingEnvironment;
import org.jooq.DSLContext;

/// Default {@link QueryDocumentService} implementation, querying the application's JOOQ schema.
public class DefaultQueryDocumentService
    implements QueryDocumentService
{
    private final DomainQL domainQL;

    private final DSLContext dslContext;

    private final GraphQL graphQL;

    private final QueryPlanBuilder planBuilder;


    public DefaultQueryDocumentService(DomainQL domainQL, DSLContext dslContext, GraphQL graphQL)
    {
        this.domainQL = domainQL;
        this.dslContext = dslContext;
        this.graphQL = graphQL;
        this.planBuilder = new QueryPlanBuilder(domainQL);
    }


    @Override
    public <T> DocumentQueryBuilder<T> buildQuery(Class<T> type, DataFetchingEnvironment env, QueryConfig config)
    {
        return new DocumentQueryBuilder<>(this::execute, type, env, config);
    }


    private <T> QueryDocument<T> execute(DocumentQueryBuilder<T> query)
    {
        final QueryPlan plan = planBuilder.build(
            query.getType(),
            query.getEnv(),
            query.getConfig(),
            query.isSelectByFilter()
        );

        // one execution per query: it collects what it materialises as it goes, so that the relations it
        // cannot join can find their parents
        return new QueryExecution(dslContext).execute(query.getType(), plan);
    }


    public DomainQL getDomainQL()
    {
        return domainQL;
    }


    public GraphQL getGraphQL()
    {
        return graphQL;
    }
}
