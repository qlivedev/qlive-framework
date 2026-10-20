/*
 * Catalogs: the rows of the catalog types, shipped whole with the bootstrap.
 */
import config from "./config";
import {convertRow} from "./form/relatedRows";

/** the converted rows by type, per catalogs object, so a new config starts over */
const converted = new WeakMap<object, Map<string, any[]>>()

/**
 * Whether the type is a catalog type, see catalogRows().
 *
 * @param type      a type name, e.g. "FooType"
 */
export function isCatalog(type: string): boolean
{
    return config().meta.types[type]?.meta?.catalog === true
}

/**
 * Every row of a catalog type, with every scalar and enum field, in the order the server read them: the type's sort
 * fields, else its primary key.
 *
 * A catalog type is an enum bound at application start: a table whose rows don't change while the application runs,
 * declared with the server's CatalogMetadataProvider. Its rows come with the bootstrap, so reading them takes no
 * request, and a picker of a relation to it filters them in the browser.
 *
 *     const fooTypes = catalogRows<FooType>("FooType")
 *
 * The rows are shared: treat them as read-only.
 *
 * @param type      the catalog type
 *
 * @throws Error for a type that is no catalog type, or a page whose config came without its rows -- a reduced one,
 *         for a view that declared noSchema()
 */
export function catalogRows<R = any>(type: string): R[]
{
    if (!isCatalog(type))
    {
        throw new Error(type + " is no catalog type. Declare it one with the server's CatalogMetadataProvider.")
    }
    const catalogs = config().catalogs
    const wire = catalogs?.[type]
    if (!catalogs || !wire)
    {
        throw new Error("The config holds no rows of the catalog type " + type + ".")
    }

    let byType = converted.get(catalogs)
    if (!byType)
    {
        byType = new Map()
        converted.set(catalogs, byType)
    }
    let rows = byType.get(type)
    if (!rows)
    {
        rows = wire.map(row => convertRow(type, row))
        byType.set(type, rows)
    }
    return rows as R[]
}
