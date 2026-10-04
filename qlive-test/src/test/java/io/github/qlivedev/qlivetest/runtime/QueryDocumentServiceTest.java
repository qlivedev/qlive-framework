package io.github.qlivedev.qlivetest.runtime;

import io.github.qlivedev.graphql.scalar.TimestampScalar;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.qlivedev.qlivetest.domain.Tables.APP_USER;
import static io.github.qlivedev.qlivetest.domain.Tables.FOO;
import static io.github.qlivedev.qlivetest.domain.Tables.FOO_TYPE;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/// Runs query documents the way a browser does -- through the schema, against this application's own
/// database -- for what only the database can answer: whether the statements find the rows they describe,
/// and whether every column type reads back as its scalar.
///
/// What QLive does on either side of the database -- the statements a document comes to, what it binds,
/// how rows are stitched into relations, the config it echoes -- is qlive's QueryDocumentExecutionTest,
/// against scripted rows.
///
/// The Foo test makes its own rows and looks only at those: each carries a marker in its description, and
/// every condition it queries with requires it. What else the table holds -- the seed rows, rows saved in the
/// browser, a previous run's leftovers -- cannot change its outcome (see docs/design/test-isolation.md).
///
/// The Bar and Qux tests still read the example database's own rows. Its many-to-many data is laid out for
/// exactly what is asserted there: one bar with three links and one with none.
@SpringBootTest
class QueryDocumentServiceTest
{
    @Autowired
    private GraphQL graphQL;

    @Autowired
    private DSLContext dslContext;

    /// In the description of every row this test makes, and nowhere else.
    private final String marker = "QueryDocumentServiceTest " + UUID.randomUUID();

    /// The user who owns Alpha and Charlie, whoever that is.
    private Record2<String, String> ownerA;


    /// Alpha and Bravo from 2018, Charlie from 2020; Alpha and Charlie belong to one user, Bravo to another.
    @BeforeEach
    void makeRows()
    {
        final List<Record2<String, String>> users = dslContext.select(APP_USER.ID, APP_USER.LOGIN)
            .from(APP_USER)
            .orderBy(APP_USER.LOGIN)
            .limit(2)
            .fetch();
        ownerA = users.get(0);
        final Record2<String, String> ownerB = users.get(1);

        final String type = dslContext.select(FOO_TYPE.NAME).from(FOO_TYPE).limit(1).fetchOne(FOO_TYPE.NAME);

        insertFoo("Alpha", 1, ownerA, type, "2018-03-01 12:00:00");
        insertFoo("Bravo", 2, ownerB, type, "2018-09-01 12:00:00");
        insertFoo("Charlie", 3, ownerA, type, "2020-06-01 12:00:00");
    }


    @AfterEach
    void removeRows()
    {
        dslContext.deleteFrom(FOO).where(FOO.DESCRIPTION.eq(marker)).execute();
    }


    @Test
    void filtersAndSortsAsTheConfigSaid()
    {
        final Map<String, Object> byName = queryDocument(
            "queryFooDocument",
            "id name",
            Map.of("pageSize", 0, "offset", 0, "condition", marked(eq("name", "String", "Bravo")))
        );

        assertThat(names(byName), contains("Bravo"));

        // through a relation: the condition reads a column of the joined table
        final Map<String, Object> byOwner = queryDocument(
            "queryFooDocument",
            "id name owner { login }",
            Map.of(
                "pageSize", 0,
                "offset", 0,
                "sortFields", List.of("name"),
                "condition", marked(eq("owner.login", "String", ownerA.value2()))
            )
        );
        assertThat(names(byOwner), contains("Alpha", "Charlie"));

        final Map<String, Object> sorted = queryDocument(
            "queryFooDocument",
            "id name",
            Map.of("pageSize", 1, "offset", 0, "sortFields", List.of("!name"), "condition", marked())
        );
        assertThat(names(sorted), contains("Charlie"));
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


    // -----------------------------------------------------------------------------------------------------

    private void insertFoo(String name, int num, Record2<String, String> owner, String type, String created)
    {
        dslContext.insertInto(FOO)
            .set(FOO.ID, UUID.randomUUID().toString())
            .set(FOO.NAME, name)
            .set(FOO.NUM, num)
            .set(FOO.FLAG, true)
            .set(FOO.TYPE, type)
            .set(FOO.OWNER_ID, owner.value1())
            .set(FOO.CREATED, Timestamp.valueOf(created))
            .set(FOO.DESCRIPTION, marker)
            .set(FOO.VERSION, UUID.randomUUID().toString())
            .execute();
    }


    /// The condition that finds this test's rows and no others.
    private Map<String, Object> marked()
    {
        return eq("description", "String", marker);
    }


    /// The given condition, limited to this test's rows.
    private Map<String, Object> marked(Map<String, Object> condition)
    {
        return Map.of("type", "Condition", "name", "and", "operands", List.of(marked(), condition));
    }


    private static List<Object> names(Map<String, Object> document)
    {
        return rows(document).stream().map(row -> row.get("name")).toList();
    }


    /// One FilterDSL comparison as it arrives over the wire: the condition scalar's own JSON shape.
    private static Map<String, Object> eq(String field, String scalarType, Object value)
    {
        return Map.of(
            "type", "Condition",
            "name", "eq",
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
