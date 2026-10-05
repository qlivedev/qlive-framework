package io.github.qlivedev.runtime.query;

import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.runtime.domain.TestDomainConfig;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/// Runs query documents the way a browser does -- a GraphQL query with the config as a variable, through the
/// query document service -- against a database that answers with rows the test scripted.
///
/// What is under test is what QLive does on either side of the database: which statements a document comes
/// to, what it binds into them, how the rows that come back are put together into the document, and what
/// config the client gets back. Whether Postgres then finds the rows those statements describe is qlive-test's
/// QueryDocumentServiceTest, against a real database.
class QueryDocumentExecutionTest
{
    private final ScriptedDatabase database = new ScriptedDatabase();

    private final GraphQL graphQL = graphQL(database);


    /// A to-one relation comes back with the rows that carry it, out of the one statement that fetched
    /// them, and not out of a query per row afterwards.
    @Test
    void fetchesRowsWithTheirToOneRelations()
    {
        database.answer(
            Map.of(
                "test_foo.id", "foo-1", "test_foo.name", "Alpha", "test_foo.type", "TYPE_A",
                "test_foo.owner_id", "user-1", "owner.id", "user-1", "owner.login", "admin",
                "foo_type.name", "TYPE_A", "foo_type.ordinal", 0
            ),
            Map.of(
                "test_foo.id", "foo-2", "test_foo.name", "Bravo", "test_foo.type", "TYPE_B",
                "test_foo.owner_id", "user-2", "owner.id", "user-2", "owner.login", "anonymous",
                "foo_type.name", "TYPE_B", "foo_type.ordinal", 1
            )
        );

        final Map<String, Object> document = queryDocument(
            "queryTestFooDocument",
            "id name type ownerId owner { id login } fooType { name ordinal }",
            config(0, 0)
        );

        assertThat(document.get("type"), is("TestFoo"));
        assertThat(database.sql(), hasSize(1));
        assertThat(database.sql().get(0), containsString("left outer join \"public\".\"test_user\" as \"owner\""));

        final List<Map<String, Object>> rows = rows(document);
        assertThat(rows, hasSize(2));

        assertThat(nested(rows.get(0), "owner"), is(Map.of("id", "user-1", "login", "admin")));
        assertThat(nested(rows.get(0), "fooType"), is(Map.of("name", "TYPE_A", "ordinal", 0)));
        assertThat(nested(rows.get(1), "owner"), is(Map.of("id", "user-2", "login", "anonymous")));
        assertThat(nested(rows.get(1), "fooType"), is(Map.of("name", "TYPE_B", "ordinal", 1)));
    }


    /// The row count is of everything the condition matches, which is the number the client pages by, not
    /// the number of rows it just received. It is counted under the same condition the page was fetched
    /// under.
    @Test
    void countsWhatThePageLeftOut()
    {
        database
            .answer(fooRow("foo-1", "Alpha"), fooRow("foo-2", "Bravo"))
            .answer(Map.of("count", 3));

        final Map<String, Object> document = queryDocument(
            "queryTestFooDocument",
            "id name",
            Map.of("pageSize", 2, "offset", 0, "condition", eq("name", "String", "x"))
        );

        assertThat(rows(document), hasSize(2));
        assertThat(document.get("rowCount"), is(3));

        assertThat(database.sql().get(1), containsString("count(*)"));
        assertThat(database.sql().get(1), containsString("where \"test_foo\".\"name\" = ?"));
        assertThat(database.statements().get(1).bindings(), contains("x"));
    }


    /// With no page size the document is everything, and the config that comes back says what was applied.
    /// The primary-key sort the server falls back on is not part of that: nobody asked for it, and an empty
    /// sort echoed back gets it again.
    @Test
    void returnsTheConfigItActuallyUsed()
    {
        final Map<String, Object> document = queryDocument("queryTestFooDocument", "id name", config(0, 0));

        final Map<String, Object> config = config(document);

        assertThat(config.get("pageSize"), is(0));
        assertThat((List<?>) config.get("sortFields"), is(empty()));

        assertThat(database.sql().get(0), containsString("order by \"test_foo\".\"id\" asc"));
        assertThat(database.sql().get(0), not(containsString("fetch next")));
    }


