package com.dataciders.qlive.runtime.filter;

import com.dataciders.qlive.runtime.QLiveException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Dispatches FilterDSL operator names onto implementations that work on plain Java values.
///
/// Mirrors {@link com.dataciders.qlive.runtime.query.condition.FilterOperators}' shape -- a table that is
/// at the same time the positive list, and the security boundary of a path conditions arrive from browsers
/// through -- but not its mechanism. There is no Java interface whose methods happen to be named after the
/// DSL's operators the way JOOQ's `Field` is, so each one is written out.
///
/// Three rules run through the whole table:
///
/// - A null operand makes a comparison not match, rather than throw. A publisher legitimately having
///   nothing at a path is ordinary, and a live fan-out is no place to find out about it. `isNull`,
///   `isNotNull`, `isTrue` and `isFalse` are the exceptions, being about the absence itself.
/// - Numbers are compared and combined by value, never by class. A condition's constants are whatever JSON
///   and the scalar coercing made of them and a payload's fields are whatever the publisher declared;
///   making a filter depend on a `Long` meeting a `Long` would make it depend on both.
/// - Bit operations go through `BigInteger`, because the one they exist for here is a 128-bit field mask.
///
/// The logic is two-valued, unlike the SQL backend's. `not` around a comparison that did not match holds,
/// where in SQL a comparison against NULL is itself NULL and stays NULL when negated.
final class PayloadOperators
{
    /// The logic operators, which combine conditions instead of values and are composed directly.
    final static Set<String> LOGIC = Set.of("and", "or", "not", "andNot", "orNot");

    /// The FilterDSL operators this backend refuses, and why. Refused when the subscription is compiled,
    /// with whoever registered it still listening, rather than becoming a filter that quietly never
    /// matches.
    private final static Map<String, String> UNSUPPORTED = Map.of(
        "isDistinctFrom", "null-safe comparison is a SQL notion; this backend's comparisons are two-valued",
        "isNotDistinctFrom", "null-safe comparison is a SQL notion; this backend's comparisons are two-valued",
        "asc", "sorting has no meaning for a single payload",
        "desc", "sorting has no meaning for a single payload"
    );


    interface ConditionImpl
    {
        boolean matches(List<Object> operands);
    }


    interface ValueImpl
    {
        Object apply(List<Object> operands);
    }


    /// @param arity   number of operands, the value the operator applies to included
    record ConditionOp(
        int arity,
        ConditionImpl impl
    )
    {
    }


    /// @param arity   number of operands, the value the operator applies to included
    record ValueOp(
        int arity,
        ValueImpl impl
    )
    {
    }


    private final static Map<String, ConditionOp> CONDITIONS = Map.ofEntries(
        Map.entry("eq", defined(2, o -> equalValues(o.get(0), o.get(1)))),
        Map.entry("equal", defined(2, o -> equalValues(o.get(0), o.get(1)))),
        Map.entry("ne", defined(2, o -> !equalValues(o.get(0), o.get(1)))),
        Map.entry("notEqual", defined(2, o -> !equalValues(o.get(0), o.get(1)))),

        Map.entry("lt", defined(2, o -> compareValues(o.get(0), o.get(1)) < 0)),
        Map.entry("lessThan", defined(2, o -> compareValues(o.get(0), o.get(1)) < 0)),
        Map.entry("le", defined(2, o -> compareValues(o.get(0), o.get(1)) <= 0)),
        Map.entry("lessOrEqual", defined(2, o -> compareValues(o.get(0), o.get(1)) <= 0)),
        Map.entry("gt", defined(2, o -> compareValues(o.get(0), o.get(1)) > 0)),
        Map.entry("greaterThan", defined(2, o -> compareValues(o.get(0), o.get(1)) > 0)),
        Map.entry("ge", defined(2, o -> compareValues(o.get(0), o.get(1)) >= 0)),
        Map.entry("greaterOrEqual", defined(2, o -> compareValues(o.get(0), o.get(1)) >= 0)),

        Map.entry("between", defined(3, o -> between(o.get(0), o.get(1), o.get(2)))),
        Map.entry("notBetween", defined(3, o -> !between(o.get(0), o.get(1), o.get(2)))),
        Map.entry("betweenSymmetric", defined(3, o -> betweenSymmetric(o.get(0), o.get(1), o.get(2)))),
        Map.entry("notBetweenSymmetric", defined(3, o -> !betweenSymmetric(o.get(0), o.get(1), o.get(2)))),

        Map.entry("equalIgnoreCase", defined(2, o -> text(o.get(0)).equalsIgnoreCase(text(o.get(1))))),
        Map.entry("notEqualIgnoreCase", defined(2, o -> !text(o.get(0)).equalsIgnoreCase(text(o.get(1))))),
        Map.entry("contains", defined(2, o -> text(o.get(0)).contains(text(o.get(1))))),
        Map.entry("notContains", defined(2, o -> !text(o.get(0)).contains(text(o.get(1))))),
        Map.entry("containsIgnoreCase", defined(2, o -> lower(o.get(0)).contains(lower(o.get(1))))),
        Map.entry("notContainsIgnoreCase", defined(2, o -> !lower(o.get(0)).contains(lower(o.get(1))))),
        Map.entry("startsWith", defined(2, o -> text(o.get(0)).startsWith(text(o.get(1))))),
        Map.entry("endsWith", defined(2, o -> text(o.get(0)).endsWith(text(o.get(1))))),
        Map.entry("likeRegex", defined(2, o -> text(o.get(0)).matches(text(o.get(1))))),
        Map.entry("notLikeRegex", defined(2, o -> !text(o.get(0)).matches(text(o.get(1))))),

        Map.entry("isNull", new ConditionOp(1, o -> o.get(0) == null)),
        Map.entry("isNotNull", new ConditionOp(1, o -> o.get(0) != null)),
        Map.entry("isTrue", new ConditionOp(1, o -> Boolean.TRUE.equals(o.get(0)))),
        Map.entry("isFalse", new ConditionOp(1, o -> Boolean.FALSE.equals(o.get(0))))
    );


