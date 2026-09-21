package io.github.qlivedev.runtime.query;

import io.github.qlivedev.model.QueryConfig;
import io.github.qlivedev.model.QueryDocument;
import io.github.qlivedev.testdomain.tables.pojos.TestUser;
import de.quinscape.domainql.annotation.GraphQLLogic;
import de.quinscape.domainql.annotation.GraphQLQuery;
import de.quinscape.domainql.annotation.GraphQLTypeParam;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

/// Logic bean that records what a query document query was asked for, so that a test can plan the query
/// the GraphQL execution actually described instead of a selection set assembled by hand.
@GraphQLLogic
public class QueryTestLogic
{
    public record Capture(
        Class<?> type,
        DataFetchingEnvironment env,
        QueryConfig config
    )
    {
    }


    private final List<Capture> captures = new ArrayList<>();


    public List<Capture> getCaptures()
    {
        return captures;
    }


    @GraphQLQuery
    public <T> @NotNull QueryDocument<T> queryDocument(
        @GraphQLTypeParam(
            namePattern = "query*Document",
            typeNamePattern = "*Document",
            types = {
                // the handwritten TestFoo, which is what puts it in the generated one's place: a type
                // reached from a logic bean is registered as an output type, and DomainQL resolves the
                // simple name against those
                io.github.qlivedev.testmodel.types.TestFoo.class,
                TestUser.class
            }
        )
        Class<T> type,
        DataFetchingEnvironment env,
        @NotNull QueryConfig config
    )
    {
        captures.add(new Capture(type, env, config));

        final QueryDocument<T> document = new QueryDocument<>(type);
        document.setConfig(config);
        document.setRows(List.of());
        return document;
    }
}
