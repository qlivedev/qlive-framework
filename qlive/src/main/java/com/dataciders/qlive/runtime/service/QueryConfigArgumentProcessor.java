package com.dataciders.qlive.runtime.service;

import com.dataciders.qlive.model.QueryConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/// Completes the query configs an injection passes, which arrive as the delta over a default config that
/// they are.
///
/// A call names the fields it cares about and no others -- the same thing QueryConfigDelta is on the client,
/// where update() spreads it over the document's current config. Here there is no current one, so the fields
/// are applied over a fresh {@link QueryConfig}, and what reaches GraphQL is the complete config that
/// config's own defaults describe.
///
/// The numbers are narrowed on the way: these came out of the analysis JSON, where an integer is a Long.
/// Everything else is passed on untouched, so a condition or a sort field written out in the call is still
/// read by the coercing that owns it.
public class QueryConfigArgumentProcessor
    implements InjectionArgumentProcessor
{
    /// GraphQL name of the query config scalar, as {@link com.dataciders.qlive.runtime.domain.QLiveDomain}
    /// registers it.
    public final static String QUERY_CONFIG_TYPE = "QueryConfig";


    @Override
    public boolean handles(String typeName)
    {
        return QUERY_CONFIG_TYPE.equals(typeName);
    }


    @Override
    public Object process(InjectionArgument argument)
    {
        if (!(argument.value() instanceof Map<?, ?> delta))
        {
            // Left to the scalar's own coercing, which is the thing that knows what else a config could
            // have been written as.
            return argument.value();
        }

        final QueryConfig defaults = new QueryConfig();

        final Map<String, Object> config = new LinkedHashMap<>();
        delta.forEach((field, fieldValue) -> config.put(String.valueOf(field), fieldValue));

        config.put("offset", intValue(argument, "offset", delta.get("offset"), defaults.getOffset()));
        config.put(
            "pageSize",
            intValue(argument, "pageSize", delta.get("pageSize"), defaults.getPageSize())
        );

        return config;
    }


    private static int intValue(InjectionArgument argument, String field, Object value, int defaultValue)
    {
        if (value == null)
        {
            return defaultValue;
        }

        if (value instanceof Number number)
        {
            return number.intValue();
        }

        throw argument.reject("non-numeric " + field + " (" + value + ")");
    }
}
