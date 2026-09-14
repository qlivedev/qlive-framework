package com.dataciders.qlivetest.runtime;

import de.quinscape.domainql.scalar.TimestampScalar;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/// Runs query documents the way a browser does -- through the schema, against this application's own
/// database -- because everything this service does only means something at the far end of that.
///
/// The rows are the example database's own. Its many-to-many data is laid out for exactly what is asserted
/// here: one bar with three links, one with none, and a baz that two bars link to.
@SpringBootTest
class QueryDocumentServiceTest
{
    @Autowired
    private GraphQL graphQL;


    /// A to-one relation comes back with the rows that carry it, out of the one statement that fetched
    /// them, and not out of a query per row afterwards.
    @Test
    void fetchesRowsWithTheirToOneRelations()
    {
        final Map<String, Object> document = queryDocument(
            "queryFooDocument",
            "id name type owner { id login } fooType { name ordinal }",
            config(2, 0)
        );

        assertThat(document.get("type"), is("Foo"));

        final List<Map<String, Object>> rows = rows(document);
        assertThat(rows, hasSize(2));

        for (Map<String, Object> row : rows)
        {
            assertThat(nested(row, "owner").get("login"), is("admin"));
            assertThat(nested(row, "fooType").get("name"), is(row.get("type")));
        }
    }


    /// The row count is of everything the condition matches, which is the number the client pages by, not
    /// the number of rows it just received.
    @Test
    void countsWhatThePageLeftOut()
    {
        final Map<String, Object> document = queryDocument("queryFooDocument", "id name", config(2, 0));

        assertThat(rows(document), hasSize(2));
        assertThat((Integer) document.get("rowCount"), is(greaterThanOrEqualTo(7)));
    }


    /// With no page size the document is everything, and the config that comes back says what was applied
    /// -- including the sort nobody asked for.
    @Test
    void returnsTheConfigItActuallyUsed()
    {
        final Map<String, Object> document = queryDocument("queryFooDocument", "id name", config(0, 0));

        @SuppressWarnings("unchecked")
        final Map<String, Object> config = (Map<String, Object>) document.get("config");

        assertThat(config.get("pageSize"), is(0));
        assertThat((List<?>) config.get("sortFields"), contains("id"));
        assertThat(rows(document), hasSize((Integer) document.get("rowCount")));
    }


    @Test
    void filtersAndSortsAsTheConfigSaid()
    {
        final Map<String, Object> byName = queryDocument(
            "queryFooDocument",
            "id name",
            Map.of(
                "pageSize", 0,
                "offset", 0,
                "condition", eq("name", "String", "Foo #1")
            )
        );

        assertThat(rows(byName).stream().map(row -> row.get("name")).toList(), contains("Foo #1"));

        // through a relation: the condition reads a column of the joined table
        final Map<String, Object> byOwner = queryDocument(
            "queryFooDocument",
            "id name owner { login }",
            Map.of(
                "pageSize", 0,
                "offset", 0,
                "condition", eq("owner.login", "String", "admin")
            )
        );
        assertThat(rows(byOwner).size(), is(greaterThanOrEqualTo(7)));

        final Map<String, Object> sorted = queryDocument(
            "queryFooDocument",
            "id name",
            Map.of("pageSize", 1, "offset", 0, "sortFields", List.of("!name"))
        );
        assertThat(rows(sorted).get(0).get("name"), is("Foo #8"));
    }


    /// A condition's values arrive as JSON, where a timestamp is a string. By the time one reaches a
    /// query it is a Timestamp, because the condition scalar converted it with the coercing of the scalar
    /// type the node named.
    @Test
    void readsTypedValuesInConditions()
    {
        final Map<String, Object> document = queryDocument(
            "queryFooDocument",
            "name created",
            Map.of(
                "pageSize", 0,
                "offset", 0,
                "sortFields", List.of("name"),
                "condition", comparison("lt", "created", "Timestamp", "2019-01-01T00:00:00.000Z")
            )
        );

        assertThat(
            rows(document).stream().map(row -> row.get("name")).toList(),
            contains("Foo #1", "Foo #22", "Foo #33", "Foo #4")
        );
    }


    /// A component names which part of a filter form a condition came from. The database has no use for
    /// it -- the condition below it filters exactly as it would on its own -- but the config the document
    /// returns still carries it, because the client spreads that config over its next update() and its
    /// form finds its own part of the condition by that id.
    @Test
    void returnsTheComponentsOfAConditionToTheClient()
    {
        final Map<String, Object> condition = component(
            "nameFilter",
            eq("name", "String", "Foo #1")
        );

        final Map<String, Object> document = queryDocument(
            "queryFooDocument",
            "id name",
            Map.of("pageSize", 0, "offset", 0, "condition", condition)
        );

        assertThat(rows(document).stream().map(row -> row.get("name")).toList(), contains("Foo #1"));

        @SuppressWarnings("unchecked")
        final Map<String, Object> config = (Map<String, Object>) document.get("config");

        assertThat(config.get("condition"), is(condition));
    }