    private final static Map<String, ValueOp> OPERATIONS = Map.ofEntries(
        Map.entry("add", arithmetic(2, o -> decimal(o.get(0)).add(decimal(o.get(1))))),
        Map.entry("plus", arithmetic(2, o -> decimal(o.get(0)).add(decimal(o.get(1))))),
        Map.entry("subtract", arithmetic(2, o -> decimal(o.get(0)).subtract(decimal(o.get(1))))),
        Map.entry("sub", arithmetic(2, o -> decimal(o.get(0)).subtract(decimal(o.get(1))))),
        Map.entry("minus", arithmetic(2, o -> decimal(o.get(0)).subtract(decimal(o.get(1))))),
        Map.entry("mul", arithmetic(2, o -> decimal(o.get(0)).multiply(decimal(o.get(1))))),
        Map.entry("times", arithmetic(2, o -> decimal(o.get(0)).multiply(decimal(o.get(1))))),
        Map.entry("multiply", arithmetic(2, o -> decimal(o.get(0)).multiply(decimal(o.get(1))))),
        Map.entry("div", arithmetic(2, o -> divide(o.get(0), o.get(1)))),
        Map.entry("divide", arithmetic(2, o -> divide(o.get(0), o.get(1)))),
        Map.entry("mod", arithmetic(2, o -> decimal(o.get(0)).remainder(decimal(o.get(1))))),
        Map.entry("modulo", arithmetic(2, o -> decimal(o.get(0)).remainder(decimal(o.get(1))))),
        Map.entry("rem", arithmetic(2, o -> decimal(o.get(0)).remainder(decimal(o.get(1))))),
        Map.entry("pow", arithmetic(2, o -> power(o.get(0), o.get(1)))),
        Map.entry("power", arithmetic(2, o -> power(o.get(0), o.get(1)))),
        Map.entry("neg", arithmetic(1, o -> decimal(o.get(0)).negate())),
        Map.entry("unaryMinus", arithmetic(1, o -> decimal(o.get(0)).negate())),
        Map.entry("unaryPlus", arithmetic(1, o -> decimal(o.get(0)))),

        Map.entry("bitAnd", arithmetic(2, o -> integer(o.get(0)).and(integer(o.get(1))))),
        Map.entry("bitOr", arithmetic(2, o -> integer(o.get(0)).or(integer(o.get(1))))),
        Map.entry("bitXor", arithmetic(2, o -> integer(o.get(0)).xor(integer(o.get(1))))),
        Map.entry("bitNand", arithmetic(2, o -> integer(o.get(0)).and(integer(o.get(1))).not())),
        Map.entry("bitNor", arithmetic(2, o -> integer(o.get(0)).or(integer(o.get(1))).not())),
        Map.entry("bitXNor", arithmetic(2, o -> integer(o.get(0)).xor(integer(o.get(1))).not())),
        Map.entry("bitNot", arithmetic(1, o -> integer(o.get(0)).not())),
        Map.entry("shl", arithmetic(2, o -> integer(o.get(0)).shiftLeft(integer(o.get(1)).intValueExact()))),
        Map.entry("shr", arithmetic(2, o -> integer(o.get(0)).shiftRight(integer(o.get(1)).intValueExact()))),

        Map.entry("lower", arithmetic(1, o -> text(o.get(0)).toLowerCase())),
        Map.entry("upper", arithmetic(1, o -> text(o.get(0)).toUpperCase())),
        Map.entry("concat", arithmetic(2, o -> text(o.get(0)) + text(o.get(1))))
    );


    private PayloadOperators()
    {
        // no instances
    }


    /// Looks up the condition operator of that name and operand count.
    ///
    /// @throws QLiveException   if the name is not an operator this backend honours, or is one that does
    ///                          not take that many operands
    static ConditionOp condition(String name, int arity)
    {
        final ConditionOp op = CONDITIONS.get(name);

        if (op == null)
        {
            throw missing(name, OPERATIONS.containsKey(name) ? "produces a value, not a condition" : null);
        }

        checkArity(name, op.arity(), arity);
        return op;
    }


