package io.github.qlivedev.graphql.scalar;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class NumberScalarTest
{
    private final Coercing<?, ?> coercing = NumberScalar.newScalar().getCoercing();


    @Test
    public void testSerializesSafeIntegersAsNumbers()
    {
        assertThat(coercing.serialize(123L), is(123L));
        assertThat(coercing.serialize(9007199254740991L), is(9007199254740991L));
        assertThat(coercing.serialize(-9007199254740991L), is(-9007199254740991L));
    }


    /**
     * The browser would round it, so it is an error rather than a different number.
     */
    @Test
    public void testRefusesWhatJavaScriptRounds()
    {
        assertThrows(CoercingSerializeException.class, () -> coercing.serialize(9007199254740992L));
        assertThrows(CoercingParseValueException.class, () -> coercing.parseValue(-9007199254740992L));
    }


    @Test
    public void testRefusesFractionsAndText()
    {
        assertThrows(CoercingParseValueException.class, () -> coercing.parseValue(1.5));
        assertThrows(CoercingParseValueException.class, () -> coercing.parseValue("123"));
    }
}
