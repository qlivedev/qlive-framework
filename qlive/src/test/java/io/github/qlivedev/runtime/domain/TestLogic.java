package io.github.qlivedev.runtime.domain;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLQuery;
import io.github.qlivedev.graphql.annotation.GraphQLTypeParam;
import io.github.qlivedev.model.QueryConfig;
import io.github.qlivedev.model.QueryDocument;
import io.github.qlivedev.testdomain.tables.pojos.TestFoo;
import io.github.qlivedev.testdomain.tables.pojos.TestUser;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@GraphQLLogic
public class TestLogic
{
    private final static Logger log = LoggerFactory.getLogger(TestLogic.class);

    /**
     * Queries [T] objects based on the given query config
     *
     * @param type
     * @param env
     * @param config    configuration for the [T] query.
     * @param <T>
     *
     * @return query document
     */
    @GraphQLQuery
    public <T> @NotNull QueryDocument<T> queryDocument(
        @GraphQLTypeParam(
            namePattern = "query*Document",
            typeNamePattern = "*Document",
            types = {
                TestFoo.class,
                TestUser.class
            }
        )
        Class<T> type,
        DataFetchingEnvironment env,
        @NotNull QueryConfig config
    )
    {

        log.info("QueryDocument<{}>, config = {}", type, config);

        // Echoes the config back instead of querying anything: there is no database behind this schema, and
        // what the execution tests need to see is that the config reached the query at all.
        final QueryDocument<T> document = new QueryDocument<>(type);
        document.setConfig(config);
        document.setRows(List.of());

        return document;
    }


    /**
     * Echoes back the page size of every given config, so that a list-typed injection argument can be seen
     * arriving the way a single one is.
     */
    @GraphQLQuery
    public @NotNull List<Integer> queryPageSizes(@NotNull List<QueryConfig> configs)
    {
        return configs.stream().map(QueryConfig::getPageSize).toList();
    }
}
