package io.github.qlivedev.graphql.scalar;

import graphql.language.StringValue;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
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

    /**
     * System property giving {@link #isLegacy()} its initial value.
     */
    public final static String LEGACY_PROPERTY = "domainql.timestamp.legacy";

    private final static DateTimeFormatter ISO8601 =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private static volatile boolean legacy = Boolean.getBoolean(LEGACY_PROPERTY);

    private TimestampScalar()
    {
        // no instances
    }


    /**
     * Whether timestamps are serialized the way they were before this was fixed: formatted in the JVM's
     * default time zone and labelled "Z" regardless, so that a timestamp gained the UTC offset every time
     * it went out and came back.
     * <p>
     *     Off by default. An application that has been compensating for the old behaviour somewhere else --
     *     in its client, or in data it has already written -- turns it back on rather than being moved
     *     under its feet by an upgrade.
     * </p>
     * <p>
     *     Only serializing ever differed. Parsing has always read an ISO instant as the UTC it says it is,
     *     which is why the two did not agree.
     * </p>
     *
     * @return true if timestamps are serialized in the default time zone
     */
    public static boolean isLegacy()
    {
        return legacy;
    }


    /**
     * Sets the legacy flag. Initialized from the {@link #LEGACY_PROPERTY} system property.
     *
     * @param legacy    true to serialize timestamps in the JVM's default time zone
     */
    public static void setLegacy(boolean legacy)
    {
        TimestampScalar.legacy = legacy;
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
     * @return ISO-8601 instant in UTC, or in the JVM's default time zone if {@link #isLegacy()}
     */
    public static String toISO8601(Timestamp dataFetcherResult)
    {
        if (legacy)
        {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            return df.format(dataFetcherResult);
        }

        return ISO8601.format(dataFetcherResult.toInstant());
    }


    /**
     * Reads an ISO-8601 instant. Always UTC, whatever {@link #isLegacy()} says.
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
