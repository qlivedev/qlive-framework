package com.dataciders.qlive.runtime.domain;

import de.quinscape.domainql.annotation.GraphQLLogic;
import de.quinscape.domainql.annotation.GraphQLQuery;
import de.quinscape.domainql.annotation.GraphQLTypeParam;
import com.dataciders.qlive.model.QueryConfig;
import com.dataciders.qlive.model.QueryDocument;
import com.dataciders.qlive.testdomain.tables.pojos.TestFoo;
import com.dataciders.qlive.testdomain.tables.pojos.TestUser;
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
}
