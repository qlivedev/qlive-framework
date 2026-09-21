package io.github.qlivedev.graphql.jsonb;

import org.jooq.Converter;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class PGJSONBindingTest
{
    private final Converter<org.jooq.JSONB, JSONB> converter = new PGJSONBinding().converter();


    /**
     * A nullable json column is null often enough, and JOOQ hands a converter that null rather than
     * skipping it.
     */
    @Test
    public void testNullValues()
    {
        assertThat(converter.from(null), is(nullValue()));
        assertThat(converter.to(null), is(nullValue()));
    }


    @Test
    public void testConversion()
    {
        final JSONB value = converter.from(org.jooq.JSONB.valueOf("{\"name\":\"value\"}"));
        assertThat(value.getProperty("name"), is("value"));

        assertThat(converter.to(value).data(), is("{\"name\":\"value\"}"));
    }
}
