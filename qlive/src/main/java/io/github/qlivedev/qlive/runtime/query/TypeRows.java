package io.github.qlivedev.qlive.runtime.query;

import io.github.qlivedev.qlive.graphql.QLiveDomain;
import io.github.qlivedev.qlive.graphql.TableLookup;
import io.github.qlivedev.qlive.model.QueryConfig;
import io.github.qlivedev.qlive.model.QueryDocument;
import io.github.qlivedev.qlive.runtime.QLiveException;
import io.github.qlivedev.qlive.runtime.meta.QueryConfigMeta;
import io.github.qlivedev.qlive.runtime.service.QueryConfigArgumentProcessor;
import io.github.qlivedev.qlive.api.json.JSONUtil;
import graphql.GraphQL;
import graphql.GraphQLContext;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// Reads rows of a table-backed type outside of a GraphQL query, written the way a GraphQL response
/// writes them: what the related-rows endpoint answers with and what a catalog ships as.
///
/// The fields are given rather than selected, and the config runs through the {@link QueryDocumentService}
/// like any other, the type's query config delta under it and its maximum page size over it. Each value is
/// written by the GraphQL scalar or enum type owning it, so the client reads the rows with the converters
/// it reads a GraphQL response with.
public final class TypeRows
{
    private TypeRows()
    {
        // no instances
    }


    /// The rows of a type a config finds, as `{ type, rows, rowCount, config }`, the config the one that
    /// was applied.
    ///
    /// @param type     name of a table-backed type
    /// @param fields   the fields of each row, scalar or enum fields of the type
    /// @param given    a QueryConfig delta in its wire format, or null
    ///
    /// @throws QLiveException for a config that is no config
    public static Map<String, Object> read(
        QLiveDomain domain,
        QueryDocumentService queryDocumentService,
        GraphQL graphQL,
        String type,
        List<String> fields,
        Object given
    )
    {
        final TableLookup lookup = domain.getTypeRegistry().lookupType(type);
        final GraphQLSchema schema = graphQL.getGraphQLSchema();
        final GraphQLScalarType configType = (GraphQLScalarType) schema.getType(
            QueryConfigArgumentProcessor.QUERY_CONFIG_TYPE
        );

        final QueryConfig config = parseConfig(domain, configType, type, lookup.getPojoType(), given);

        final GraphQLObjectType objectType = schema.getObjectType(type);
        final QueryDocument<?> document = queryDocumentService.buildQuery(lookup.getPojoType(), fields, config)
            .execute();

        final List<Map<String, Object>> rows = new ArrayList<>(document.getRows().size());
        for (Object row : document.getRows())
        {
            final Map<String, Object> written = new LinkedHashMap<>();
            for (String field : fields)
            {
                written.put(
                    field,
                    serialize(
                        GraphQLTypeUtil.unwrapAll(objectType.getFieldDefinition(field).getType()),
                        JSONUtil.DEFAULT_UTIL.getProperty(row, field)
                    )
                );
            }
            rows.add(written);
        }

        final Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("type", type);
        answer.put("rows", rows);
        answer.put("rowCount", document.getRowCount());
        answer.put(
            "config",
            configType.getCoercing().serialize(document.getConfig(), GraphQLContext.getDefault(), Locale.ROOT)
        );
        return answer;
    }


    /// The scalar and enum fields of a type, in schema order: every field a row of it can be written with.
    public static List<String> scalarFields(GraphQL graphQL, String type)
    {
        return graphQL.getGraphQLSchema().getObjectType(type).getFieldDefinitions().stream()
            .filter(field -> {
                final GraphQLNamedType fieldType = (GraphQLNamedType) GraphQLTypeUtil.unwrapAll(field.getType());
                return fieldType instanceof GraphQLScalarType || fieldType instanceof GraphQLEnumType;
            })
            .map(field -> field.getName())
            .toList();
    }


    /// The given config over the type's delta over the defaults, read by the QueryConfig scalar.
    private static QueryConfig parseConfig(
        QLiveDomain domain,
        GraphQLScalarType configType,
        String type,
        Class<?> pojoType,
        Object given
    )
    {
        if (given != null && !(given instanceof Map))
        {
            throw new QLiveException("A query config for " + type + " is an object: " + given);
        }

        final QueryConfig defaults = new QueryConfig();
        final Map<String, Object> config = new LinkedHashMap<>();
        config.put("offset", defaults.getOffset());
        config.put("pageSize", defaults.getPageSize());

        final Map<String, Object> declared = QueryConfigMeta.deltaForType(domain, pojoType);
        if (declared != null)
        {
            config.putAll(declared);
        }
        if (given != null)
        {
            ((Map<?, ?>) given).forEach((key, value) -> config.put(String.valueOf(key), value));
        }

        try
        {
            return (QueryConfig) configType.getCoercing().parseValue(config, GraphQLContext.getDefault(), Locale.ROOT);
        }
        catch (RuntimeException e)
        {
            throw new QLiveException("Invalid query config for " + type + ": " + e.getMessage(), e);
        }
    }


    /// A value as the scalar or enum type owning it writes it into a GraphQL response.
    private static Object serialize(GraphQLNamedType type, Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (type instanceof GraphQLScalarType scalar)
        {
            return scalar.getCoercing().serialize(value, GraphQLContext.getDefault(), Locale.ROOT);
        }
        return ((GraphQLEnumType) type).serialize(value, GraphQLContext.getDefault(), Locale.ROOT);
    }
}