    /// A sort field can be any field expression, not only a field name. It arrives as a node of the condition
    /// tree, reaches the statement as that expression, and the config that comes back carries it as it was
    /// sent.
    @Test
    void sortsByAnExpression()
    {
        // num * -1, ascending: the largest num first
        final Map<String, Object> negated = Map.of(
            "type", "Operation",
            "name", "mul",
            "operands", List.of(
                Map.of("type", "Field", "name", "num"),
                Map.of("type", "Value", "scalarType", "Int", "value", -1)
            )
        );

        final Map<String, Object> document = queryDocument(
            "queryTestFooDocument",
            "id name",
            Map.of("pageSize", 0, "offset", 0, "sortFields", List.of(negated))
        );

        assertThat(database.sql().get(0), containsString("order by (\"test_foo\".\"num\" * ?)"));
        assertThat(database.statements().get(0).bindings(), contains(-1));

        assertThat((List<?>) config(document).get("sortFields"), contains(negated));
    }


    /// A condition's values arrive as JSON, where a timestamp is a string. By the time one reaches the
    /// statement it is a Timestamp, because the condition scalar converted it with the coercing of the
    /// scalar type the node named.
    @Test
    void bindsTypedValuesOfConditions()
    {
        queryDocument(
            "queryTestFooDocument",
            "name created",
            Map.of(
                "pageSize", 0,
                "offset", 0,
                "condition", comparison("lt", "created", "Timestamp", "2019-01-01T00:00:00.000Z")
            )
        );

        final Object bound = database.statements().get(0).bindings().get(0);

        assertThat(bound, instanceOf(Timestamp.class));
        assertThat(((Timestamp) bound).toInstant(), is(Instant.parse("2019-01-01T00:00:00Z")));
    }


    /// A component names which part of a filter form a condition came from. The database has no use for
    /// it -- the condition below it filters exactly as it would on its own -- but the config the document
    /// returns still carries it, because the client spreads that config over its next update() and its
    /// form finds its own part of the condition by that id.
    @Test
    void returnsTheComponentsOfAConditionToTheClient()
    {
        final Map<String, Object> condition = Map.of(
            "type", "Component",
            "id", "nameFilter",
            "condition", eq("name", "String", "Alpha")
        );

        final Map<String, Object> document = queryDocument(
            "queryTestFooDocument",
            "id name",
            Map.of("pageSize", 0, "offset", 0, "condition", condition)
        );

        assertThat(database.sql().get(0), containsString("where \"test_foo\".\"name\" = ? order by"));
        assertThat(database.statements().get(0).bindings(), contains("Alpha"));

        assertThat(config(document).get("condition"), is(condition));
    }


    /// A to-many relation is fetched by a statement of its own, keyed by the parents already fetched, and
    /// stitched back onto the rows it belongs to -- a parent nothing points at gets an empty list rather
    /// than none.
    @Test
    void fetchesToManyRelations()
    {
        database
            .answer(
                Map.of("test_user.id", "user-1", "test_user.login", "admin"),
                Map.of("test_user.id", "user-2", "test_user.login", "anonymous"),
                Map.of("test_user.id", "user-3", "test_user.login", "nobody")
            )
            .answer(
                Map.of("test_foos.id", "foo-1", "test_foos.name", "Alpha", "test_foos.owner_id", "user-1"),
                Map.of("test_foos.id", "foo-2", "test_foos.name", "Bravo", "test_foos.owner_id", "user-2"),
                Map.of("test_foos.id", "foo-3", "test_foos.name", "Charlie", "test_foos.owner_id", "user-1")
            );

        final List<Map<String, Object>> rows = rows(
            queryDocument("queryTestUserDocument", "login testFoos { name }", config(0, 0))
        );

        assertThat(database.sql(), hasSize(2));
        assertThat(database.sql().get(1), containsString("where \"test_foos\".\"owner_id\" in (?, ?, ?)"));
        assertThat(database.statements().get(1).bindings(), contains("user-1", "user-2", "user-3"));

        assertThat(names(rows.get(0), "testFoos"), contains("Alpha", "Charlie"));
        assertThat(names(rows.get(1), "testFoos"), contains("Bravo"));
        assertThat(names(rows.get(2), "testFoos"), is(empty()));
    }


