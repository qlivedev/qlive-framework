package io.github.qlivedev.graphql.scalar;

import graphql.schema.GraphQLScalarType;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.TimeZone;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TimestampScalarTest
{
    private final static String ISO = "2018-11-01T19:58:59.000Z";

    private final static Timestamp TIMESTAMP = Timestamp.from(Instant.parse(ISO));


    /**
     * What goes out is what comes back. Parsing reads an ISO instant as the UTC it says it is, so serializing
     * writes one, or a timestamp gains the offset of whatever zone the server happens to run in.
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
}
