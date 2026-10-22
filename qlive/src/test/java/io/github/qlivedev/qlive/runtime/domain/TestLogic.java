package io.github.qlivedev.qlive.runtime.domain;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.api.annotation.QLiveTypeParam;
import io.github.qlivedev.qlive.model.QueryConfig;
import io.github.qlivedev.qlive.model.QueryDocument;
import io.github.qlivedev.qlive.testdomain.tables.pojos.TestFoo;
import io.github.qlivedev.qlive.testdomain.tables.pojos.TestUser;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@QLiveLogic
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
    @QLiveQuery
    public <T> @NotNull QueryDocument<T> queryDocument(
        @QLiveTypeParam(
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
    @QLiveQuery
    public @NotNull List<Integer> queryPageSizes(@NotNull List<QueryConfig> configs)
    {
        return configs.stream().map(QueryConfig::getPageSize).toList();
    }
}