    /// Many-to-many is not a case of its own: the link table is a to-many relation and the far side is a
    /// to-one relation of that, so it is joined into the link table's statement and the result mirrors the
    /// selection right through it.
    @Test
    void fetchesManyToManyThroughItsLinkTable()
    {
        database
            .answer(
                Map.of("test_bar.id", "bar-1", "test_bar.name", "Bar #1"),
                Map.of("test_bar.id", "bar-4", "test_bar.name", "Bar #4")
            )
            .answer(
                link("link-1", "bar-1", "baz_links_baz", "baz-1", "Baz #1"),
                link("link-2", "bar-1", "baz_links_baz", "baz-2", "Baz #2")
            );

        final List<Map<String, Object>> bars = rows(
            queryDocument("queryTestBarDocument", "name bazLinks { id baz { name } }", config(0, 0))
        );

        assertThat(database.sql(), hasSize(2));
        assertThat(
            database.sql().get(1),
            containsString("left outer join \"public\".\"test_baz\" as \"baz_links_baz\"")
        );

        assertThat(linked(bars.get(0), "baz"), contains("Baz #1", "Baz #2"));
        assertThat(linked(bars.get(1), "baz"), is(empty()));
    }


    /// The same links from the other end, where the link table's other foreign key is the to-many relation
    /// and the first one is the to-one below it.
    @Test
    void readsTheSameLinksFromEitherSide()
    {
        database
            .answer(Map.of("test_baz.id", "baz-1", "test_baz.name", "Baz #1"))
            .answer(
                link("link-1", "baz-1", "baz_links_bar", "bar-1", "Bar #1"),
                link("link-4", "baz-1", "baz_links_bar", "bar-2", "Bar #2")
            );

        final List<Map<String, Object>> bazs = rows(
            queryDocument("queryTestBazDocument", "name bazLinks { id bar { name } }", config(0, 0))
        );

        assertThat(database.sql().get(1), containsString("where \"baz_links\".\"baz_id\" in (?)"));
        assertThat(linked(bazs.get(0), "bar"), contains("Bar #1", "Bar #2"));
    }


    /// A declared many-to-many lists the far side directly. Its statement goes through the link table, and a
    /// row is stitched onto the parent the link column names -- the far side carries nothing pointing back,
    /// and one Baz linked to two Bars comes back under both.
    @Test
    void fetchesManyToManyThroughItsField()
    {
        database
            .answer(
                Map.of("test_bar.id", "bar-1", "test_bar.name", "Bar #1"),
                Map.of("test_bar.id", "bar-2", "test_bar.name", "Bar #2"),
                Map.of("test_bar.id", "bar-3", "test_bar.name", "Bar #3")
            )
            .answer(
                through("bazs", "bar_id", "bar-1", "baz-1", "Baz #1"),
                through("bazs", "bar_id", "bar-2", "baz-1", "Baz #1"),
                through("bazs", "bar_id", "bar-1", "baz-2", "Baz #2")
            );

        final List<Map<String, Object>> bars = rows(
            queryDocument("queryTestBarDocument", "name bazs { name }", config(0, 0))
        );

        assertThat(database.sql(), hasSize(2));
        assertThat(
            database.sql().get(1),
            containsString(
                "from \"public\".\"test_baz\" as \"bazs\" join \"public\".\"test_bar_link\" as \"bazs_link\" " +
                    "on \"bazs_link\".\"baz_id\" = \"bazs\".\"id\""
            )
        );
        assertThat(database.sql().get(1), containsString("where \"bazs_link\".\"bar_id\" in (?, ?, ?)"));
        assertThat(database.statements().get(1).bindings(), contains("bar-1", "bar-2", "bar-3"));

        assertThat(names(bars.get(0), "bazs"), contains("Baz #1", "Baz #2"));
        assertThat(names(bars.get(1), "bazs"), contains("Baz #1"));
        assertThat(names(bars.get(2), "bazs"), is(empty()));
    }


    /// The other end of the same declaration, through the other link column.
    @Test
    void fetchesManyToManyFromTheOtherEnd()
    {
        database
            .answer(Map.of("test_baz.id", "baz-1", "test_baz.name", "Baz #1"))
            .answer(
                through("bars", "baz_id", "baz-1", "bar-1", "Bar #1"),
                through("bars", "baz_id", "baz-1", "bar-2", "Bar #2")
            );

        final List<Map<String, Object>> bazs = rows(
            queryDocument("queryTestBazDocument", "name bars { name }", config(0, 0))
        );

        assertThat(database.sql().get(1), containsString("where \"bars_link\".\"baz_id\" in (?)"));
        assertThat(names(bazs.get(0), "bars"), contains("Bar #1", "Bar #2"));
    }


