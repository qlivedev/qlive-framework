package com.dataciders.qlive.runtime.query.condition;

import com.dataciders.qlive.runtime.QLiveException;
import org.jooq.Field;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// Dispatches FilterDSL operator names onto JOOQ's own API, gated by a positive list.
///
/// The names the FilterDSL can produce are, without exception, methods of {@link Field} taking as many
/// `Field` arguments as the node has operands beyond the first. That is what makes this a lookup instead of
/// seventy handwritten lambdas: two methods of one interface cannot share an erasure, so a name plus an
/// arity names exactly one method, and the mapping cannot drift out of step with the DSL by a typo. `in` is
/// the single exception -- its argument is a list of values -- and its caller handles it.
///
/// The positive list is the security boundary of the whole filter path. Conditions arrive from a browser,
/// and a name that is not on this list never reaches reflection.
final class FilterOperators
{
    /// Every operator name the FilterDSL can produce: the conditions and value operations of
    /// {@link com.dataciders.qlive.model.condition.ValueNode}, the logic operators of
    /// {@link com.dataciders.qlive.model.condition.Condition}, and the two sort operations.
    private final static Set<String> POSITIVE_LIST = Set.of(
        "greaterOrEqual", "lessOrEqual", "lt", "notBetweenSymmetric", "notEqualIgnoreCase", "betweenSymmetric",
        "lessThan", "equalIgnoreCase", "isDistinctFrom", "between", "ge", "greaterThan", "isNotNull", "notLikeRegex",
        "notBetween", "notEqual", "isFalse", "containsIgnoreCase", "eq", "gt", "equal", "likeRegex", "isTrue",
        "contains", "notContainsIgnoreCase", "notContains", "ne", "isNull", "endsWith", "le", "isNotDistinctFrom",
        "startsWith", "in", "not", "or", "orNot", "and", "andNot", "bitNand", "mod", "div", "neg", "rem", "add",
        "subtract", "plus", "bitAnd", "bitXor", "shl", "unaryMinus", "bitNor", "shr", "modulo", "bitXNor", "bitNot",
        "sub", "minus", "mul", "bitOr", "times", "pow", "divide", "power", "multiply", "unaryPlus", "lower",
        "upper", "asc", "desc"
    );

    /// The logic operators, which combine conditions instead of fields and are applied directly.
    final static Set<String> LOGIC = Set.of("and", "or", "not", "andNot", "orNot");

    private final static Map<String, Method> METHODS = new ConcurrentHashMap<>();


    private FilterOperators()
    {
        // no instances
    }


    /// Rejects any name that is not a FilterDSL operator, before it is used for anything at all.
    static void checkAllowed(String name)
    {
        if (name == null || !POSITIVE_LIST.contains(name))
        {
            throw new QLiveException("Invalid filter operator: " + name);
        }
    }


    /// Invokes the JOOQ method the given operator name and operand count identify.
    ///
    /// @param name         operator name, already checked against the positive list
    /// @param receiver     field the operator is applied to, i.e. the first operand
    /// @param args         remaining operands
    ///
    /// @return whatever JOOQ returns: a `Condition`, a `Field` or a `SortField`
    static Object invoke(String name, Field<?> receiver, List<Field<?>> args)
    {
        final Method method = METHODS.computeIfAbsent(
            name + "/" + args.size(),
            key -> lookup(name, args.size())
        );

        try
        {
            return method.invoke(receiver, args.toArray());
        }
        catch (InvocationTargetException e)
        {
            throw new QLiveException("Error applying filter operator '" + name + "'", e.getTargetException());
        }
        catch (IllegalAccessException e)
        {
            throw new QLiveException("Error applying filter operator '" + name + "'", e);
        }
    }


    private static Method lookup(String name, int arity)
    {
        final Class<?>[] parameterTypes = new Class<?>[arity];
        Arrays.fill(parameterTypes, Field.class);

        try
        {
            return Field.class.getMethod(name, parameterTypes);
        }
        catch (NoSuchMethodException e)
        {
            throw new QLiveException(
                "Filter operator '" + name + "' does not take " + arity + " operand(s) besides the field it " +
                    "applies to"
            );
        }
    }
}
