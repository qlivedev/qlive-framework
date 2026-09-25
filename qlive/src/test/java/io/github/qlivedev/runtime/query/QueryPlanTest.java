package io.github.qlivedev.runtime.query;

import io.github.qlivedev.model.QueryConfig;
import io.github.qlivedev.model.condition.CNode;
import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.runtime.domain.TestDomainConfig;
import io.github.qlivedev.graphql.QLiveDomain;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.jooq.SQLDialect;
import org.jooq.conf.ParamType;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.qlivedev.runtime.scalar.FilterDSL.field;
import static io.github.qlivedev.runtime.scalar.FilterDSL.value;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Covers what a query document query comes to: which tables it reaches, how it reaches them, and what a
/// query config is and is not allowed to add to that.
///
/// The plans are made from selection sets a real GraphQL execution produced, because the selection is the
/// input that decides everything else. The config is built in Java instead of traveling as a variable:
/// what the coercing does with a condition is its own business and its own test.
class QueryPlanTest
{
    private final static String FOO_WITH_OWNER =
        "query Q($config: QueryConfig!) { queryTestFooDocument(config: $config) { rows { id name owner { id login } } } }";

    private final static String FOO_ALONE =
        "query Q($config: QueryConfig!) { queryTestFooDocument(config: $config) { rows { id name } } }";

    private final static String USER_WITH_FOOS =
        "query Q($config: QueryConfig!) { queryTestUserDocument(config: $config) { rows { id login testFoos { id name fooType { name } } } } }";


    /// A to-one relation is a left join on an alias named after the relation field, not after the table it
    /// happens to lead to.
    @Test
    void joinsToOneRelations()
    {
        final QueryConfig config = config(10, 0);
        config.setCondition(field("name").eq(value("x")));

        assertThat(
            sql(FOO_WITH_OWNER, config, false),
            is(
                "select \"test_foo\".\"id\", \"test_foo\".\"name\", \"owner\".\"id\", \"owner\".\"login\" " +
                    "from \"public\".\"test_foo\" as \"test_foo\" " +
                    "left outer join \"public\".\"test_user\" as \"owner\" " +
                    "on \"test_foo\".\"owner_id\" = \"owner\".\"id\" " +
                    "where \"test_foo\".\"name\" = 'x' " +
                    "order by \"test_foo\".\"id\" asc " +
                    "offset 0 rows fetch next 10 rows only"
            )
        );
    }


    /// A to-many relation is never joined -- it would multiply the rows and take the page and the count
    /// with it -- so a condition reaching through one becomes a correlated EXISTS instead. Whatever the
    /// subquery needs to join to answer that, it joins for itself.
    @Test
    void filtersThroughToManyRelationsWithExists()
    {
        final QueryConfig config = config(0, 0);
        config.setCondition(field("testFoos.fooType.name").eq(value("x")));

        assertThat(
            sql(USER_WITH_FOOS, config, false),
            is(
                "select \"test_user\".\"id\", \"test_user\".\"login\" " +
                    "from \"public\".\"test_user\" as \"test_user\" " +
                    "where exists (" +
                    "select 1 from \"public\".\"test_foo\" as \"test_foos\" " +
                    "left outer join \"public\".\"test_foo_type\" as \"test_foos_foo_type\" " +
                    "on \"test_foos\".\"type\" = \"test_foos_foo_type\".\"name\" " +
                    "where (\"test_foos\".\"owner_id\" = \"test_user\".\"id\" " +
                    "and \"test_foos_foo_type\".\"name\" = 'x')" +
                    ") " +
                    "order by \"test_user\".\"id\" asc"
            )
        );
    }


    /// Ordering by a set of rows would need an aggregate, and the FilterDSL has no way to say which.
    @Test
    void refusesToSortThroughAToManyRelation()
    {
        final QueryConfig config = config(0, 0);
        config.setSortFields(List.of(field("testFoos.name")));

        final QLiveException e = assertThrows(QLiveException.class, () -> sql(USER_WITH_FOOS, config, false));
        assertThat(e.getMessage(), containsString("cannot follow a to-many relation"));
    }


