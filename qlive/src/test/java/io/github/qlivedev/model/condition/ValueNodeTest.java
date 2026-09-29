package io.github.qlivedev.model.condition;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.qlivedev.runtime.scalar.FilterDSL.field;
import static io.github.qlivedev.runtime.scalar.FilterDSL.values;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

/// Covers the fluent builders of ValueNode. They are one line each and all alike, which is what makes a
/// wrong name in one of them easy to miss: the node it builds is valid, it just means another operator.
class ValueNodeTest
{
    /// builders whose node is named differently on purpose
    private static final Map<String, String> RENAMED = Map.of("asText", "toString");


    @Test
    void everyBuilderNamesItsNodeAfterItself() throws Exception
    {
        final List<String> wrong = new ArrayList<>();
        for (Method method : ValueNode.class.getDeclaredMethods())
        {
            final Class<?> result = method.getReturnType();
            if (!Modifier.isPublic(method.getModifiers()) ||
                (result != Condition.class && result != Operation.class))
            {
                continue;
            }

            final Class<?>[] types = method.getParameterTypes();
            final Object[] operands = new Object[types.length];
            for (int i = 0; i < operands.length; i++)
            {
                operands[i] = types[i] == Values.class ? values(List.of(i), "Int") : field("operand" + i);
            }
            final CNode node = (CNode) method.invoke(field("receiver"), operands);

            final String name = node instanceof Condition c ? c.getName() : ((Operation) node).getName();
            final String expected = RENAMED.getOrDefault(method.getName(), method.getName());
            if (!name.equals(expected))
            {
                wrong.add(method.getName() + "() builds " + name);
            }
        }
        assertThat(wrong, is(empty()));
    }
}
