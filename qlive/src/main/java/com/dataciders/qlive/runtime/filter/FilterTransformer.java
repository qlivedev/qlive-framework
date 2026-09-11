package com.dataciders.qlive.runtime.filter;

import com.dataciders.qlive.model.condition.CNode;
import com.dataciders.qlive.model.condition.Component;
import com.dataciders.qlive.model.condition.Field;
import com.dataciders.qlive.model.condition.Operation;
import com.dataciders.qlive.model.condition.Value;
import com.dataciders.qlive.model.condition.Values;
import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.scalar.ComputedValue;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/// Compiles a FilterDSL condition into a predicate over one plain Java object.
///
/// A peer of {@link com.dataciders.qlive.runtime.query.condition.ConditionTransformer}, not a variant of
/// it: the same condition model, a different backend. That one builds JOOQ conditions a database evaluates
/// over rows; this one builds a tree of composed {@link Predicate}s the server evaluates over one live
/// Java object, which is what a pub/sub message is.
///
/// Compiled once and evaluated many times from then on -- in pub/sub, its first caller, compiled when a
/// subscription is registered and evaluated per published message. Everything that can be decided from
/// the condition alone is decided at compile time, while whoever asked for the predicate is still there
/// to be told: a field path that names no property of the declared class, an operator this backend cannot
/// honour, an operator given the wrong number of operands. None of those may become a filter that
/// silently never matches.
///
/// What reaches a payload is plain property access, always -- see {@link PropertyPath}. There is no GraphQL
/// field resolution anywhere in here and no query, for any payload, including one that happens to be a
/// `DomainObject`: nothing here selects fields per caller, so the machinery that serves a selection has
/// nothing to do. The obligation that puts on whoever produces the object is plain in return -- whatever
/// relation a condition might reach through has to be populated on the instance handed over. For pub/sub
/// that means the instance passed to `publish()`.
///
/// Values are already the Java objects they claim to be, the same contract the SQL transformer works
/// under: reading a condition's JSON is
/// {@link com.dataciders.qlive.runtime.scalar.ConditionCoercing}'s business, and it converts every value
/// in the hierarchy with the coercing of the scalar type that value names. A condition that arrives
/// straight off the wire has to go through that before it gets here, or its timestamps are still strings.
public class FilterTransformer
{
    private final Class<?> declaredType;


    /// @param declaredType   class the objects this predicate will read are declared to have, which is
    ///                       what field paths are checked against. Nothing enforces it at evaluation
    ///                       time -- a path resolves against whatever object actually arrives -- so this
    ///                       constrains the condition rather than the subject. `null` where no shape is
    ///                       declared, in which case paths are taken as written and nothing is checked.
    public FilterTransformer(Class<?> declaredType)
    {
        this.declaredType = declaredType;
    }


    /// Compiles a condition graph.
    ///
    /// @param node      root of the condition, may be `null`
    ///
    /// @return predicate over a payload, or `null` for a condition that turns out to constrain nothing,
    ///         which a caller reads as "everything" -- a subscription, as "every message on this channel"
    public Predicate<Object> transform(CNode node)
    {
        return condition(node);
    }


    // -----------------------------------------------------------------------------------------------------
    // conditions
    // -----------------------------------------------------------------------------------------------------

    private Predicate<Object> condition(CNode node)
    {
        return switch (node)
        {
            case null -> null;
            case Component component -> condition(component.getCondition());
            case com.dataciders.qlive.model.condition.Condition c ->
                PayloadOperators.LOGIC.contains(c.getName()) ? logic(c) : comparison(c);
            default -> throw new QLiveException("Cannot use a " + node.getType() + " node as a condition");
        };
    }


    /// The logic operators, which combine conditions rather than values.
    ///
    /// Operands that compile to nothing are dropped, the same way the SQL backend drops them: the client's
    /// FilterDSL writes a `null` operand for a filter component that currently filters nothing, and an
    /// `and()` of those constrains nothing at all.
    private Predicate<Object> logic(com.dataciders.qlive.model.condition.Condition node)
    {
        final String name = node.getName();

        final List<Predicate<Object>> operands = new ArrayList<>();
        if (node.getOperands() != null)
        {
            for (CNode operand : node.getOperands())
            {
                final Predicate<Object> predicate = condition(operand);
                if (predicate != null)
                {
                    operands.add(predicate);
                }
            }
        }

        if (operands.isEmpty())
        {
            return null;
        }

        if (name.equals("not"))
        {
            if (operands.size() != 1)
            {
                throw new QLiveException("'not' takes exactly one operand");
            }
            return operands.get(0).negate();
        }

        Predicate<Object> result = operands.get(0);
        for (Predicate<Object> operand : operands.subList(1, operands.size()))
        {
            result = switch (name)
            {
                case "and" -> result.and(operand);
                case "or" -> result.or(operand);
                case "andNot" -> result.and(operand.negate());
                case "orNot" -> result.or(operand.negate());
                default -> throw new QLiveException("Invalid logic operator: " + name);
            };
        }
        return result;
    }


