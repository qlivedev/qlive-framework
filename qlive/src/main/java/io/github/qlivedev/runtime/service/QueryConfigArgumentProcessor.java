package io.github.qlivedev.runtime.service;

import io.github.qlivedev.model.QueryConfig;
import io.github.qlivedev.runtime.meta.QueryConfigMeta;
import de.quinscape.domainql.DomainQL;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLTypeUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/// Completes the query configs an injection passes, which arrive as the delta over a default config that
/// they are.
///
/// A call names the fields it cares about and no others -- the same thing QueryConfigDelta is on the client,
/// where update() spreads it over the document's current config. Here there is no current one, so the config
/// is assembled from what there is, most general first:
///
///  1. the defaults a fresh {@link QueryConfig} describes,
///  2. the delta the type being queried declares, if it declares one -- see {@link QueryConfigMeta},
///  3. the delta the `useInjection()` call itself names.
///
/// Which type that is comes from the query rather than from the config: the variable is passed to a field
/// returning a query document, and that document's rows are what is being queried. The first of the fields
/// the variable is used at whose rows declare a delta is the one that counts, which for the one field a
/// config is normally passed to is simply that field.
///
/// The numbers are narrowed on the way: these came out of the analysis JSON, where an integer is a Long.
/// Everything else is passed on untouched, so a condition or a sort field written out in the call is still
/// read by the coercing that owns it.
public class QueryConfigArgumentProcessor
    implements InjectionArgumentProcessor
{
    /// GraphQL name of the query config scalar, as {@link io.github.qlivedev.runtime.domain.QLiveDomain}
    /// registers it.
    public final static String QUERY_CONFIG_TYPE = "QueryConfig";

    private final DomainQL domainQL;


    /// @param domainQL  the domain, which is what carries the per-type deltas as meta data
    public QueryConfigArgumentProcessor(DomainQL domainQL)
    {
        this.domainQL = domainQL;
    }


    @Override
    public boolean handles(String typeName)
    {
        return QUERY_CONFIG_TYPE.equals(typeName);
    }


    @Override
    public Object process(InjectionArgument argument)
    {
        final Map<?, ?> delta;

        if (argument.value() == null)
        {
            // The call named no config at all, which is the normal case: what the type declares is the
            // whole of what it asks for, and an empty delta is how that is said here.
            delta = Map.of();
        }
        else if (argument.value() instanceof Map<?, ?> named)
        {
            delta = named;
        }
        else
        {
            // Left to the scalar's own coercing, which is the thing that knows what else a config could
            // have been written as. Completing it here would have to guess at a shape it does not know.
            return argument.value();
        }

        final QueryConfig defaults = new QueryConfig();

        final Map<String, Object> config = new LinkedHashMap<>();

        final Map<String, Object> declared = declaredDelta(argument);
        if (declared != null)
        {
            config.putAll(declared);
        }

        delta.forEach((field, fieldValue) -> config.put(String.valueOf(field), fieldValue));

        config.put("offset", intValue(argument, "offset", config.get("offset"), defaults.getOffset()));
        config.put(
            "pageSize",
            intValue(argument, "pageSize", config.get("pageSize"), defaults.getPageSize())
        );

        return config;
    }


    /// The delta declared for what this argument queries, or `null` where nothing declares one.
    private Map<String, Object> declaredDelta(InjectionArgument argument)
    {
        for (GraphQLFieldDefinition field : argument.usedAt())
        {
            final Map<String, Object> delta = QueryConfigMeta.deltaForDocumentType(
                domainQL,
                GraphQLTypeUtil.unwrapAll(field.getType()).getName()
            );

            if (delta != null)
            {
                return delta;
            }
        }

        return null;
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
