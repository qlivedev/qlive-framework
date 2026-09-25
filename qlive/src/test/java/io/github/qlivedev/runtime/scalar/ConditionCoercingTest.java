package io.github.qlivedev.runtime.scalar;

import io.github.qlivedev.model.QueryConfig;
import io.github.qlivedev.model.condition.CNode;
import io.github.qlivedev.model.condition.Component;
import io.github.qlivedev.model.condition.Condition;
import io.github.qlivedev.model.condition.Operation;
import io.github.qlivedev.model.condition.Value;
import io.github.qlivedev.model.condition.Values;
import io.github.qlivedev.runtime.domain.TestDomainConfig;
import io.github.qlivedev.runtime.domain.TestLogic;
import io.github.qlivedev.graphql.QLiveDomain;
import graphql.GraphQLContext;
import graphql.schema.Coercing;
import graphql.schema.GraphQLScalarType;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

/// A condition arrives as JSON, where a timestamp is a string. Reading those is the scalar's business, and
/// the condition scalar knows which scalar owns a value because the node says so -- so what comes out of a
/// parse is a condition whose values are the Java objects they claim to be, wherever in the hierarchy they
/// sit.
class ConditionCoercingTest
{
    private final static GraphQLContext CONTEXT = GraphQLContext.newContext().build();

    private final QLiveDomain domain = TestDomainConfig.domain(new TestLogic());

    /// Built rather than taken from the schema, which is also how the query config's coercing comes by
    /// one: a condition only ever travels inside a query config, so the condition scalar is not itself
    /// referenced by any type and never lands in the schema. Being QLiveDomainAware is what makes an instance
    /// of it usable anyway.
    private final ConditionCoercing coercing = conditionCoercing();


    private ConditionCoercing conditionCoercing()
    {
        final ConditionCoercing conditionCoercing = new ConditionCoercing();
        conditionCoercing.setDomain(domain);
        return conditionCoercing;
    }


    @Test
    void convertsValuesWithTheScalarTheyName()
    {
        final CNode node = coercing.parseValue(
            comparison(
                "lt",
                Map.of("type", "Field", "name", "created"),
                Map.of("type", "Value", "scalarType", "Timestamp", "value", "2018-11-01T19:58:59.000Z")
            ),
            CONTEXT,
            Locale.getDefault()
        );

        final Value value = (Value) ((Condition) node).getOperands().get(1);

        assertThat(value.getValue(), is(instanceOf(Timestamp.class)));
        assertThat(value.getValue(), is(Timestamp.from(Instant.parse("2018-11-01T19:58:59.000Z"))));
    }


    /// Including the values below an operation, and the ones inside a list: the walk that finds a node is
    /// the walk that converts it.
    @Test
    void convertsValuesWhereverTheyAre()
    {
        final CNode node = coercing.parseValue(
            comparison(
                "in",
                Map.of(
                    "type", "Operation",
                    "name", "plus",
                    "operands", List.of(
                        Map.of("type", "Field", "name", "num"),
                        Map.of("type", "Value", "scalarType", "Int", "value", 1)
                    )
                ),
                Map.of("type", "Values", "scalarType", "Int", "values", List.of(1, 2, 3))
            ),
            CONTEXT,
            Locale.getDefault()
        );

        final List<CNode> operands = ((Condition) node).getOperands();

        final io.github.qlivedev.model.condition.Operation operation =
            (io.github.qlivedev.model.condition.Operation) operands.get(0);
        assertThat(((Value) operation.getOperands().get(1)).getValue(), is(1));

        assertThat(((Values) operands.get(1)).getValues(), contains(1, 2, 3));
    }


    /// And back out again as the JSON it came in as. A query document returns the config it applied, and
    /// the client spreads that config over its next update() and sends it back, so what comes out has to
    /// be something that can go in.
    @Test
    void serializesBackToJsonThatCanBeParsedAgain()
    {
        final Map<String, Object> json = comparison(
            "eq",
            Map.of("type", "Field", "name", "name"),
            Map.of("type", "Value", "scalarType", "String", "value", "Foo #1")
        );

        final CNode parsed = coercing.parseValue(json, CONTEXT, Locale.getDefault());

        assertThat(coercing.serialize(parsed, CONTEXT, Locale.getDefault()), is(json));
    }


    /// Timestamps included, which only holds because the scalar writes the UTC it reads: labeling a local
    /// time "Z" would send a filter back an offset away from the one that arrived.
    @Test
    void keepsTimestampsThroughTheRoundTrip()
    {
        final Map<String, Object> json = comparison(
            "between",
            Map.of("type", "Field", "name", "created"),
            Map.of("type", "Value", "scalarType", "Timestamp", "value", "2018-11-01T19:58:59.000Z"),
            Map.of("type", "Value", "scalarType", "Timestamp", "value", "2019-06-21T14:00:00.000Z")
        );

        final CNode parsed = coercing.parseValue(json, CONTEXT, Locale.getDefault());

        assertThat(coercing.serialize(parsed, CONTEXT, Locale.getDefault()), is(json));
    }


