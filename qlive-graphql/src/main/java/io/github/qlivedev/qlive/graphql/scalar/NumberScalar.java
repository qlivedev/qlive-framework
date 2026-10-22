package io.github.qlivedev.qlive.graphql.scalar;

import graphql.language.IntValue;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * A long integer that travels as a JSON number and is a JavaScript number in the browser, for a field whose values are
 * known to stay between Number.MIN_SAFE_INTEGER and Number.MAX_SAFE_INTEGER -- a count, a size, a position. Selected by
 * annotating the field with {@code @GraphQLField(type = "Number")}; a long without one is a {@link LongScalar}.
 *
 * A value outside that range is an error in both directions rather than a number the browser rounds.
 */
public class NumberScalar
{
    /**
     * Number.MAX_SAFE_INTEGER, the largest integer a JavaScript number holds exactly.
     */
    public static final long MAX_SAFE_INTEGER = (1L << 53) - 1;

    /**
     * Number.MIN_SAFE_INTEGER
     */
    public static final long MIN_SAFE_INTEGER = -MAX_SAFE_INTEGER;


    private NumberScalar()
    {
        // no instances
    }


    public static GraphQLScalarType newScalar()
    {
        return GraphQLScalarType.newScalar()
            .name("Number")
            .description("64-bit integer restricted to the range a JavaScript number holds exactly")
            .coercing(new Coercing())
            .build();
    }


    /**
     * Whether a JavaScript number holds the value exactly.
     *
     * @param value     value
     *
     * @return true if it is between MIN_SAFE_INTEGER and MAX_SAFE_INTEGER
     */
    public static boolean isSafe(long value)
    {
        return value >= MIN_SAFE_INTEGER && value <= MAX_SAFE_INTEGER;
    }


    public static class Coercing
        implements graphql.schema.Coercing<Long, Long>
    {
        /**
         * The value as a long, or null where it is no integer a long holds.
         */
        private Long toLong(Object input)
        {
            if (input instanceof Long || input instanceof Integer || input instanceof Short || input instanceof Byte)
            {
                return ((Number) input).longValue();
            }
            if (input instanceof Number)
            {
                try
                {
                    return new BigDecimal(input.toString()).longValueExact();
                }
                catch (ArithmeticException | NumberFormatException e)
                {
                    return null;
                }
            }
            return null;
        }


        @Override
        public Long serialize(Object dataFetcherResult) throws CoercingSerializeException
        {
            final Long value = toLong(dataFetcherResult);
            if (value == null || !isSafe(value))
            {
                throw new CoercingSerializeException(
                    "Cannot serialize " + dataFetcherResult + " as Number: not an integer a JavaScript number holds " +
                        "exactly. Use Long for the full range."
                );
            }
            return value;
        }


        @Override
        public Long parseValue(Object input) throws CoercingParseValueException
        {
            final Long value = toLong(input);
            if (value == null || !isSafe(value))
            {
                throw new CoercingParseValueException(
                    "Cannot coerce " + input + " to Number: not an integer a JavaScript number holds exactly."
                );
            }
            return value;
        }


        @Override
        public Long parseLiteral(Object input) throws CoercingParseLiteralException
        {
            if (!(input instanceof IntValue))
            {
                throw new CoercingParseLiteralException("Cannot coerce " + input + " to Number");
            }
            final BigInteger value = ((IntValue) input).getValue();
            if (value.bitLength() > 63 || !isSafe(value.longValue()))
            {
                throw new CoercingParseLiteralException(
                    "Cannot coerce " + value + " to Number: not an integer a JavaScript number holds exactly."
                );
            }
            return value.longValue();
        }
    }
}