    /// A filter path through a many-to-many asks whether some row on the far side matches, in an EXISTS that
    /// goes through the link table and is tied to the row by the link column.
    @Test
    void filtersThroughAManyToMany()
    {
        queryDocument(
            "queryTestBarDocument",
            "name bazs { name }",
            Map.of("pageSize", 0, "offset", 0, "condition", eq("bazs.name", "String", "Baz #1"))
        );

        assertThat(
            database.sql().get(0),
            containsString(
                "exists (select 1 from \"public\".\"test_baz\" as \"bazs\" join \"public\".\"test_bar_link\" as " +
                    "\"bazs_link\" on \"bazs_link\".\"baz_id\" = \"bazs\".\"id\" where (\"bazs_link\".\"bar_id\" = " +
                    "\"test_bar\".\"id\" and \"bazs\".\"name\" = ?))"
            )
        );
        assertThat(database.statements().get(0).bindings(), contains("Baz #1"));
    }


    // -----------------------------------------------------------------------------------------------------

    private static GraphQL graphQL(ScriptedDatabase database)
    {
        final DocumentTestLogic logic = new DocumentTestLogic();
        final QLiveDomain domain = TestDomainConfig.domainNoMeta(logic);
        final GraphQL graphQL = GraphQL.newGraphQL(domain.getGraphQLSchema()).build();

        logic.setQueryDocumentService(new DefaultQueryDocumentService(domain, database.dslContext(), graphQL));

        return graphQL;
    }


    @SuppressWarnings("unchecked")
    private Map<String, Object> queryDocument(String query, String rowFields, Map<String, Object> config)
    {
        final ExecutionResult result = graphQL.execute(
            ExecutionInput.newExecutionInput(
                    "query Q($config: QueryConfig!) { document: " + query +
                        "(config: $config) { type config rowCount rows { " + rowFields + " } } }"
                )
                .variables(Map.of("config", config))
                .build()
        );

        assertThat(result.getErrors().toString(), result.getErrors(), is(empty()));

        final Map<String, Object> data = result.getData();
        return (Map<String, Object>) data.get("document");
    }


    private static Map<String, Object> config(int pageSize, int offset)
    {
        return Map.of("pageSize", pageSize, "offset", offset);
    }


    @SuppressWarnings("unchecked")
    private static Map<String, Object> config(Map<String, Object> document)
    {
        return (Map<String, Object>) document.get("config");
    }


    private static Map<String, Object> fooRow(String id, String name)
    {
        return Map.of("test_foo.id", id, "test_foo.name", name);
    }


    /// One row of a link table's statement: the link, the key it hangs off and the far side joined to it.
    private static Map<String, Object> link(String id, String parentId, String farSide, String farId, String farName)
    {
        final String parentKey = farSide.endsWith("_baz") ? "bar_id" : "baz_id";

        return Map.of(
            "baz_links.id", id,
            "baz_links." + parentKey, parentId,
            farSide + ".id", farId,
            farSide + ".name", farName
        );
    }


    /// One row of a many-to-many's statement: the far side and the link column naming the parent.
    private static Map<String, Object> through(
        String field,
        String linkColumn,
        String parentId,
        String farId,
        String farName
    )
    {
        return Map.of(
            field + ".id", farId,
            field + ".name", farName,
            field + "_link." + linkColumn, parentId
        );
    }


    private static Map<String, Object> eq(String field, String scalarType, Object value)
    {
        return comparison("eq", field, scalarType, value);
    }


    /// One FilterDSL comparison as it arrives over the wire: the condition scalar's own JSON shape.
    private static Map<String, Object> comparison(String name, String field, String scalarType, Object value)
    {
        return Map.of(
            "type", "Condition",
            "name", name,
            "operands", List.of(
                Map.of("type", "Field", "name", field),
                Map.of("type", "Value", "scalarType", scalarType, "value", value)
            )
        );
    }


    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> document)
    {
        return (List<Map<String, Object>>) document.get("rows");
    }


    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> row, String name)
    {
        return (Map<String, Object>) row.get(name);
    }


    @SuppressWarnings("unchecked")
    private static List<Object> names(Map<String, Object> row, String relation)
    {
        return ((List<Map<String, Object>>) row.get(relation)).stream().map(r -> r.get("name")).toList();
    }


    /// The names on the far side of a link table, in the order the links came back.
    @SuppressWarnings("unchecked")
    private static List<Object> linked(Map<String, Object> row, String farSide)
    {
        return ((List<Map<String, Object>>) row.get("bazLinks")).stream()
            .map(link -> nested(link, farSide).get("name"))
            .toList();
    }
}
