package io.github.qlivedev.runtime.merge;

import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.generic.GenericScalar;
import io.github.qlivedev.model.merge.EntityChange;
import io.github.qlivedev.model.merge.EntityDeletion;
import io.github.qlivedev.model.merge.LinkChange;
import io.github.qlivedev.model.merge.MergeConfig;
import io.github.qlivedev.model.merge.MergeConflict;
import io.github.qlivedev.model.merge.MergeConflictField;
import io.github.qlivedev.model.merge.MergeResult;
import io.github.qlivedev.model.merge.MergeStatus;
import io.github.qlivedev.runtime.domain.TestDomainConfig;
import io.github.qlivedev.runtime.domain.TestLogic;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.jooq.Table;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/// The merge the way a browser sends it: one mutation, over the schema, with the values as GenericScalars.
///
/// What this proves is that there is nothing between the wire and the service -- no `TestFooInput`, no
/// mutation per operation, and a value that arrives as `{ type: "Int", value: 7 }` reaches the service as an
/// Integer. The service is a stand-in that keeps what it was handed and answers what it is told to; what a
/// real merge makes of a working set is qlive-test's MergeServiceTest, against a real database.
class MergeLogicTest
{
    private final static String MUTATION =
        "mutation M($changes: [EntityChangeInput]!, $links: [LinkChangeInput]!, " +
            "$deletions: [EntityDeletionInput]!, $mergeConfig: MergeConfigInput!) {" +
            "  result: mergeWorkingSet(changes: $changes, links: $links, deletions: $deletions, " +
            "    mergeConfig: $mergeConfig) {" +
            "    status conflicts { type id storedVersion deleted fields { field mine stored informational } }" +
            "  }" +
            "}";

    private final Recording mergeService = new Recording();

    private final GraphQL graphQL = graphQL(mergeService);


    /// A new row and a deletion, and no application type in either direction. Every value reaches the
    /// service as the Java type its scalar names, the timestamp included, which arrived as a string.
    @Test
    void handsTheServiceTheWorkingSetWithTypedValues()
    {
        final Map<String, Object> change = change(
            "TestFoo",
            "foo-1",
            null,
            field("name", "String", "Mutation #1"),
            field("num", "Int", 11),
            field("created", "Timestamp", "2026-09-10T12:00:00.000Z")
        );
        change.put("new", true);

        mergeWorkingSet(
            List.of(change),
            List.of(Map.of("type", "TestFoo", "id", "foo-2", "version", "v-2")),
            Map.of("conflictValues", true)
        );

        final EntityChange received = mergeService.changes.get(0);
        assertThat(received.getType(), is("TestFoo"));
        assertThat(received.getId(), is("foo-1"));
        assertThat(received.getVersion(), is(nullValue()));
        assertThat(received.isNew(), is(true));

        assertThat(value(received, "name"), is("Mutation #1"));
        assertThat(value(received, "num"), is(11));
        assertThat(value(received, "created"), instanceOf(Timestamp.class));
        assertThat(
            ((Timestamp) value(received, "created")).toInstant().toString(),
            is("2026-09-10T12:00:00Z")
        );

        final EntityDeletion deletion = mergeService.deletions.get(0);
        assertThat(deletion.getType(), is("TestFoo"));
        assertThat(deletion.getId(), is("foo-2"));
        assertThat(deletion.getVersion(), is("v-2"));

        assertThat(mergeService.config.isConflictValues(), is(true));
    }


    /// The conflict as the client sees it, which is the shape the form the user was editing renders from:
    /// the version to try again against, and both values of every field that clashed -- each as the
    /// GenericScalar it went out as, the timestamp back to the string it arrived as.
    @Test
    void handsBackTheConflictAsData()
    {
        mergeService.answer = conflict(
            conflictField("name", scalar("String", "typed by me"), scalar("String", "saved by somebody else")),
            conflictField(
                "created",
                null,
                scalar("Timestamp", Timestamp.from(java.time.Instant.parse("2026-09-10T12:00:00Z")))
            )
        );

        final Map<String, Object> result = mergeWorkingSet(
            List.of(change("TestFoo", "foo-1", "v-1", field("name", "String", "typed by me"))),
            List.of(),
            Map.of("conflictValues", true)
        );

        assertThat(result.get("status"), is("CONFLICT"));

        final Map<String, Object> conflict = first(result.get("conflicts"));
        assertThat(conflict.get("type"), is("TestFoo"));
        assertThat(conflict.get("id"), is("foo-1"));
        assertThat(conflict.get("deleted"), is(false));
        assertThat(conflict.get("storedVersion"), is("v-2"));

        final List<Map<String, Object>> fields = list(conflict.get("fields"));

        assertThat(fields.get(0).get("field"), is("name"));
        assertThat(fields.get(0).get("mine"), is(Map.of("type", "String", "value", "typed by me")));
        assertThat(fields.get(0).get("stored"), is(Map.of("type", "String", "value", "saved by somebody else")));
        assertThat(fields.get(0).get("informational"), is(false));

        // a field nobody here has an opinion about carries no value of ours, and says so with a null
        assertThat(fields.get(1).get("field"), is("created"));
        assertThat(fields.get(1).get("mine"), is(nullValue()));
        assertThat(
            fields.get(1).get("stored"),
            is(Map.of("type", "Timestamp", "value", "2026-09-10T12:00:00.000Z"))
        );
    }


