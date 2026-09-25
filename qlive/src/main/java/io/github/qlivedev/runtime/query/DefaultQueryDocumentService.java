package io.github.qlivedev.runtime.query;

import io.github.qlivedev.model.QueryConfig;
import io.github.qlivedev.model.QueryDocument;
import io.github.qlivedev.runtime.meta.QueryConfigMeta;
import io.github.qlivedev.graphql.QLiveDomain;
import graphql.GraphQL;
import graphql.schema.DataFetchingEnvironment;
import org.jooq.DSLContext;

/// Default {@link QueryDocumentService} implementation, querying the application's JOOQ schema.
public class DefaultQueryDocumentService
    implements QueryDocumentService
{
    private final QLiveDomain domain;

    private final DSLContext dslContext;

    private final GraphQL graphQL;

    private final QueryPlanBuilder planBuilder;


    public DefaultQueryDocumentService(QLiveDomain domain, DSLContext dslContext, GraphQL graphQL)
    {
        this.domain = domain;
        this.dslContext = dslContext;
        this.graphQL = graphQL;
        this.planBuilder = new QueryPlanBuilder(domain);
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
            limitPage(query.getType(), query.getConfig()),
            query.isSelectByFilter()
        );

        // one execution per query: it collects what it materialises as it goes, so that the relations it
        // cannot join can find their parents
        return new QueryExecution(dslContext).execute(query.getType(), plan);
    }


    /// The given config, held to the maximum page size the queried type declares.
    ///
    /// Here rather than where a config is built, because this is the one place every query document query
    /// passes through: a config that came out of an injection, one a browser posted, one a logic bean wrote
    /// by hand -- all of them are read here, and none of them can talk its way past this. A page size of 0
    /// asks for every row there is, so it is exactly the case a maximum is declared for and becomes the
    /// maximum like any other page that is too large.
    ///
    /// The config that comes back is the one that was applied: the plan carries it into the document, the
    /// client spreads its next update() over it, and a page that was cut says so rather than looking like
    /// the last page of a short table.
    private QueryConfig limitPage(Class<?> type, QueryConfig config)
    {
        final int maxPageSize = QueryConfigMeta.maxPageSizeForType(domain, type);

        if (maxPageSize == 0 || (config.getPageSize() > 0 && config.getPageSize() <= maxPageSize))
        {
            return config;
        }

        // a copy, because the config belongs to whoever passed it in -- for a client query that is the
        // coerced GraphQL variable, which nothing here has any business changing
        final QueryConfig limited = new QueryConfig();
        limited.setCondition(config.getCondition());
        limited.setOffset(config.getOffset());
        limited.setPageSize(maxPageSize);
        limited.setSortFields(config.getSortFields());

        return limited;
    }


    public QLiveDomain getDomain()
    {
        return domain;
    }


    public GraphQL getGraphQL()
    {
        return graphQL;
    }
}
