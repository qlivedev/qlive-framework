package io.github.qlivedev.qlivetest.runtime;

import io.github.qlivedev.qlive.api.generic.GenericScalar;
import io.github.qlivedev.qlive.model.merge.EntityChange;
import io.github.qlivedev.qlive.model.merge.FieldChange;
import io.github.qlivedev.qlive.model.merge.MergeConfig;
import io.github.qlivedev.qlive.model.merge.MergeStatus;
import io.github.qlivedev.qlive.runtime.merge.MergeService;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.schema.GraphQLFieldDefinition;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.qlivedev.qlivetest.domain.Tables.APP_VERSION;
import static io.github.qlivedev.qlivetest.domain.Tables.BAR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/// Issues the example actions of `BarLogic` as the mutations they are, against bars the test makes: the rows are
/// loaded and their versions checked, the rules evaluated, and a refusal comes back as errors and no data.
@SpringBootTest
class ActionTest
{
    private final static String APPROVE_BAR =
        "mutation approve($bar: SubjectRefInput!, $comment: String!, $notify: Boolean!) {" +
            "  approveBar(bar: $bar, comment: $comment, notify: $notify) { barId name comment notified }" +
            "}";

    private final static String APPROVE_BARS =
        "mutation approve($bars: [SubjectRefInput!]!, $comment: String!) {" +
            "  approveBars(bars: $bars, comment: $comment) { barId name }" +
            "}";

    private final static String QUOTE_BAR =
        "mutation quote($draft: BarInput!) { quoteBar(draft: $draft) }";

    @Autowired
    private GraphQL graphQL;

    @Autowired
    private MergeService mergeService;

    @Autowired
    private DSLContext dslContext;

    /// Ids of the bars this test made, for the cleanup that has to run either way.
    private final List<String> bars = new ArrayList<>();


    @AfterEach
    void removeWhatWasMade()
    {
        dslContext.deleteFrom(BAR).where(BAR.ID.in(bars)).execute();
        dslContext.deleteFrom(APP_VERSION).where(APP_VERSION.ENTITY_ID.in(bars)).execute();
    }


    /// The schema says what a request carries: a subject is a reference, required input is non-null, and a
    /// primitive is too. The descriptions are the method's doc comment and its `@param` lines.
    @Test
    void declaresTheActionAsAMutation()
    {
        final GraphQLFieldDefinition approveBar = graphQL.getGraphQLSchema().getMutationType()
            .getFieldDefinition("approveBar");

        assertThat(approveBar.getArgument("bar").getType().toString(), is("SubjectRefInput!"));
        assertThat(approveBar.getArgument("comment").getType().toString(), is("String!"));
        assertThat(approveBar.getArgument("notify").getType().toString(), is("Boolean!"));
        assertThat(approveBar.getType().toString(), is("ApproveResult!"));
        assertThat(approveBar.getDescription().startsWith("Approves a bar."), is(true));
        // what tells an action from a plain mutation in a schema file
        assertThat(approveBar.getDescription().endsWith("\n\n@action"), is(true));
        assertThat(
            graphQL.getGraphQLSchema().getMutationType().getFieldDefinition("mergeWorkingSet").getDescription(),
            is(nullValue())
        );
        assertThat(approveBar.getArgument("bar").getDescription(), is("the bar to approve"));

        assertThat(
            graphQL.getGraphQLSchema().getMutationType().getFieldDefinition("approveBars")
                .getArgument("bars").getType().toString(),
            is("[SubjectRefInput!]!")
        );
    }


    /// The ordinary path: the rules hold, the method gets the row as it is stored, and its write lands in the
    /// action's transaction under a new version.
    @Test
    void approvesABarWhoseRulesHold()
    {
        final String id = newBar("Action #1", "described");
        final String seen = version(id);

        final ExecutionResult result = execute(APPROVE_BAR, Map.of(
            "bar", ref(id, seen),
            "comment", "fine",
            "notify", true
        ));

        assertThat(result.getErrors(), is(empty()));
        assertThat(
            data(result, "approveBar"),
            is(Map.of("barId", id, "name", "Action #1", "comment", "fine", "notified", true))
        );
        assertThat(approved(id), is(true));
        assertThat(version(id).equals(seen), is(false));
    }