    /// Looks up the value operation of that name and operand count.
    ///
    /// @throws QLiveException   if the name is not an operation this backend honours, or is one that does
    ///                          not take that many operands
    static ValueOp operation(String name, int arity)
    {
        final ValueOp op = OPERATIONS.get(name);

        if (op == null)
        {
            throw missing(name, CONDITIONS.containsKey(name) ? "produces a condition, not a value" : null);
        }

        checkArity(name, op.arity(), arity);
        return op;
    }


    /// Whether two values are the same value, with numbers compared by value across their classes.
    static boolean equalValues(Object a, Object b)
    {
        if (a instanceof Number numberA && b instanceof Number numberB)
        {
            return decimal(numberA).compareTo(decimal(numberB)) == 0;
        }

        return Objects.equals(a, b);
    }


    /// Says which of the three ways a name can fail to name an operator here it failed in: it is a
    /// FilterDSL operator this backend refuses, it is one that produces the other kind of thing, or it is
    /// no operator at all.
    private static QLiveException missing(String name, String wrongKind)
    {
        final String refused = UNSUPPORTED.get(name);

        if (refused != null)
        {
            return new QLiveException(
                "Filter operator '" + name + "' cannot be evaluated against a payload: " + refused
            );
        }

        if (wrongKind != null)
        {
            return new QLiveException("Filter operator '" + name + "' " + wrongKind);
        }

        return new QLiveException("Invalid filter operator: " + name);
    }


    private static void checkArity(String name, int expected, int actual)
    {
        if (expected != actual)
        {
            throw new QLiveException(
                "Filter operator '" + name + "' takes " + expected + " operand(s), not " + actual
            );
        }
    }


    /// A condition that does not match unless every one of its operands has a value.
    private static ConditionOp defined(int arity, ConditionImpl impl)
    {
        return new ConditionOp(
            arity,
            operands -> {
                for (Object operand : operands)
                {
                    if (operand == null)
                    {
                        return false;
                    }
                }
                return impl.matches(operands);
            }
        );
    }


    /// An operation that has no value unless every one of its operands has one. The absence travels
    /// outwards until it reaches a comparison, which is where it decides anything.
    private static ValueOp arithmetic(int arity, ValueImpl impl)
    {
        return new ValueOp(
            arity,
            operands -> {
                for (Object operand : operands)
                {
                    if (operand == null)
                    {
                        return null;
                    }
                }
                return impl.apply(operands);
            }
        );
    }


    private static boolean between(Object value, Object low, Object high)
    {
        return compareValues(value, low) >= 0 && compareValues(value, high) <= 0;
    }


    /// Between, with the bounds put the right way round first, which is what "symmetric" means.
    private static boolean betweenSymmetric(Object value, Object a, Object b)
    {
        return compareValues(a, b) <= 0 ? between(value, a, b) : between(value, b, a);
    }


    @SuppressWarnings("unchecked")
    private static int compareValues(Object a, Object b)
    {
        if (a instanceof Number numberA && b instanceof Number numberB)
        {
            return decimal(numberA).compareTo(decimal(numberB));
        }

        if (a instanceof Comparable<?> && a.getClass().isInstance(b))
        {
            return ((Comparable<Object>) a).compareTo(b);
        }

        throw new QLiveException("Cannot order a " + a.getClass().getName() + " against a " + b.getClass().getName());
    }


    private static Object divide(Object a, Object b)
    {
        final BigDecimal divisor = decimal(b);

        if (divisor.signum() == 0)
        {
            throw new QLiveException("Division by zero in a filter condition");
        }

        return decimal(a).divide(divisor, MathContext.DECIMAL128);
    }


    private static Object power(Object a, Object b)
    {
        final BigInteger exponent = integer(b);

        if (exponent.signum() < 0)
        {
            throw new QLiveException("Negative exponent in a filter condition: " + exponent);
        }

        return decimal(a).pow(exponent.intValueExact());
    }


    private static BigDecimal decimal(Object value)
    {
        return switch (value)
        {
            case BigDecimal decimal -> decimal;
            case BigInteger integer -> new BigDecimal(integer);
            case Double d -> BigDecimal.valueOf(d);
            case Float f -> BigDecimal.valueOf(f);
            case Number number -> BigDecimal.valueOf(number.longValue());
            default -> throw new QLiveException("Not a number: " + value);
        };
    }


    private static BigInteger integer(Object value)
    {
        return switch (value)
        {
            case BigInteger integer -> integer;
            case BigDecimal decimal -> decimal.toBigIntegerExact();
            case Double ignored -> throw new QLiveException("Bit operations need a whole number: " + value);
            case Float ignored -> throw new QLiveException("Bit operations need a whole number: " + value);
            case Number number -> BigInteger.valueOf(number.longValue());
            default -> throw new QLiveException("Not a number: " + value);
        };
    }


    private static String text(Object value)
    {
        return value instanceof String string ? string : String.valueOf(value);
    }


    private static String lower(Object value)
    {
        return text(value).toLowerCase();
    }
}
