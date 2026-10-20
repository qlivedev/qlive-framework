package io.github.qlivedev.runtime.controller;

import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.TableLookup;
import io.github.qlivedev.graphql.TypeRegistry;
import io.github.qlivedev.graphql.meta.DomainMeta;
import io.github.qlivedev.graphql.meta.DomainTypeMeta;
import io.github.qlivedev.model.QueryConfig;
import io.github.qlivedev.model.QueryDocument;
import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.runtime.meta.QueryConfigMeta;
import io.github.qlivedev.runtime.query.QueryDocumentService;
import io.github.qlivedev.runtime.service.QueryConfigArgumentProcessor;
import io.github.qlivedev.runtime.util.Util;
import io.github.qlivedev.util.JSONUtil;
import graphql.GraphQL;
import graphql.GraphQLContext;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/// Serves the rows a to-one picker offers: the rows of a type a foreign key references, as a client's query
/// config narrows them.
///
/// A picker of a derived form knows the type it picks from but has no query for it, and the schema has no
/// query per type to give it one. So this answers like a query document query without being one: it selects
/// what a picker shows and sets, runs the config through the {@link QueryDocumentService} like any other,
/// and writes each value the way the GraphQL scalar owning it would. The client reads the answer with the
/// same converters it reads a GraphQL response with.
///
/// What a picker shows and sets is the fields foreign keys reference and the type's name fields
/// ({@link DomainMeta#NAME_FIELDS}), and nothing else. Not every scalar field: a type is a foreign key's
/// target because rows point at it, which says nothing about whether all of it is for everyone to read -- a
/// user has a password hash. The config filters and sorts by these fields only.
///
/// Only the targets of foreign keys are served, which is the whole of what pickers need. The type's query
/// config delta applies under the client's config, as it does for an injection; the page is held to the
/// type's maximum page size, as for every query document query.
///
/// The request is `{ type, config }`, the config a QueryConfig delta in its wire format; the answer is
/// `{ type, rows, rowCount, config }`, the config the one that was applied.
@Controller
public class RelatedRowsController
{
    private final static Logger log = LoggerFactory.getLogger(RelatedRowsController.class);

    /// Where related rows are served. An ordinary authenticated URI, protected against CSRF like /graphql.
    public final static String RELATED_ROWS_URI = "/api/related-rows";

    private final QLiveDomain domain;

    private final QueryDocumentService queryDocumentService;

    private final GraphQL graphQL;


    public RelatedRowsController(
        QLiveDomain domain,
        @Lazy QueryDocumentService queryDocumentService,
        @Lazy GraphQL graphQL
    )
    {
        this.domain = domain;
        this.queryDocumentService = queryDocumentService;
        this.graphQL = graphQL;
    }


    @RequestMapping(
        value = RELATED_ROWS_URI,
        method = RequestMethod.POST,
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<String> serveRelatedRows(@RequestBody Map<String, Object> body)
    {
        try
        {
            return new ResponseEntity<>(JSONUtil.DEFAULT_GENERATOR.forValue(relatedRows(body)), HttpStatus.OK);
        }
        catch (QLiveException e)
        {
            log.warn("Refused related rows request: {}", e.getMessage());
            return new ResponseEntity<>(
                JSONUtil.DEFAULT_GENERATOR.forValue(Map.of("error", e.getMessage())),
                HttpStatus.BAD_REQUEST
            );
        }
    }


    /// The answer to one request, as the map that is written out.
    ///
    /// @param body     the request, `{ type, config }`
    ///
    /// @throws QLiveException for a type that is no foreign key's target, or a config that is no config
    public Map<String, Object> relatedRows(Map<String, Object> body)
    {
        final Object typeName = body.get("type");
        if (!(typeName instanceof String type))
        {
            throw new QLiveException("A related rows request names its type as a string: " + body.get("type"));
        }

        final TypeRegistry types = domain.getTypeRegistry();
        final boolean referenced = types.getRelationModels().stream()
            .anyMatch(relation -> relation.getTargetType().equals(type));
        final TableLookup lookup = types.lookupType(type);
        if (!referenced || lookup == null)
        {
            throw new QLiveException(
                "No foreign key references '" + type + "'. Related rows are served for the targets of foreign " +
                    "keys only, which is what a picker offers."
            );
        }

        final GraphQLSchema schema = graphQL.getGraphQLSchema();
        final GraphQLScalarType configType = (GraphQLScalarType) schema.getType(
            QueryConfigArgumentProcessor.QUERY_CONFIG_TYPE
        );

        final QueryConfig config = parseConfig(configType, lookup.getPojoType(), body.get("config"));

        final GraphQLObjectType objectType = schema.getObjectType(type);
        final List<String> fields = pickerFields(type, types);

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


    /// The client's config over the type's delta over the defaults, read by the QueryConfig scalar.
    private QueryConfig parseConfig(GraphQLScalarType configType, Class<?> pojoType, Object given)
    {
        if (given != null && !(given instanceof Map))
        {
            throw new QLiveException("A related rows request's config is an object: " + given);
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
            throw new QLiveException("Invalid query config in a related rows request: " + e.getMessage(), e);
        }
    }


    /// The fields foreign keys reference, then the name fields, each once.
    private List<String> pickerFields(String type, TypeRegistry types)
    {
        final Set<String> fields = new LinkedHashSet<>();
        types.getRelationModels().stream()
            .filter(relation -> relation.getTargetType().equals(type))
            .forEach(relation -> fields.addAll(relation.getTargetFields()));

        final DomainTypeMeta typeMeta = Util.typeMeta(domain, type);
        final List<String> nameFields = typeMeta == null ? null : typeMeta.getMeta(DomainMeta.NAME_FIELDS);
        if (nameFields != null)
        {
            fields.addAll(nameFields);
        }
        return new ArrayList<>(fields);
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