    /// A rule over the subject's stored field, evaluated against the database. A null description is no proof
    /// the rule holds, and neither is an empty one.
    @Test
    void refusesABarWithoutDescription()
    {
        final String id = newBar("Action #2", null);

        final ExecutionResult result = execute(APPROVE_BAR, Map.of(
            "bar", ref(id, version(id)),
            "comment", "fine",
            "notify", false
        ));

        assertThat(result.getData(), is(nullValue()));
        assertThat(result.getErrors(), hasSize(1));
        assertThat(
            extensions(result.getErrors().get(0)),
            is(refusal("RULE", "Bar.descriptionRequired", "bar.description"))
        );

        // what the client and the endpoint tell a refusal from a failure by
        final Map<String, Object> specification = result.getErrors().get(0).toSpecification();
        assertThat(((Map<?, ?>) specification.get("extensions")).get("classification"), is("REFUSED"));
    }


    /// A rule over input alone, evaluated in memory, broken together with a stored one: every broken rule is one
    /// error.
    @Test
    void reportsEveryBrokenRule()
    {
        final String id = newBar("Action #3", "");

        final ExecutionResult result = execute(APPROVE_BAR, Map.of(
            "bar", ref(id, version(id)),
            "comment", "",
            "notify", true
        ));

        assertThat(result.getData(), is(nullValue()));
        assertThat(
            result.getErrors().stream().map(ActionTest::extensions).toList(),
            contains(
                refusal("RULE", "Bar.descriptionRequired", "bar.description"),
                refusal("RULE", "Bar.commentWhenNotifying", "comment")
            )
        );
    }


    /// The action decides on the state the user saw: a row whose version moved since is a conflict, and the rules
    /// aren't asked.
    @Test
    void refusesARowThatChanged()
    {
        final String id = newBar("Action #4", "described");
        final String seen = version(id);

        changeName(id, seen, "Action #4 renamed");

        final ExecutionResult result = execute(APPROVE_BAR, Map.of(
            "bar", ref(id, seen),
            "comment", "fine",
            "notify", false
        ));

        assertThat(result.getData(), is(nullValue()));
        assertThat(
            result.getErrors().stream().map(ActionTest::extensions).toList(),
            contains(refusal("CONFLICT", "Action.subjectChanged", "bar"))
        );
    }


    @Test
    void refusesARowThatIsGone()
    {
        final ExecutionResult result = execute(APPROVE_BAR, Map.of(
            "bar", ref(UUID.randomUUID().toString(), "no-such-version"),
            "comment", "fine",
            "notify", false
        ));

        assertThat(
            result.getErrors().stream().map(ActionTest::extensions).toList(),
            contains(refusal("CONFLICT", "Action.subjectGone", "bar"))
        );
    }


    /// A versioned row named without its version is a broken request, not a refusal.
    @Test
    void failsARowWithoutVersion()
    {
        final String id = newBar("Action #5", "described");

        final Map<String, Object> ref = new HashMap<>();
        ref.put("id", id);

        final ExecutionResult result = execute(APPROVE_BAR, Map.of(
            "bar", ref,
            "comment", "fine",
            "notify", false
        ));

        assertThat(result.getErrors(), hasSize(1));
        assertThat(result.getErrors().get(0).getMessage().contains("without a version"), is(true));
    }


    /// A list subject: the rows come in the order sent, and a refusal blames each failing row by its position.
    @Test
    void approvesAllOrNone()
    {
        final String first = newBar("Action #6", "described");
        final String second = newBar("Action #7", null);
        final String third = newBar("Action #8", "described");

        final ExecutionResult refused = execute(APPROVE_BARS, Map.of(
            "bars", List.of(ref(first, version(first)), ref(second, version(second)), ref(third, version(third))),
            "comment", "fine"
        ));

        assertThat(refused.getData(), is(nullValue()));
        assertThat(
            refused.getErrors().stream().map(ActionTest::extensions).toList(),
            contains(refusal("RULE", "Bar.descriptionRequired", "bars.1.description"))
        );
        assertThat(approved(first) || approved(second) || approved(third), is(false));

        final ExecutionResult approved = execute(APPROVE_BARS, Map.of(
            "bars", List.of(ref(third, version(third)), ref(first, version(first))),
            "comment", "fine"
        ));

        assertThat(approved.getErrors(), is(empty()));
        assertThat(
            data(approved, "approveBars"),
            is(List.of(Map.of("barId", third, "name", "Action #8"), Map.of("barId", first, "name", "Action #6")))
        );
        assertThat(approved(first) && approved(third) && !approved(second), is(true));
    }


