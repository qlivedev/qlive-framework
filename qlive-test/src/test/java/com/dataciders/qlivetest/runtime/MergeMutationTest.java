package com.dataciders.qlivetest.runtime;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.dataciders.qlivetest.domain.Tables.BAR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/// Runs the merge the way a browser does: one mutation, over the schema, with the values as GenericScalars.
///
/// What this proves and the service test cannot is that there is nothing between the two -- no `BarInput`,
/// no mutation per operation, and a value that arrives as `{ type: "Int", value: 7 }` and reaches the column
/// as an int.
@SpringBootTest
class MergeMutationTest
{
    private final static String MUTATION =
        "mutation M($changes: [EntityChangeInput]!, $deletions: [EntityDeletionInput]!, " +
            "$mergeConfig: MergeConfigInput!) {" +
            "  result: mergeWorkingSet(changes: $changes, deletions: $deletions, mergeConfig: $mergeConfig) {" +
            "    status conflicts { type id version deleted fields { field mine stored } }" +
            "  }" +
            "}";

    @Autowired
    private GraphQL graphQL;

    @Autowired
    private DSLContext dslContext;

    private final List<String> bars = new ArrayList<>();


    @AfterEach
    void removeWhatWasMade()
    {
        dslContext.deleteFrom(BAR).where(BAR.ID.in(bars)).execute();
    }


    /// A new row and a change to it, and no application type in either direction.
    @Test
    void storesAWorkingSetThroughOneMutation()
    {
        final String id = UUID.randomUUID().toString();
        bars.add(id);

        assertThat(
            mergeWorkingSet(
                List.of(
                    newBar(id, "Mutation #1", 11)
                )
            ).get("status"),
            is("DONE")
        );

        final Record stored = bar(id);
        assertThat(stored.get(BAR.NAME), is("Mutation #1"));
        assertThat(stored.get(BAR.NUM), is(11));
        assertThat(stored.get(BAR.VERSION), is(notNullValue()));

        assertThat(
            mergeWorkingSet(
                List.of(
                    change(id, stored.get(BAR.VERSION), field("num", "Int", 12))
                )
            ).get("status"),
            is("DONE")
        );

        assertThat(bar(id).get(BAR.NUM), is(12));
    }


    /// The conflict as the client sees it, which is the shape the form the user was editing renders from:
    /// the version to try again against, and both values of every field that clashed.
    @Test
    void handsBackTheConflictAsData()
    {
        final String id = UUID.randomUUID().toString();
        bars.add(id);

        mergeWorkingSet(List.of(newBar(id, "Mutation #2", 21)));

        final String base = bar(id).get(BAR.VERSION);
        mergeWorkingSet(List.of(change(id, base, field("name", "String", "saved by somebody else"))));

        final Map<String, Object> result = mergeWorkingSet(
            List.of(change(id, base, field("name", "String", "typed by me"))),
            List.of(),
            Map.of("resolveConflicts", true)
        );

        assertThat(result.get("status"), is("CONFLICT"));

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> conflicts = (List<Map<String, Object>>) result.get("conflicts");
        assertThat(conflicts, contains(notNullValue()));

        final Map<String, Object> conflict = conflicts.get(0);
        assertThat(conflict.get("type"), is("Bar"));
        assertThat(conflict.get("id"), is(id));
        assertThat(conflict.get("deleted"), is(false));
        assertThat(conflict.get("version"), is(bar(id).get(BAR.VERSION)));

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> fields = (List<Map<String, Object>>) conflict.get("fields");
        assertThat(fields, contains(notNullValue()));

        final Map<String, Object> field = fields.get(0);
        assertThat(field.get("field"), is("name"));
        assertThat(genericScalar(field, "mine"), is(Map.of("type", "String", "value", "typed by me")));
        assertThat(
            genericScalar(field, "stored"),
            is(Map.of("type", "String", "value", "saved by somebody else"))
        );

        assertThat(bar(id).get(BAR.NAME), is("saved by somebody else"));
    }


    /// A caller that says nothing about resolving gets the fields and no values, which is the default a
    /// service calling this has no reason to change.
    @Test
    void leavesTheValuesOutWhereNobodyAskedForThem()
    {
        final String id = UUID.randomUUID().toString();
        bars.add(id);

        mergeWorkingSet(List.of(newBar(id, "Mutation #3", 31)));

        final String base = bar(id).get(BAR.VERSION);
        mergeWorkingSet(List.of(change(id, base, field("num", "Int", 32))));

        final Map<String, Object> result = mergeWorkingSet(
            List.of(change(id, base, field("num", "Int", 33)))
        );

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> conflicts = (List<Map<String, Object>>) result.get("conflicts");

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> fields = (List<Map<String, Object>>) conflicts.get(0).get("fields");

        assertThat(fields.get(0).get("field"), is("num"));
        assertThat(fields.get(0).get("mine"), is(nullValue()));
        assertThat(fields.get(0).get("stored"), is(nullValue()));
    }


    // -----------------------------------------------------------------------------------------------------

    private Map<String, Object> mergeWorkingSet(List<Map<String, Object>> changes)
    {
        return mergeWorkingSet(changes, List.of(), Map.of("resolveConflicts", false));
    }


    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeWorkingSet(
        List<Map<String, Object>> changes,
        List<Map<String, Object>> deletions,
        Map<String, Object> mergeConfig
    )
    {
        final ExecutionResult result = graphQL.execute(
            ExecutionInput.newExecutionInput(MUTATION)
                .variables(
                    Map.of("changes", changes, "deletions", deletions, "mergeConfig", mergeConfig)
                )
                .build()
        );

        assertThat(result.getErrors().toString(), result.getErrors(), is(empty()));

        final Map<String, Object> data = result.getData();

        return (Map<String, Object>) data.get("result");
    }


    private static Map<String, Object> newBar(String id, String name, int num)
    {
        final Map<String, Object> change = change(
            id,
            null,
            field("name", "String", name),
            field("num", "Int", num),
            field("created", "Timestamp", "2026-09-10T12:00:00.000Z")
        );
        change.put("new", true);

        return change;
    }


    /// One change as JSON, which is exactly what a working set will post.
    private static Map<String, Object> change(String id, String version, Map<String, Object>... fields)
    {
        // a LinkedHashMap rather than Map.of, because a null version is a value the mutation has to accept
        final Map<String, Object> change = new LinkedHashMap<>();
        change.put("type", "Bar");
        change.put("id", id);
        change.put("version", version);
        change.put("new", false);
        change.put("changes", List.of(fields));

        return change;
    }


    private static Map<String, Object> field(String name, String scalarType, Object value)
    {
        return Map.of("field", name, "value", Map.of("type", scalarType, "value", value));
    }


    @SuppressWarnings("unchecked")
    private static Map<String, Object> genericScalar(Map<String, Object> field, String name)
    {
        return (Map<String, Object>) field.get(name);
    }


    private Record bar(String id)
    {
        return dslContext.selectFrom(BAR).where(BAR.ID.eq(id)).fetchOne();
    }
}
