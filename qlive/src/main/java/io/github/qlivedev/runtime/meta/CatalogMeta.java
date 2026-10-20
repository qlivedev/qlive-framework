package io.github.qlivedev.runtime.meta;

import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.meta.DomainTypeMeta;
import io.github.qlivedev.runtime.util.Util;

import java.util.List;

/// The type meta data entry marking a catalog type, and how it is read back.
///
/// A catalog type is an enum bound at application start rather than at compile time: a table whose rows
/// are constant for the lifetime of an application run, a lookup table usually. Its rows are read once at
/// startup and ship in the cached part of every client's bootstrap, so a picker filters them in the
/// browser and never asks the server.
///
/// That is also what it costs: every row of every catalog type is in every page that gets the schema, and
/// everyone who can load such a page can read them. A constant table can still be too large, or too
/// private, to be a catalog.
///
/// Written with {@link CatalogMetadataProvider}.
public final class CatalogMeta
{
    private CatalogMeta()
    {
        // no instances
    }

    /// Name of the type meta data property marking a catalog type. Has to agree with `DomainTypeMetaProps`
    /// on the client, which declares the same name to TypeScript.
    public final static String CATALOG = "catalog";


    /// Whether the type of the given name is a catalog type.
    public static boolean isCatalog(QLiveDomain domain, String typeName)
    {
        final DomainTypeMeta typeMeta = Util.typeMeta(domain, typeName);
        return typeMeta != null && Boolean.TRUE.equals(typeMeta.getMeta(CATALOG));
    }


    /// The names of the catalog types, sorted.
    public static List<String> catalogTypes(QLiveDomain domain)
    {
        return domain.getTypeRegistry().getJooqTables().keySet().stream()
            .filter(typeName -> isCatalog(domain, typeName))
            .sorted()
            .toList();
    }
}
