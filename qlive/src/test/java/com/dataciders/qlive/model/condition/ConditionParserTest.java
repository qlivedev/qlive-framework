package com.dataciders.qlive.model.condition;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Covers reading a FilterDSL hierarchy from JSON text. The "type" each node carries is what says which
/// subclass to build, and it is a derived property with no setter, so it has to steer the parse without
/// being written back to the node.
class ConditionParserTest
{
    private final ConditionParser parser = new ConditionParser();


    @Test
    void buildsTheSubclassEachNodeNames()
    {
        final CNode node = parser.parse(
            """
            {
                "type": "Condition",
                "name": "eq",
                "operands": [
                    { "type": "Field", "name": "name" },
                    { "type": "Value", "scalarType": "String", "value": "Foo #1" }
                ]
            }
            """
        );

        assertThat(node, is(instanceOf(Condition.class)));

        final Condition condition = (Condition) node;
        assertThat(condition.getName(), is("eq"));
        assertThat(condition.getType(), is("Condition"));

        final List<CNode> operands = condition.getOperands();

        final Field field = (Field) operands.get(0);
        assertThat(field.getName(), is("name"));
        assertThat(field.getType(), is("Field"));

        final Value value = (Value) operands.get(1);
        assertThat(value.getScalarType(), is("String"));
        assertThat(value.getValue(), is("Foo #1"));
        assertThat(value.getType(), is("Value"));
    }


    /// However deeply the hierarchy nests, and whichever node type sits at a level: an operand is a CNode,
    /// so every level is chosen by the same discriminator.
    ///
    /// Values arrive as the Java types JSON has, a number being a Long. This parser has no schema to ask
    /// what a node's scalarType means -- ConditionCoercing is the one that turns values into the types
    /// they name, and it needs a DomainQL to do it.
    @Test
    void buildsThemAtEveryLevel()
    {
        final CNode node = parser.parse(
            """
            {
                "type": "Component",
                "id": "filter",
                "condition": {
                    "type": "Condition",
                    "name": "in",
                    "operands": [
                        {
                            "type": "Operation",
                            "name": "plus",
                            "operands": [
                                { "type": "Field", "name": "num" },
                                { "type": "Value", "scalarType": "Int", "value": 1 }
                            ]
                        },
                        { "type": "Values", "scalarType": "Int", "values": [ 1, 2, 3 ] }
                    ]
                }
            }
            """
        );

        final Component component = (Component) node;
        assertThat(component.getId(), is("filter"));

        final List<CNode> operands = ((Condition) component.getCondition()).getOperands();

        final Operation operation = (Operation) operands.get(0);
        assertThat(operation.getName(), is("plus"));
        assertThat(((Field) operation.getOperands().get(0)).getName(), is("num"));
        assertThat(((Value) operation.getOperands().get(1)).getValue(), is(1L));

        assertThat(((Values) operands.get(1)).getValues(), contains(1L, 2L, 3L));
    }


    /// The convenience entry point for the common case, where the caller wants a condition and anything
    /// else is a mistake worth hearing about.
    @Test
    void parseConditionRefusesAnythingButACondition()
    {
        assertThat(
            parser.parseCondition("{ \"type\": \"Condition\", \"name\": \"isTrue\", \"operands\": [] }").getName(),
            is("isTrue")
        );

        assertThrows(
            IllegalStateException.class,
            () -> parser.parseCondition("{ \"type\": \"Field\", \"name\": \"name\" }")
        );
    }
}
