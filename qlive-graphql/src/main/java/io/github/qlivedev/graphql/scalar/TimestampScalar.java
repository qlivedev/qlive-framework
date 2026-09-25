package io.github.qlivedev.graphql.scalar;

import graphql.language.StringValue;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * GraphQL Scalar implementation for java.sql.Timestamp.
 * <p>
 *     Timestamps travel as ISO-8601 instants in UTC, which is what the "Z" in them says and what a client
 *     reading one as an instant assumes.
 * </p>
 */
public class TimestampScalar
{
    public static String NAME = "Timestamp";

    private final static DateTimeFormatter ISO8601 =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);


    private TimestampScalar()
    {
        // no instances
    }


    public static GraphQLScalarType newScalar()
    {
        return GraphQLScalarType.newScalar()
            .name(NAME)
            .description("SQL timestamp equivalent")
            .coercing(new Coercing())
            .build();
    }


    public static class Coercing
        implements graphql.schema.Coercing<Timestamp, String>
    {




        @Override
        public String serialize(Object dataFetcherResult) throws CoercingSerializeException
        {
            if (dataFetcherResult instanceof Timestamp)
            {
                try
                {
                    return toISO8601((Timestamp) dataFetcherResult);
                }
                catch (RuntimeException e)
                {
                    throw new CoercingSerializeException("Error converting " + dataFetcherResult + " to ISO string", e);
                }
            }
            else
            {
                throw new CoercingSerializeException("Could not convert " + dataFetcherResult + " to ISO string");
            }
        }


        @Override
        public Timestamp parseValue(Object input) throws CoercingParseValueException
        {
            if (!(input instanceof String))
            {
                throw new CoercingParseValueException("Cannot coerce " + input + " to Timestamp");
            }


            final String isoString = (String) input;
            return convert(isoString);
        }


        @Override
        public Timestamp parseLiteral(Object input) throws CoercingParseLiteralException
        {
            if (!(input instanceof StringValue))
            {
                throw new CoercingParseValueException("Cannot coerce " + input + " to Timestamp");
            }

            return convert(((StringValue) input).getValue());
        }
    }


    /**
     * Formats a timestamp as the ISO-8601 instant {@link #convert(String)} reads back.
     *
     * @param dataFetcherResult     timestamp
     *
     * @return ISO-8601 instant in UTC
     */
    public static String toISO8601(Timestamp dataFetcherResult)
    {
        return ISO8601.format(dataFetcherResult.toInstant());
    }


    /**
     * Reads an ISO-8601 instant.
     *
     * @param isoString     ISO-8601 instant
     *
     * @return timestamp
     */
    public static Timestamp convert(String isoString)
    {
        Instant instant = Instant.parse(isoString);
        return new Timestamp(instant.toEpochMilli());
    }
}
