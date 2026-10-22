package io.github.qlivedev.qlive.graphql.util;

import com.google.common.collect.ImmutableMap;
import io.github.qlivedev.qlive.api.json.JSONUtil;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class JSONHolderTest
{
    @Test
    public void testJSONHolder()
    {
        {
            final String json = jsonifyMap(new JSONHolder("\"blabla\""));
            assertThat(json,is("{\"value\":\"blabla\"}"));
        }
        {
            final String json = jsonifyMap(new JSONHolder(ImmutableMap.of("foo", 1)));
            assertThat(json,is("{\"value\":{\"foo\":1}}"));
        }
    }


    private String jsonifyMap(JSONHolder holder)
    {
        final ImmutableMap<String, JSONHolder> map = ImmutableMap.of("value", holder);
        return JSONUtil.DEFAULT_GENERATOR.forValue(map);
    }


}
