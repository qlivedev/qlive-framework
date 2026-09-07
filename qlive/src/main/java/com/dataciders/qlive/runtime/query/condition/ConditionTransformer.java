package com.dataciders.qlive.runtime.query.condition;

import com.dataciders.qlive.model.condition.CNode;
import com.dataciders.qlive.model.condition.Component;
import com.dataciders.qlive.model.condition.Operation;
import com.dataciders.qlive.model.condition.Value;
import com.dataciders.qlive.model.condition.Values;
import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.scalar.ComputedValue;
import org.jooq.Condition;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/// Turns a FilterDSL condition graph into JOOQ conditions, sort fields and value expressions.
///
/// The one thing that varies between callers is the {@link FieldResolver}, which says what a field path
/// means: a caller filtering a single table resolves names against that table, while the query document
/// plugs in a resolver that knows about joins and to-many relations.
///
/// The values are already the Java objects they claim to be -- reading a condition's JSON is
/// {@link com.dataciders.qlive.runtime.scalar.ConditionCoercing}'s business, and it converts every value in
/// the hierarchy with the coercing of the scalar type that value names. What is left to do with one here is
/// to bind it as the type of the field it is compared to.
///
/// Values are always bound, never rendered into the SQL, and operator names are checked against
/// {@link FilterOperators}' positive list before anything is done with them. Conditions come from browsers.
public class ConditionTransformer
{
    private final FieldResolver resolver;


    public ConditionTransformer(FieldResolver resolver)
    {
        this.resolver = resolver;
    }


    /// Transforms a condition graph.
    ///
    /// @param node      root of the condition, may be `null`
    ///
    /// @return JOOQ condition, or `null` for a condition that turns out to constrain nothing
    public Condition transform(CNode node)
    {
        return condition(node);
    }


    /// Transforms one entry of the query config's sort fields, which is either a plain field expression or
    /// an `asc()` / `desc()` around one.
    public SortField<?> sortField(CNode node)
    {
        final List<ExistsScope> scopes = new ArrayList<>();

        if (node instanceof Operation operation && ("asc".equals(operation.getName()) || "desc".equals(
            operation.getName())))
        {
            final List<CNode> operands = operation.getOperands();
            if (operands == null || operands.size() != 1)
            {
                throw new QLiveException("'" + operation.getName() + "' takes exactly one operand");
            }

            final Field<?> field = field(operands.get(0), null, scopes);
            rejectToMany(scopes);
            return "desc".equals(operation.getName()) ? field.desc() : field.asc();
        }

        final Field<?> field = field(node, null, scopes);
        rejectToMany(scopes);
        return field.asc();
    }


    // -----------------------------------------------------------------------------------------------------
    // conditions
    // -----------------------------------------------------------------------------------------------------

    private Condition condition(CNode node)
    {
        return switch (node)
        {
            case null -> null;
            case Component component -> condition(component.getCondition());
            case com.dataciders.qlive.model.condition.Condition c ->
                FilterOperators.LOGIC.contains(c.getName()) ? logic(c) : comparison(c);
            default -> throw new QLiveException("Cannot use a " + node.getType() + " node as a condition");
        };
    }


