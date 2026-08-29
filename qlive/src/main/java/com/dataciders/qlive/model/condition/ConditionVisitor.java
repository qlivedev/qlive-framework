package com.dataciders.qlive.model.condition;

/**
 * Visitor for FilterDSL condition hierarchies.
 */
@SuppressWarnings("UnusedReturnValue")
public interface ConditionVisitor<D>
{
    default Object visit(Condition condition, D data)
    {
        for (CNode operand : condition.getOperands())
        {
            if (operand != null)
            {
                operand.accept(this, data);
            }
        }
        return null;
    }

    default Object visit(Operation operation, D data)
    {
        for (CNode operand : operation.getOperands())
        {
            if (operand != null)
            {
                operand.accept(this, data);
            }
        }
        return null;
    }

    default Object visit(Component component, D data)
    {
        final CNode node = component.getCondition();
        return node.accept(this, data);
    }

    default Object visit(Field field, D data)
    {
        return null;
    }

    default Object visit(Value value, D data)
    {
        return null;
    }

    default Object visit(Values values, D data)
    {
        return null;
    }
}
