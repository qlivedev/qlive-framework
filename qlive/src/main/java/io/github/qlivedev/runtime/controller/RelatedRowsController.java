package io.github.qlivedev.runtime.controller;

import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.TableLookup;
import io.github.qlivedev.graphql.TypeRegistry;
import io.github.qlivedev.graphql.config.RelationModel;
import io.github.qlivedev.graphql.meta.DomainMeta;
import io.github.qlivedev.graphql.meta.DomainTypeMeta;
import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.runtime.meta.CatalogMeta;
import io.github.qlivedev.runtime.query.QueryDocumentService;
import io.github.qlivedev.runtime.query.TypeRows;
import io.github.qlivedev.runtime.util.Util;
import io.github.qlivedev.util.JSONUtil;
import graphql.GraphQL;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/// Serves the rows a picker offers: the rows of a type a foreign key references, as a client's query config
/// narrows them. That includes the foreign keys of many-to-many link types, so a row set picks the rows to
/// associate here as well.
///
/// A picker of a derived form knows the type it picks from but has no query for it, and the schema has no
/// query per type to give it one. So this answers like a query document query without being one, through
/// {@link TypeRows}: it selects what a picker shows and sets, runs the config through the
/// {@link QueryDocumentService} like any other, and writes each value the way the GraphQL scalar owning it
/// would.
///
/// What a picker shows and sets is the fields foreign keys reference and the type's name fields
/// ({@link DomainMeta#NAME_FIELDS}), and nothing else. Not every scalar field: a type is a foreign key's
/// target because rows point at it, which says nothing about whether all of it is for everyone to read -- a
/// user has a password hash. The config filters and sorts by these fields only.
///
/// Only the targets of foreign keys are served, which is the whole of what pickers need, and not the
/// catalog types among them ({@link CatalogMeta}), whose rows a client already has. The type's query
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
    /// @throws QLiveException for a type that is no foreign key's target, a catalog type, or a config that is
    ///                        no config
    public Map<String, Object> relatedRows(Map<String, Object> body)
    {
        final Object typeName = body.get("type");
        if (!(typeName instanceof String type))
        {
            throw new QLiveException("A related rows request names its type as a string: " + body.get("type"));
        }

        final TypeRegistry types = domain.getTypeRegistry();
        final boolean referenced = foreignKeys(types).anyMatch(relation -> relation.getTargetType().equals(type));
        final TableLookup lookup = types.lookupType(type);
        if (!referenced || lookup == null)
        {
            throw new QLiveException(
                "No foreign key references '" + type + "'. Related rows are served for the targets of foreign " +
                    "keys only, which is what a picker offers."
            );
        }

        if (CatalogMeta.isCatalog(domain, type))
        {
            throw new QLiveException(
                "'" + type + "' is a catalog type. Its rows are in the bootstrap, and a picker filters them " +
                    "in the browser rather than asking for them."
            );
        }

        return TypeRows.read(domain, queryDocumentService, graphQL, type, pickerFields(type, types), body.get("config"));
    }


    /// The fields foreign keys reference, then the name fields, each once.
    private List<String> pickerFields(String type, TypeRegistry types)
    {
        final Set<String> fields = new LinkedHashSet<>();
        foreignKeys(types)
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


    /// Every foreign key of the domain: the relations, and those of the many-to-many link types, which a
    /// many-to-many field stands in for rather than lists among the relations.
    private static Stream<RelationModel> foreignKeys(TypeRegistry types)
    {
        return Stream.concat(
            types.getRelationModels().stream(),
            types.getManyToManyModels().stream()
                .flatMap(model -> Stream.of(model.getLeft().getRelation(), model.getRight().getRelation()))
        );
    }
}
