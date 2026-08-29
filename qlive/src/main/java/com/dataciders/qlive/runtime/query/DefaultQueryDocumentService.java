package com.dataciders.qlive.runtime.query;

import de.quinscape.domainql.DomainQL;
import com.dataciders.qlive.model.QueryConfig;
import graphql.GraphQL;
import graphql.schema.DataFetchingEnvironment;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultQueryDocumentService
    implements QueryDocumentService
{
    private final static Logger log = LoggerFactory.getLogger(DefaultQueryDocumentService.class);

    private final DomainQL domainQL;
    private final DSLContext dslContext;
    private final GraphQL graphQL;


    public DefaultQueryDocumentService(
        DomainQL domainQL,
        DSLContext dslContext,
        GraphQL graphQL
    )
    {
        this.domainQL = domainQL;
        this.dslContext = dslContext;
        this.graphQL = graphQL;
    }


    @Override
    public <T> QueryPlanBuilder<T> buildQuery(Class<T> type, DataFetchingEnvironment env, QueryConfig config)
    {
        final QueryExecutionContext ctx = new QueryExecutionContext(
            domainQL,
            dslContext,
            graphQL,
            type,
            env,
            config
        );

        return new QueryPlanBuilder<>(ctx);
    }

}