    private Predicate<Object> comparison(com.dataciders.qlive.model.condition.Condition node)
    {
        final String name = node.getName();
        final List<CNode> operands = node.getOperands();

        if (operands == null || operands.isEmpty())
        {
            throw new QLiveException("Filter operator '" + name + "' has no operands");
        }

        if (name.equals("in"))
        {
            return in(operands);
        }

        final PayloadOperators.ConditionOp op = PayloadOperators.condition(name, operands.size());
        final List<ValueExpression> arguments = values(operands);

        return payload -> op.impl().matches(evaluate(arguments, payload));
    }


    /// `in` is the one operator whose second operand is a list of values rather than a value, which is
    /// what keeps it out of the operator table on both backends.
    private Predicate<Object> in(List<CNode> operands)
    {
        if (operands.size() != 2 || !(operands.get(1) instanceof Values values))
        {
            throw new QLiveException("'in' takes exactly one list of values");
        }

        final ValueExpression receiver = value(operands.get(0));
        final List<Object> candidates = values.getValues() == null ? List.of() : values.getValues();

        return payload -> {
            final Object value = receiver.evaluate(payload);

            if (value == null)
            {
                return false;
            }

            for (Object candidate : candidates)
            {
                if (PayloadOperators.equalValues(value, candidate))
                {
                    return true;
                }
            }
            return false;
        };
    }


    // -----------------------------------------------------------------------------------------------------
    // values
    // -----------------------------------------------------------------------------------------------------

    /// One operand of a condition or an operation, compiled: everything about it that a payload does not
    /// decide is already decided.
    @FunctionalInterface
    private interface ValueExpression
    {
        Object evaluate(Object payload);
    }


    private List<ValueExpression> values(List<CNode> nodes)
    {
        final List<ValueExpression> expressions = new ArrayList<>(nodes.size());
        for (CNode node : nodes)
        {
            expressions.add(value(node));
        }
        return expressions;
    }


    private ValueExpression value(CNode node)
    {
        return switch (node)
        {
            case null -> throw new QLiveException("Filter operand is null");

            case Field field ->
            {
                final PropertyPath path = PropertyPath.compile(field.getName(), declaredType);
                yield path::read;
            }

            case Value value -> constant(value.getValue());

            case Operation operation ->
            {
                final List<CNode> operands = operation.getOperands();

                if (operands == null || operands.isEmpty())
                {
                    throw new QLiveException("Filter operator '" + operation.getName() + "' has no operands");
                }

                final PayloadOperators.ValueOp op = PayloadOperators.operation(operation.getName(), operands.size());
                final List<ValueExpression> arguments = values(operands);

                yield payload -> op.impl().apply(evaluate(arguments, payload));
            }

            case Values ignored -> throw new QLiveException(
                "A list of values is only valid as the operand of 'in'"
            );

            default -> throw new QLiveException("Cannot use a " + node.getType() + " node as a value");
        };
    }


    /// A constant, or -- for the two computed ones -- a reading taken per message.
    ///
    /// The SQL backend hands `now` and `today` to the database, so that every row of one query sees the
    /// same clock the rest of the schema's defaults use. There is no such clock here, and the closest
    /// honest thing is the moment the message is being matched.
    private static ValueExpression constant(Object raw)
    {
        if (raw instanceof ComputedValue computed)
        {
            return switch (computed.getName())
            {
                case "now" -> payload -> new Timestamp(System.currentTimeMillis());
                case "today" -> payload -> Date.valueOf(LocalDate.now());
                case null, default -> throw new QLiveException("Unknown computed filter value: " + computed.getName());
            };
        }

        return payload -> raw;
    }


    private static List<Object> evaluate(List<ValueExpression> expressions, Object payload)
    {
        final List<Object> values = new ArrayList<>(expressions.size());
        for (ValueExpression expression : expressions)
        {
            values.add(expression.evaluate(payload));
        }
        return values;
    }
}
