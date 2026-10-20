package io.github.qlivedev.runtime.service;

import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.runtime.meta.CatalogMeta;
import io.github.qlivedev.runtime.query.QueryDocumentService;
import io.github.qlivedev.runtime.query.TypeRows;
import graphql.GraphQL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Reads the rows of every catalog type ({@link CatalogMeta}), once, for the bootstrap to ship.
public final class Catalogs
{
    private final static Logger log = LoggerFactory.getLogger(Catalogs.class);

    private Catalogs()
    {
        // no instances
    }


    /// Every row of every catalog type, by type name, each row with every scalar and enum field of its type
    /// and written the way a GraphQL response writes it. The type's query config delta applies, so its sort
    /// fields order the rows; without any they come in primary key order.
    ///
    /// @throws QLiveException for a catalog type whose maximum page size holds back some of its rows: a
    ///                        catalog that isn't whole would offer a value as missing that the database has
    public static Map<String, List<Map<String, Object>>> load(
        QLiveDomain domain,
        QueryDocumentService queryDocumentService,
        GraphQL graphQL
    )
    {
        final Map<String, List<Map<String, Object>>> catalogs = new LinkedHashMap<>();
        for (String type : CatalogMeta.catalogTypes(domain))
        {
            final Map<String, Object> answer = TypeRows.read(
                domain,
                queryDocumentService,
                graphQL,
                type,
                TypeRows.scalarFields(graphQL, type),
                Map.of("offset", 0, "pageSize", 0)
            );

            @SuppressWarnings("unchecked")
            final List<Map<String, Object>> rows = (List<Map<String, Object>>) answer.get("rows");
            final int rowCount = (Integer) answer.get("rowCount");
            if (rows.size() < rowCount)
            {
                throw new QLiveException(
                    "Catalog type '" + type + "' has " + rowCount + " rows, more than its maximum page size of " +
                        rows.size() + ". A catalog ships whole: raise the maximum for it, or don't make it a " +
                        "catalog."
                );
            }

            log.info("Catalog {}: {} rows", type, rows.size());
            catalogs.put(type, rows);
        }
        return catalogs;
    }
}