    /// The strict mode is what makes the query document the boundary: the config varies the where, the
    /// order and the page, and can reach nothing the query itself does not name.
    @Test
    void strictModeRefusesFieldsTheQueryDoesNotSelect()
    {
        final QueryConfig byColumn = config(0, 0);
        byColumn.setCondition(field("description").eq(value("x")));

        assertThat(
            assertThrows(QLiveException.class, () -> sql(FOO_ALONE, byColumn, false)).getMessage(),
            containsString("which the query does not select")
        );

        final QueryConfig byRelation = config(0, 0);
        byRelation.setCondition(field("owner.login").eq(value("x")));

        assertThat(
            assertThrows(QLiveException.class, () -> sql(FOO_ALONE, byRelation, false)).getMessage(),
            containsString("which the query does not select")
        );

        final QueryConfig bySort = config(0, 0);
        bySort.setSortFields(List.of(field("description")));

        assertThrows(QLiveException.class, () -> sql(FOO_ALONE, bySort, false));
    }


    /// The same paths with selectByFilter: the filter extends the plan instead of being refused, joining
    /// what it has to cross and selecting what it ends at.
    @Test
    void selectByFilterExtendsThePlan()
    {
        final QueryConfig config = config(0, 0);
        config.setCondition(field("owner.login").eq(value("admin")));

        final String sql = sql(FOO_ALONE, config, true);

        assertThat(sql, containsString("left outer join \"public\".\"test_user\" as \"owner\""));
        assertThat(sql, containsString("\"owner\".\"login\""));
        assertThat(sql, containsString("where \"owner\".\"login\" = 'admin'"));
    }


    /// A path that names nothing is an error, never a condition quietly left out.
    @Test
    void refusesPathsThatNameNothing()
    {
        final QueryConfig unknownField = config(0, 0);
        unknownField.setCondition(field("nonexistent").eq(value("x")));

        assertThat(
            assertThrows(QLiveException.class, () -> sql(FOO_ALONE, unknownField, true)).getMessage(),
            containsString("has no database field 'nonexistent'")
        );

        final QueryConfig unknownRelation = config(0, 0);
        unknownRelation.setCondition(field("nonexistent.name").eq(value("x")));

        assertThat(
            assertThrows(QLiveException.class, () -> sql(FOO_ALONE, unknownRelation, true)).getMessage(),
            containsString("has no relation 'nonexistent'")
        );
    }


    /// A handwritten type can add fields the table has no column for. QLiveDomain fetches those from the
    /// object, so the planner has nothing to select for them and says so by leaving them alone -- rather
    /// than refusing a query it has no reason to refuse.
    @Test
    void ignoresFieldsThatAreNotColumns()
    {
        final String sql = sql(
            "query Q($config: QueryConfig!) { queryTestFooDocument(config: $config) { rows { id name summary } } }",
            config(0, 0),
            false
        );

        assertThat(sql, containsString("\"test_foo\".\"name\""));
        assertThat(sql, not(containsString("summary")));
    }


    /// A filter is the other case: there is no way to put a computed property into a WHERE clause, so a
    /// path naming one is an error however permissive the query is.
    @Test
    void refusesToFilterByFieldsThatAreNotColumns()
    {
        final QueryConfig config = config(0, 0);
        config.setCondition(field("summary").eq(value("x")));

        assertThat(
            assertThrows(
                QLiveException.class,
                () -> sql(
                    "query Q($config: QueryConfig!) { queryTestFooDocument(config: $config) { rows { id summary } } }",
                    config,
                    true
                )
            ).getMessage(),
            containsString("has no database field 'summary'")
        );
    }


    /// The count joins what its condition reads and nothing else. The rest cannot change a count -- they
    /// are all left joins on keys -- so joining them would be work nobody reads.
    @Test
    void countsWithoutTheJoinsTheConditionDoesNotRead()
    {
        final QueryConfig unfiltered = config(10, 0);
        assertThat(
            count(FOO_WITH_OWNER, unfiltered),
            is("select count(*) from \"public\".\"test_foo\" as \"test_foo\"")
        );

        final QueryConfig byRootColumn = config(10, 0);
        byRootColumn.setCondition(field("name").eq(value("x")));
        assertThat(count(FOO_WITH_OWNER, byRootColumn), not(containsString("join")));

        // ... and keeps the one it does read
        final QueryConfig byRelation = config(10, 0);
        byRelation.setCondition(field("owner.login").eq(value("admin")));
        assertThat(
            count(FOO_WITH_OWNER, byRelation),
            is(
                "select count(*) from \"public\".\"test_foo\" as \"test_foo\" " +
                    "left outer join \"public\".\"test_user\" as \"owner\" " +
                    "on \"test_foo\".\"owner_id\" = \"owner\".\"id\" " +
                    "where \"owner\".\"login\" = 'admin'"
            )
        );
    }