    /// A merge that landed has nothing to report but that it did.
    @Test
    void reportsAMergeThatLanded()
    {
        final Map<String, Object> result = mergeWorkingSet(
            List.of(change("TestFoo", "foo-1", "v-1", field("num", "Int", 12))),
            List.of(),
            Map.of("conflictValues", false)
        );

        assertThat(result.get("status"), is("DONE"));
        assertThat(list(result.get("conflicts")), is(empty()));
        assertThat(mergeService.config.isConflictValues(), is(false));
        assertThat(mergeService.changes.stream().map(EntityChange::getId).toList(), contains("foo-1"));
    }


    /// Associations travel as the row, the many-to-many field and the ids on the other end -- no link row, no
    /// link id and no version.
    @Test
    void handsTheServiceTheAssociations()
    {
        mergeWorkingSet(
            List.of(),
            List.of(
                Map.of(
                    "type", "TestBar",
                    "id", "bar-1",
                    "field", "bazes",
                    "added", List.of("baz-2"),
                    "removed", List.of("baz-1")
                )
            ),
            List.of(),
            Map.of("conflictValues", false)
        );

        final LinkChange received = mergeService.links.get(0);
        assertThat(received.getType(), is("TestBar"));
        assertThat(received.getId(), is("bar-1"));
        assertThat(received.getField(), is("bazes"));
        assertThat(received.getAdded(), is(List.of("baz-2")));
        assertThat(received.getRemoved(), is(List.of("baz-1")));
    }


    // -----------------------------------------------------------------------------------------------------

    private static GraphQL graphQL(MergeService mergeService)
    {
        // TestLogic for the query root a schema cannot do without
        final QLiveDomain domain = TestDomainConfig.domain(new TestLogic(), new MergeLogic(mergeService));

        return GraphQL.newGraphQL(domain.getGraphQLSchema()).build();
    }


    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeWorkingSet(
        List<Map<String, Object>> changes,
        List<Map<String, Object>> deletions,
        Map<String, Object> mergeConfig
    )
    {
        return mergeWorkingSet(changes, List.of(), deletions, mergeConfig);
    }


    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeWorkingSet(
        List<Map<String, Object>> changes,
        List<Map<String, Object>> links,
        List<Map<String, Object>> deletions,
        Map<String, Object> mergeConfig
    )
    {
        final ExecutionResult result = graphQL.execute(
            ExecutionInput.newExecutionInput(MUTATION)
                .variables(
                    Map.of("changes", changes, "links", links, "deletions", deletions, "mergeConfig", mergeConfig)
                )
                .build()
        );

        assertThat(result.getErrors().toString(), result.getErrors(), is(empty()));

        final Map<String, Object> data = result.getData();

        return (Map<String, Object>) data.get("result");
    }


    /// One change as JSON, which is exactly what a working set will post.
    @SafeVarargs
    private static Map<String, Object> change(String type, String id, String version, Map<String, Object>... fields)
    {
        // a LinkedHashMap rather than Map.of, because a null version is a value the mutation has to accept
        final Map<String, Object> change = new LinkedHashMap<>();
        change.put("type", type);
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


    private static Object value(EntityChange change, String field)
    {
        return change.getChanges().stream()
            .filter(c -> c.getField().equals(field))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No change of '" + field + "'"))
            .getValue()
            .getValue();
    }


    private static GenericScalar scalar(String type, Object value)
    {
        return new GenericScalar(type, value);
    }


    private static MergeConflictField conflictField(String name, GenericScalar mine, GenericScalar stored)
    {
        final MergeConflictField field = new MergeConflictField();
        field.setField(name);
        field.setMine(mine);
        field.setStored(stored);
        field.setInformational(mine == null);

        return field;
    }


    private static MergeResult conflict(MergeConflictField... fields)
    {
        final MergeConflict conflict = new MergeConflict();
        conflict.setType("TestFoo");
        conflict.setId("foo-1");
        conflict.setStoredVersion("v-2");
        conflict.setFields(List.of(fields));

        final MergeResult result = new MergeResult();
        result.setStatus(MergeStatus.CONFLICT);
        result.setConflicts(List.of(conflict));

        return result;
    }


    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value)
    {
        return (List<Map<String, Object>>) value;
    }


    private static Map<String, Object> first(Object value)
    {
        return list(value).get(0);
    }


    /// Keeps what the mutation handed it and answers with whatever it was given, a clean merge unless told
    /// otherwise.
    private static final class Recording
        implements MergeService
    {
        private List<EntityChange> changes;

        private List<LinkChange> links;

        private List<EntityDeletion> deletions;

        private MergeConfig config;

        private MergeResult answer = done();


        @Override
        public MergeResult merge(
            List<EntityChange> changes,
            List<LinkChange> links,
            List<EntityDeletion> deletions,
            MergeConfig config
        )
        {
            this.changes = changes;
            this.links = links;
            this.deletions = deletions;
            this.config = config;

            return answer;
        }


        @Override
        public void ensureNotVersioned(Table<?> table)
        {
            throw new UnsupportedOperationException();
        }


        private static MergeResult done()
        {
            final MergeResult result = new MergeResult();
            result.setStatus(MergeStatus.DONE);
            result.setConflicts(List.of());

            return result;
        }
    }
}
