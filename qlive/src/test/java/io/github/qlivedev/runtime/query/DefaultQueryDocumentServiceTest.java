package io.github.qlivedev.runtime.query;

import io.github.qlivedev.model.QueryConfig;
import io.github.qlivedev.model.QueryDocument;
import io.github.qlivedev.runtime.domain.TestDomainConfig;
import io.github.qlivedev.runtime.meta.QueryConfigMetadataProvider;
import io.github.qlivedev.testmodel.types.TestFoo;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.meta.MetadataProvider;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/// What the service does to a query config before it plans anything, which is where the maximum page size a
/// type declares is applied.
///
/// The queries run against a mock connection: what they come to is the point, and what a database would
/// answer them with is not.
class DefaultQueryDocumentServiceTest
{
    private final static String FOO =
        "query Q($config: QueryConfig!) { queryTestFooDocument(config: $config) { rows { id name } } }";


    /// A page larger than the type allows becomes the largest one it allows.
    @Test
    void limitsAPageToWhatTheTypeAllows()
    {
        final QueryConfig config = config(500, 20);

        final Run run = run(config);

        assertThat(run.sql().getFirst(), containsString("fetch next ? rows only"));
        assertThat(run.document().getConfig().getPageSize(), is(100));

        // and the offset the caller asked for is still the offset
        assertThat(run.document().getConfig().getOffset(), is(20));

        // the config the caller passed in is the caller's, and for a client query it is the coerced
        // variable of a query that is still running
        assertThat(config.getPageSize(), is(500));
    }


    /// A page size of 0 asks for every row there is, which is exactly what a maximum is declared against.
    @Test
    void limitsAQueryThatAsksForEveryRow()
    {
        final Run run = run(config(0, 0));

        assertThat(run.document().getConfig().getPageSize(), is(100));

        // paged rather than unpaged, and counted: the row count is what a client pages by, and the rows it
        // just received are no longer all of them
        assertThat(run.sql().getFirst(), containsString("fetch next ? rows only"));
        assertThat(run.sql().getLast(), containsString("count(*)"));
    }


    /// A page within the maximum is left where it is.
    @Test
    void leavesAPageWithinTheMaximumAlone()
    {
        final Run run = run(config(10, 5));

        assertThat(run.document().getConfig().getPageSize(), is(10));
        assertThat(run.document().getConfig().getOffset(), is(5));
    }


    /// A type declaring no maximum queries the way it did before there was one.
    @Test
    void leavesATypeWithoutAMaximumUnpaged()
    {
        final Run run = run(config(0, 0), null);

        assertThat(run.document().getConfig().getPageSize(), is(0));
        assertThat(run.sql().getFirst(), is(not(containsString("fetch next"))));
    }


    private static QueryConfig config(int pageSize, int offset)
    {
        final QueryConfig config = new QueryConfig();
        config.setPageSize(pageSize);
        config.setOffset(offset);
        return config;
    }


    /// One query document query, executed against a mock connection.
    ///
    /// @param document  the document it produced
    /// @param sql       the statements it ran, in the order it ran them
    private record Run(
        QueryDocument<?> document,
        List<String> sql
    )
    {
    }


    private static Run run(QueryConfig config)
    {
        return run(config, QueryConfigMetadataProvider.newProvider().forType(TestFoo.class).maxPageSize(100).build());
    }


    private static Run run(QueryConfig config, MetadataProvider metadataProvider)
    {
        final QueryTestLogic logic = new QueryTestLogic();

        final QLiveDomain domainQL = metadataProvider == null
            ? TestDomainConfig.domainQL(logic)
            : TestDomainConfig.domainQL(List.of(metadataProvider), logic);

        final GraphQL graphQL = GraphQL.newGraphQL(domainQL.getGraphQLSchema()).build();

        final ExecutionResult result = graphQL.execute(
            ExecutionInput.newExecutionInput(FOO)
                .variables(Map.of("config", Map.of("pageSize", 0, "offset", 0)))
                .build()
        );

        assertThat(result.getErrors(), is(empty()));

        final QueryTestLogic.Capture capture = logic.getCaptures().getFirst();

        final List<String> sql = new ArrayList<>();

        final QueryDocument<?> document = new DefaultQueryDocumentService(domainQL, mockContext(sql), graphQL)
            .buildQuery(capture.type(), capture.env(), config)
            .execute();

        return new Run(document, sql);
    }


    /// A DSL context whose statements all come back empty, recording what they were.
    private static DSLContext mockContext(List<String> sql)
    {
        final MockDataProvider provider = ctx -> {
            sql.add(ctx.sql());
            return new MockResult[]{new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

        return DSL.using(new MockConnection(provider), SQLDialect.POSTGRES);
    }
}
