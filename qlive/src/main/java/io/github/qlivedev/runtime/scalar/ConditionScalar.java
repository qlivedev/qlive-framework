package io.github.qlivedev.runtime.scalar;

/**
 * Stands for the Condition scalar, see {@link ConditionType}, where scalars are known by Java class. Its values are
 * {@link io.github.qlivedev.model.condition.CNode}s, and so are the field expression scalar's, which is why neither
 * can be registered under that class: the second registration would replace the first.
 */
public final class ConditionScalar
{
    private ConditionScalar()
    {
        // no instances
    }
}
