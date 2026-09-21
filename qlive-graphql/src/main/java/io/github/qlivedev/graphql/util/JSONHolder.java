package io.github.qlivedev.graphql.util;

import io.github.qlivedev.util.JSONUtil;
import org.svenson.JSONable;

/**
 * Wrapper that uses the JSONable interface to cache the JSONification of a given object value.
 */
public final class JSONHolder
    implements JSONable
{
    private final String json;

    public JSONHolder(Object value)
    {
        this.json = JSONUtil.DEFAULT_GENERATOR.forValue(value);
    }
    public JSONHolder(String json)
    {
        this.json = json;
    }

    @Override
    public String toJSON()
    {
        return json;
    }
}
