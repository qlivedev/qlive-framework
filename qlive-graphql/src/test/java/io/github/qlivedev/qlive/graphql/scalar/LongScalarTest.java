package io.github.qlivedev.qlive.graphql.scalar;

import graphql.schema.Coercing;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class LongScalarTest
{
    private final Coercing<?, ?> coercing = LongScalar.newScalar().getCoercing();


    /**
     * Past 2^53 a JSON number is rounded by JSON.parse, so the full range only reaches the browser as text.
     */
    @Test
    public void testSerializesAsString()
    {
        assertThat(coercing.serialize(Long.MAX_VALUE), is("9223372036854775807"));
        assertThat(coercing.serialize(123L), is("123"));
    }


    @Test
    public void testParsesStringAndNumber()
    {
        assertThat(coercing.parseValue("9223372036854775807"), is(Long.MAX_VALUE));
        assertThat(coercing.parseValue(123), is(123L));
    }
}