    /// The logic operators, which combine conditions rather than fields.
    ///
    /// Operands that transform to nothing are dropped: the client's FilterDSL writes a `null` operand for a
    /// filter component that currently filters nothing, and an `and()` of those constrains nothing at all.
    private Condition logic(com.dataciders.qlive.model.condition.Condition node)
    {
        final String name = node.getName();

        final List<Condition> operands = new ArrayList<>();
        if (node.getOperands() != null)
        {
            for (CNode operand : node.getOperands())
            {
                final Condition condition = condition(operand);
                if (condition != null)
                {
                    operands.add(condition);
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
            return operands.get(0).not();
        }

        Condition result = operands.get(0);
        for (Condition operand : operands.subList(1, operands.size()))
        {
            result = switch (name)
            {
                case "and" -> result.and(operand);
                case "or" -> result.or(operand);
                case "andNot" -> result.andNot(operand);
                case "orNot" -> result.orNot(operand);
                default -> throw new QLiveException("Invalid logic operator: " + name);
            };
        }
        return result;
    }


    private Condition comparison(com.dataciders.qlive.model.condition.Condition node)
    {
        final List<ExistsScope> scopes = new ArrayList<>();

        final Object result = apply(node.getName(), node.getOperands(), scopes);
        if (!(result instanceof Condition condition))
        {
            throw new QLiveException("Filter operator '" + node.getName() + "' does not produce a condition");
        }

        return wrap(condition, scopes);
    }


    /// Wraps a condition in the `EXISTS` of every to-many relation its fields reached through.
    ///
    /// Deepest first, so that a scope nested inside another ends up inside the other's subquery, where the
    /// alias it correlates to is in scope. Two unrelated to-many relations in one comparison nest as well,
    /// which is what keeps the two sides of something like `a.x eq b.y` correlated.
    private Condition wrap(Condition condition, List<ExistsScope> scopes)
    {
        if (scopes.isEmpty())
        {
            return condition;
        }

        final List<ExistsScope> ordered = new ArrayList<>(new LinkedHashSet<>(scopes));
        ordered.sort(Comparator.comparingInt(ExistsScope::depth).reversed());

        Condition result = condition;
        for (ExistsScope scope : ordered)
        {
            result = scope.wrap(result);
        }
        return result;
    }


    // -----------------------------------------------------------------------------------------------------
    // values
    // -----------------------------------------------------------------------------------------------------

    /// Applies one operator to its operands. The first operand is the field or expression the operator is
    /// applied to, and its type is what the remaining operands' values are read as.
    private Object apply(String name, List<CNode> operands, List<ExistsScope> scopes)
    {
        FilterOperators.checkAllowed(name);

        if (operands == null || operands.isEmpty())
        {
            throw new QLiveException("Filter operator '" + name + "' has no operands");
        }

        final Field<?> receiver = field(operands.get(0), null, scopes);

        if (name.equals("in"))
        {
            return in(receiver, operands);
        }

        final DataType<?> hint = receiver.getDataType();
        final List<Field<?>> args = new ArrayList<>(operands.size() - 1);
        for (CNode operand : operands.subList(1, operands.size()))
        {
            args.add(field(operand, hint, scopes));
        }

        return FilterOperators.invoke(name, receiver, args);
    }


    @SuppressWarnings("unchecked")
    private Condition in(Field<?> receiver, List<CNode> operands)
    {
        if (operands.size() != 2 || !(operands.get(1) instanceof Values values))
        {
            throw new QLiveException("'in' takes exactly one list of values");
        }

        final DataType<Object> type = (DataType<Object>) receiver.getDataType();

        final List<Object> converted = new ArrayList<>();
        if (values.getValues() != null)
        {
            for (Object value : values.getValues())
            {
                converted.add(type.convert(value));
            }
        }

        return receiver.in(converted);
    }


    private Field<?> field(CNode node, DataType<?> hint, List<ExistsScope> scopes)
    {
        return switch (node)
        {
            case null -> throw new QLiveException("Filter operand is null");

            case com.dataciders.qlive.model.condition.Field f ->
            {
                final ResolvedField resolved = resolver.resolve(f.getName());
                scopes.addAll(resolved.scopes());
                yield resolved.field();
            }

            case Value value -> value(value, hint);

            case Operation operation ->
            {
                final Object result = apply(operation.getName(), operation.getOperands(), scopes);
                if (!(result instanceof Field<?> field))
                {
                    throw new QLiveException(
                        "Filter operation '" + operation.getName() + "' does not produce a value"
                    );
                }
                yield field;
            }

            case Values ignored -> throw new QLiveException(
                "A list of values is only valid as the operand of 'in'"
            );

            default -> throw new QLiveException("Cannot use a " + node.getType() + " node as a value");
        };
    }


    @SuppressWarnings("unchecked")
    private Field<?> value(Value value, DataType<?> hint)
    {
        final Object raw = value.getValue();

        if (raw instanceof ComputedValue computedValue)
        {
            return computed(computedValue);
        }

        return hint == null ? DSL.val(raw) : DSL.val(raw, (DataType<Object>) hint);
    }


    /// The computed values are evaluated by the database, so that every row of one query sees the same
    /// "now" and it is the same clock the rest of the schema's defaults use.
    private Field<?> computed(ComputedValue value)
    {
        return switch (value.getName())
        {
            case "now" -> DSL.currentTimestamp();
            case "today" -> DSL.currentDate();
            case null, default -> throw new QLiveException("Unknown computed filter value: " + value.getName());
        };
    }


    private void rejectToMany(List<ExistsScope> scopes)
    {
        if (!scopes.isEmpty())
        {
            throw new QLiveException(
                "Sort fields cannot follow a to-many relation: ordering by a set of rows would need an " +
                    "aggregate function, which the FilterDSL has no way to express"
            );
        }
    }
}