    /// A component is the client's marker for which part of a filter form a condition came from. The
    /// database has no use for it and the transformer drops it, but what the client gets back has to still
    /// have it: the form finds its own part of the condition by that id, and a component the way back home
    /// loses is a filter field that comes back empty.
    @Test
    void keepsComponentsThroughTheRoundTrip()
    {
        final Map<String, Object> json = Map.of(
            "type", "Component",
            "id", "nameFilter",
            "condition", comparison(
                "eq",
                Map.of("type", "Field", "name", "name"),
                Map.of("type", "Value", "scalarType", "String", "value", "Foo #1")
            )
        );

        final CNode parsed = coercing.parseValue(json, CONTEXT, Locale.getDefault());

        final Component component = (Component) parsed;
        assertThat(component.getId(), is("nameFilter"));
        assertThat(component.getCondition(), is(instanceOf(Condition.class)));

        assertThat(coercing.serialize(parsed, CONTEXT, Locale.getDefault()), is(json));
    }


    /// The whole way in and out, through the scalar a query actually declares. The config's coercing
    /// delegates to condition coercings of its own, and this is what says they were given a domain.
    @Test
    void convertsTheValuesInsideAQueryConfig()
    {
        @SuppressWarnings("unchecked")
        final Coercing<QueryConfig, Map<String, Object>> configCoercing =
            (Coercing<QueryConfig, Map<String, Object>>) ((GraphQLScalarType) domain.getGraphQLSchema()
                .getType("QueryConfig")).getCoercing();

        final Map<String, Object> condition = comparison(
            "lt",
            Map.of("type", "Field", "name", "created"),
            Map.of("type", "Value", "scalarType", "Timestamp", "value", "2018-11-01T19:58:59.000Z")
        );

        final QueryConfig config = configCoercing.parseValue(
            Map.of("pageSize", 10, "offset", 0, "condition", condition),
            CONTEXT,
            Locale.getDefault()
        );

        final Value value = (Value) ((Condition) config.getCondition()).getOperands().get(1);
        assertThat(value.getValue(), is(instanceOf(Timestamp.class)));

        @SuppressWarnings("unchecked")
        final Map<String, Object> serialized =
            (Map<String, Object>) configCoercing.serialize(config, CONTEXT, Locale.getDefault())
                .get("condition");

        // the node types survive, so the config can be sent straight back in
        assertThat(serialized.get("type"), is("Condition"));
        assertThat(((List<Map<String, Object>>) serialized.get("operands")).get(0).get("type"), is("Field"));
    }


    /// The other way in. A `Subscribe` frame is parsed by Svenson, which builds the node classes and
    /// leaves what is inside them as whatever JSON had -- so a condition that never went through GraphQL
    /// arrives with a timestamp that is still a string, and comparing that to an instant compares a string
    /// to an instant.
    @Test
    void readsTheValuesOfAConditionThatDidNotComeThroughGraphQL()
    {
        final Value asParsed = new Value();
        asParsed.setScalarType("Timestamp");
        asParsed.setValue("2018-11-01T19:58:59.000Z");

        final Condition off = new Condition();
        off.setName("lt");
        off.setOperands(List.of(FilterDSL.field("created"), asParsed));

        final Condition coerced = (Condition) coercing.coerceValues(off, CONTEXT, Locale.ROOT);

        final Value value = (Value) coerced.getOperands().get(1);

        assertThat(value.getValue(), is(Timestamp.from(Instant.parse("2018-11-01T19:58:59.000Z"))));
        assertThat(value.getScalarType(), is("Timestamp"));

        // a new tree: the message the node arrived in is the caller's, and finds it as it came
        assertThat(asParsed.getValue(), is(instanceOf(String.class)));
    }


    /// Wherever they sit, which is the same walk the map-side parse does: below an operation, inside a
    /// list, under a component.
    @Test
    void readsThemWhereverTheyAre()
    {
        final Values list = new Values();
        list.setScalarType("Int");
        list.setValues(List.of(1, 2, 3));

        final Value operand = new Value();
        operand.setScalarType("Timestamp");
        operand.setValue("2019-06-21T14:00:00.000Z");

        final Operation operation = new Operation();
        operation.setName("plus");
        operation.setOperands(List.of(FilterDSL.field("created"), operand));

        final Condition in = new Condition();
        in.setName("in");
        in.setOperands(List.of(operation, list));

        final Component coerced =
            (Component) coercing.coerceValues(FilterDSL.component("part", in), CONTEXT, Locale.ROOT);

        final Condition condition = (Condition) coerced.getCondition();
        final Operation coercedOperation = (Operation) condition.getOperands().get(0);

        assertThat(coerced.getId(), is("part"));
        assertThat(
            ((Value) coercedOperation.getOperands().get(1)).getValue(),
            is(Timestamp.from(Instant.parse("2019-06-21T14:00:00.000Z")))
        );
        assertThat(((Values) condition.getOperands().get(1)).getValues(), contains(1, 2, 3));
    }


    /// A subscription with no condition is a subscription to everything the channel carries, and that has
    /// to survive the pass rather than become an empty condition that matches nothing.
    @Test
    void leavesNothingAsNothing()
    {
        assertThat(coercing.coerceValues(null, CONTEXT, Locale.ROOT), is((CNode) null));
    }


    @SafeVarargs
    private static Map<String, Object> comparison(String name, Map<String, Object>... operands)
    {
        return Map.of("type", "Condition", "name", name, "operands", List.of(operands));
    }
}