    /// A path through a to-many relation is answered by a subquery that joins what it needs for itself, so
    /// nothing of it reaches the outer query.
    @Test
    void countsThroughToManyRelationsWithoutJoiningThem()
    {
        final QueryConfig config = config(10, 0);
        config.setCondition(field("testFoos.fooType.name").eq(value("x")));

        final String sql = count(USER_WITH_FOOS, config);

        assertThat(sql, containsString("from \"public\".\"test_user\" as \"test_user\" where exists"));
        assertThat(sql, containsString("select 1 from \"public\".\"test_foo\" as \"test_foos\""));
    }


    @Test
    void pagesAsTheConfigSaid()
    {
        assertThat(sql(FOO_ALONE, config(0, 0), false), not(containsString("fetch next")));
        assertThat(sql(FOO_ALONE, config(0, 5), false), containsString("offset 5 rows"));
        assertThat(sql(FOO_ALONE, config(20, 5), false), containsString("offset 5 rows fetch next 20 rows only"));
    }


    /// With no sort fields the primary key is the sort, and the config that goes back to the client says
    /// so -- the client spreads its next update over exactly this config.
    @Test
    void defaultsTheSortToThePrimaryKey()
    {
        final QueryPlan plan = plan(FOO_ALONE, config(0, 0), false);

        final List<CNode> sortFields = plan.config().getSortFields();
        assertThat(
            sortFields.stream()
                .map(node -> ((io.github.qlivedev.model.condition.Field) node).getName())
                .toList(),
            contains("id")
        );
    }


    /// Aliases follow the path, and stay unique and short enough for the database to keep them apart.
    @Test
    void namesAliasesAfterThePath()
    {
        assertThat(QueryPlanBuilder.snakeCase("TestFooType"), is("test_foo_type"));
        assertThat(QueryPlanBuilder.snakeCase("fooId"), is("foo_id"));
        assertThat(QueryPlanBuilder.snakeCase("bazLinks"), is("baz_links"));

        final Set<String> used = new HashSet<>();
        assertThat(QueryPlanBuilder.uniqueAlias("a_b_c", used), is("a_b_c"));
        assertThat(QueryPlanBuilder.uniqueAlias("a_b_c", used), is("a_b_c_2"));
        assertThat(QueryPlanBuilder.uniqueAlias("a_b_c", used), is("a_b_c_3"));

        final String tooLong = "x".repeat(70);
        assertThat(QueryPlanBuilder.uniqueAlias(tooLong, used).length(), is(63));
        assertThat(QueryPlanBuilder.uniqueAlias(tooLong, used).length(), is(63));
    }


    // -----------------------------------------------------------------------------------------------------

    private static QueryConfig config(int pageSize, int offset)
    {
        final QueryConfig config = new QueryConfig();
        config.setPageSize(pageSize);
        config.setOffset(offset);
        return config;
    }


    private static String count(String query, QueryConfig config)
    {
        return new QueryExecution(DSL.using(SQLDialect.POSTGRES))
            .countQuery(plan(query, config, false))
            .getSQL(ParamType.INLINED);
    }


    private static String sql(String query, QueryConfig config, boolean selectByFilter)
    {
        return new QueryExecution(DSL.using(SQLDialect.POSTGRES))
            .mainQuery(plan(query, config, selectByFilter))
            .getSQL(ParamType.INLINED);
    }


    private static QueryPlan plan(String query, QueryConfig config, boolean selectByFilter)
    {
        final QueryTestLogic logic = new QueryTestLogic();
        final QLiveDomain domain = TestDomainConfig.domain(logic);

        final ExecutionResult result = GraphQL.newGraphQL(domain.getGraphQLSchema())
            .build()
            .execute(
                ExecutionInput.newExecutionInput(query)
                    .variables(Map.of("config", Map.of("pageSize", 0, "offset", 0)))
                    .build()
            );

        assertThat(result.getErrors(), is(empty()));

        final QueryTestLogic.Capture capture = logic.getCaptures().get(0);

        return new QueryPlanBuilder(domain).build(capture.type(), capture.env(), config, selectByFilter);
    }
}
