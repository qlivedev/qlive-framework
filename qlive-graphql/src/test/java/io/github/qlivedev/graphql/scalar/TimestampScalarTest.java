package io.github.qlivedev.graphql.scalar;

import graphql.schema.GraphQLScalarType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.TimeZone;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class TimestampScalarTest
{
    private final static String ISO = "2018-11-01T19:58:59.000Z";

    private final static Timestamp TIMESTAMP = Timestamp.from(Instant.parse(ISO));


    @AfterEach
    public void reset()
    {
        TimestampScalar.setLegacy(false);
    }


    /**
     * What goes out is what comes back. Parsing has always read an ISO instant as UTC, so serializing has
     * to write one, or a timestamp gains the offset of whatever zone the server happens to run in.
     */
    @Test
    public void testRoundTrip()
    {
        final GraphQLScalarType scalar = TimestampScalar.newScalar();

        assertThat(scalar.getCoercing().serialize(TIMESTAMP), is(ISO));
        assertThat(scalar.getCoercing().parseValue(ISO), is(TIMESTAMP));
        assertThat(
            scalar.getCoercing().parseValue(scalar.getCoercing().serialize(TIMESTAMP)),
            is(TIMESTAMP)
        );
    }


    /**
     * The result does not depend on where the server is.
     */
    @Test
    public void testTimeZoneIndependence()
    {
        final TimeZone original = TimeZone.getDefault();
        try
        {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            assertThat(TimestampScalar.toISO8601(TIMESTAMP), is(ISO));

            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            assertThat(TimestampScalar.toISO8601(TIMESTAMP), is(ISO));
        }
        finally
        {
            TimeZone.setDefault(original);
        }
    }


    /**
     * The old behaviour is still there for an application that compensates for it elsewhere: the local time
     * of the JVM's zone, labelled "Z" whether it is one or not.
     */
    @Test
    public void testLegacyBehaviour()
    {
        TimestampScalar.setLegacy(true);

        final TimeZone original = TimeZone.getDefault();
        try
        {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));

            assertThat(TimestampScalar.toISO8601(TIMESTAMP), is("2018-11-01T20:58:59.000Z"));
            assertThat(TimestampScalar.toISO8601(TIMESTAMP), is(not(ISO)));

            // and parsing is unchanged, which is what made the two disagree
            assertThat(TimestampScalar.convert(ISO), is(TIMESTAMP));
        }
        finally
        {
            TimeZone.setDefault(original);
        }
    }
}