    /// A to-many relation is fetched by a query of its own and stitched back onto the rows it belongs to.
    @Test
    void fetchesToManyRelations()
    {
        final Map<String, Object> document = queryDocument(
            "queryAppUserDocument",
            "id login foos { id name }",
            Map.of(
                "pageSize", 0,
                "offset", 0,
                "condition", eq("login", "String", "admin")
            )
        );

        final List<Map<String, Object>> rows = rows(document);
        assertThat(rows, hasSize(1));

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> foos = (List<Map<String, Object>>) rows.get(0).get("foos");
        assertThat(foos.size(), is(greaterThanOrEqualTo(7)));
    }


    /// The scalars, through a type that a handwritten class replaces: the query document materializes
    /// that class, its Currency annotation reaches the schema, and the field it adds -- which no column
    /// backs -- comes back alongside the ones that do.
    @Test
    void readsEveryScalarOfAHandWrittenType()
    {
        final Map<String, Object> document = queryDocument(
            "queryQuxDocument",
            "id name bool intValue doubleValue stringValue timestampValue dateValue longValue " +
                "currencyValue byteValue bigDecimalValue jsonbValue summary",
            Map.of("pageSize", 0, "offset", 0, "sortFields", List.of("name"))
        );

        final List<Map<String, Object>> rows = rows(document);
        assertThat(rows, hasSize(3));

        final Map<String, Object> full = rows.get(0);
        assertThat(full.get("name"), is("Qux #1"));
        assertThat(full.get("bool"), is(true));
        assertThat(full.get("intValue"), is(12));
        assertThat(full.get("doubleValue"), is(12.34));
        assertThat(full.get("stringValue"), is("abc"));
        // the column is a timestamp without time zone holding 19:58:59, which JDBC reads as that wall
        // clock in the server's zone -- so the instant that goes out is an offset away from it
        assertThat(
            full.get("timestampValue"),
            is(TimestampScalar.toISO8601(Timestamp.valueOf("2018-11-01 19:58:59")))
        );
        assertThat(full.get("dateValue"), is("2018-11-01"));
        assertThat(full.get("longValue"), is(12345678901L));
        assertThat(full.get("byteValue"), is((byte) 23));
        assertThat(full.get("jsonbValue"), is(notNullValue()));

        // the field the handwritten type adds, computed off the row rather than selected from it
        assertThat(full.get("summary"), is("Qux #1 / abc"));

        // and the row that is null throughout stays null throughout
        final Map<String, Object> nulls = rows.get(2);
        assertThat(nulls.get("name"), is("Qux #3 (nulls)"));
        assertThat(nulls.get("bool"), is(nullValue()));
        assertThat(nulls.get("jsonbValue"), is(nullValue()));
    }


    /// Many-to-many is not a case of its own: the link table is a to-many relation and the far side is a
    /// to-one relation of that, so the result mirrors the selection right through it.
    @Test
    void fetchesManyToManyThroughItsLinkTable()
    {
        final List<Map<String, Object>> bars = rows(
            queryDocument(
                "queryBarDocument",
                "name bazLinks { id baz { name } }",
                Map.of("pageSize", 0, "offset", 0, "sortFields", List.of("name"))
            )
        );

        assertThat(
            bars.stream().map(bar -> bar.get("name")).toList(),
            contains("Bar #1", "Bar #2", "Bar #3", "Bar #4")
        );
        assertThat(linked(bars.get(0), "baz"), contains("Baz #1", "Baz #2", "Baz #3"));
        assertThat(linked(bars.get(3), "baz"), is(empty()));
    }


    /// The same links from the other end, where the link table's other foreign key is the to-many relation
    /// and the first one is the to-one below it.
    @Test
    void readsTheSameLinksFromEitherSide()
    {
        final List<Map<String, Object>> bazs = rows(
            queryDocument(
                "queryBazDocument",
                "name bazLinks { id bar { name } }",
                Map.of(
                    "pageSize", 0,
                    "offset", 0,
                    "condition", eq("name", "String", "Baz #1")
                )
            )
        );

        assertThat(bazs, hasSize(1));
        assertThat(linked(bazs.get(0), "bar"), contains("Bar #1", "Bar #2"));
    }


    // -----------------------------------------------------------------------------------------------------

    private static Map<String, Object> config(int pageSize, int offset)
    {
        return Map.of("pageSize", pageSize, "offset", offset);
    }


    private static Map<String, Object> eq(String field, String scalarType, Object value)
    {
        return comparison("eq", field, scalarType, value);
    }


    /// A condition wrapped in the client's marker for the filter form field it came from.
    private static Map<String, Object> component(String id, Map<String, Object> condition)
    {
        return Map.of("type", "Component", "id", id, "condition", condition);
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


    /// The names on the far side of a link table, sorted, so that a link's own order does not decide
    /// whether the test passes.
    @SuppressWarnings("unchecked")
    private static List<String> linked(Map<String, Object> row, String farSide)
    {
        final List<Map<String, Object>> links = (List<Map<String, Object>>) row.get("bazLinks");

        return links.stream()
            .map(link -> (String) nested(link, farSide).get("name"))
            .sorted()
            .toList();
    }
}