    /// A parameter of a table-backed type marked `@Input` is input: it takes the row's values, saved or not, and its
    /// rule reads them in memory.
    @Test
    void takesAnUnsavedRowAsInput()
    {
        assertThat(
            graphQL.getGraphQLSchema().getMutationType().getFieldDefinition("quoteBar")
                .getArgument("draft").getType().toString(),
            is("BarInput!")
        );

        final String id = UUID.randomUUID().toString();
        final ExecutionResult quoted = execute(QUOTE_BAR, Map.of(
            "draft", Map.of("id", id, "name", "Quote #1", "num", 3, "created", "2026-10-23T12:00:00.000Z")
        ));

        assertThat(quoted.getErrors(), is(empty()));
        assertThat(data(quoted, "quoteBar"), is(300));
        assertThat(version(id), is(nullValue()));

        final ExecutionResult refused = execute(QUOTE_BAR, Map.of(
            "draft", Map.of("id", id, "name", "Quote #1", "num", 0)
        ));

        assertThat(refused.getData(), is(nullValue()));
        assertThat(
            refused.getErrors().stream().map(ActionTest::extensions).toList(),
            contains(refusal("RULE", "Bar.numPositive", "draft.num"))
        );
    }


    private ExecutionResult execute(String mutation, Map<String, Object> variables)
    {
        return graphQL.execute(
            ExecutionInput.newExecutionInput()
                .query(mutation)
                .variables(variables)
                .build()
        );
    }


    private static Object data(ExecutionResult result, String field)
    {
        final Map<String, Object> data = result.getData();
        return data.get(field);
    }


    private static Map<String, Object> extensions(GraphQLError error)
    {
        return error.getExtensions();
    }


    private static Map<String, Object> refusal(String code, String messageKey, String field)
    {
        final Map<String, Object> map = new HashMap<>();
        map.put("code", code);
        map.put("messageKey", messageKey);
        map.put("field", field);
        map.put("errorClass", null);
        return map;
    }


    private static Map<String, Object> ref(String id, String version)
    {
        return Map.of("id", id, "version", version);
    }


    private String newBar(String name, String description)
    {
        final String id = UUID.randomUUID().toString();
        bars.add(id);

        final List<FieldChange> fields = new ArrayList<>(List.of(
            field("name", "String", name),
            field("num", "Int", 1),
            field("created", "Timestamp", Timestamp.valueOf("2026-10-22 12:00:00"))
        ));
        if (description != null)
        {
            fields.add(field("description", "String", description));
        }

        final EntityChange change = new EntityChange();
        change.setType("Bar");
        change.setId(id);
        change.setNew(true);
        change.setChanges(fields);

        assertThat(merge(change), is(MergeStatus.DONE));
        return id;
    }


    private void changeName(String id, String version, String name)
    {
        final EntityChange change = new EntityChange();
        change.setType("Bar");
        change.setId(id);
        change.setVersion(version);
        change.setChanges(List.of(field("name", "String", name)));

        assertThat(merge(change), is(MergeStatus.DONE));
    }


    private MergeStatus merge(EntityChange change)
    {
        return mergeService.merge(List.of(change), List.of(), List.of(), new MergeConfig()).getStatus();
    }


    private boolean approved(String id)
    {
        return dslContext.select(BAR.APPROVED).from(BAR).where(BAR.ID.eq(id)).fetchOne(BAR.APPROVED) != null;
    }


    private String version(String id)
    {
        return dslContext.select(BAR.VERSION).from(BAR).where(BAR.ID.eq(id)).fetchOne(BAR.VERSION);
    }


    private static FieldChange field(String name, String scalarType, Object value)
    {
        final FieldChange change = new FieldChange();
        change.setField(name);
        change.setValue(new GenericScalar(scalarType, value));
        return change;
    }
}
