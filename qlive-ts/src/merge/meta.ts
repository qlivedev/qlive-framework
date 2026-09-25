import config, {RelationInfo} from "../config";
import {GraphQLObjectType} from "../GraphQLSchema";

/**
 * Name of the field that makes a type versioned, and which holds the id of the app_version record describing
 * the state the row is in now.
 */
export const VERSION = "version"

/**
 * What a type declares about merging it, written server-side by QLive's MergeMetadataProvider and read back
 * through the functions below. Reached as config().meta.types[name].meta.merge, which is where
 * DomainTypeMetaProps declares it.
 *
 * Which types take part is not in here and cannot be -- that is the "version" field, see isVersioned().
 * What a provider declares is only the part that is genuinely the application's decision.
 */
export type MergeTypeMeta = {

    /**
     * true if a conflict on this type comes back to the view with both values per field to resolve, rather
     * than failing the write. Absent where the type did not ask for it, which is the default.
     */
    resolve?: boolean

    /**
     * false if a concurrent change overlapping none of the user's own fields should still be shown to them
     * instead of merging silently. Absent means true.
     */
    autoMerge?: boolean

    /**
     * Fields whose change is neither recorded nor ever a conflict, alphabetically. Absent where the type
     * declared none.
     */
    ignoredFields?: string[]

    /**
     * true if the type is a link table that carries fields of its own and is therefore not recognisable by
     * its shape. Absent for a link table of the plain shape, which isLinkType() sees anyway.
     */
    linkType?: boolean
}

/**
 * A many-to-many relation as an edit sees it: one list field on a type, the link type its rows are, and the
 * two foreign keys of a link row.
 *
 * Derived from config().meta.relations rather than declared anywhere. Editing bar.bazLinks means inserting
 * and deleting BarLink rows and never touching Baz, which is what the GraphQL type of the field already
 * says; this is that statement in the form a working set needs it in.
 */
export type LinkRelation = {

    /**
     * The list field on the source type, e.g. "bazLinks" on Bar.
     */
    field: string

    /**
     * The type the field is on, e.g. "Bar".
     */
    sourceType: string

    /**
     * The type of the rows in the field, e.g. "BarLink".
     */
    linkType: string

    /**
     * The field of a link row holding the foreign key back to the source, e.g. "barId".
     */
    sourceField: string

    /**
     * The type on the other side of the link, e.g. "Baz".
     */
    targetType: string

    /**
     * The field of a link row holding the foreign key to the other side, e.g. "bazId".
     */
    targetField: string

    /**
     * The field of a link row holding the row on the other side, e.g. "baz". Absent where the relation
     * generated none.
     *
     * What lets a new association be written as the row it is about -- `[...bar.bazLinks, {baz}]` -- rather
     * than as the foreign key alone. It is also the object a view renders the association through, so the
     * short form is the one that both diffs and displays.
     */
    targetObject?: string
}

/**
 * Whether rows of the given type take part in conflict detection, i.e. whether the type has a "version"
 * field.
 *
 * Nothing declares this and nothing can: the field is the declaration, and the server derives it from the
 * same schema, so the two ends cannot disagree about who takes part. A type without one is written
 * last-write-wins, which is what not having the column means.
 *
 * @param typeName      GraphQL type name, known or not
 */
export function isVersioned(typeName: string): boolean
{
    const type = config().typesByName!.get(typeName)

    return !!type && type.kind === "OBJECT" && type.fields.some(f => f.name === VERSION)
}

/**
 * Names of every versioned type in the schema, alphabetically.
 */
export function versionedTypes(): string[]
{
    return config().schema.types
        .filter(t => t.kind === "OBJECT" && (t as GraphQLObjectType).fields.some(f => f.name === VERSION))
        .map(t => t.name)
        .sort()
}

/**
 * Everything the given type declared about merging it. Empty where the type declared nothing, where it is no
 * type of the schema, or where the application registered no provider at all -- which are the same answer to
 * every reader below.
 *
 * @param typeName      GraphQL type name, known or not
 */
export function metaOf(typeName: string): MergeTypeMeta
{
    return config().meta.types[typeName]?.meta?.merge ?? {}
}

/**
 * Whether the type asked for conflicts to come back to the view with both values per field, rather than
 * failing the write. Declared, because handing a user two values and asking them to choose is a decision
 * about the application's UI.
 *
 * @param typeName      GraphQL type name, known or not
 */
export function resolvesConflicts(typeName: string): boolean
{
    return metaOf(typeName).resolve === true
}

/**
 * Whether a concurrent change that touched none of the fields the user touched is merged without asking.
 * True unless the type said otherwise: that case is what the whole mechanism exists to swallow.
 *
 * @param typeName      GraphQL type name, known or not
 */
export function isAutoMerge(typeName: string): boolean
{
    return metaOf(typeName).autoMerge !== false
}

/**
 * Fields of the type whose change is neither recorded nor ever a conflict, alphabetically. Empty where the
 * type declared none.
 *
 * @param typeName      GraphQL type name, known or not
 */
export function ignoredFields(typeName: string): string[]
{
    return metaOf(typeName).ignoredFields ?? []
}

/**
 * Whether the type is a link table, i.e. whether its rows exist to say two entities are associated.
 *
 * True for a type of the plain link shape -- an id, an optional version and nothing but the two foreign keys
 * -- and for one the application declared with MergeMetadataProvider#linkType, which is how a link table
 * that carries fields of its own says so.
 *
 * @param typeName      GraphQL type name, known or not
 */
export function isLinkType(typeName: string): boolean
{
    return relationsFrom(typeName).length === 2 &&
        (metaOf(typeName).linkType === true || hasOnlyLinkFields(typeName))
}

/**
 * The many-to-many relations of the given type, in the order the relation meta data lists them.
 *
 * @param typeName      GraphQL type name, known or not
 */
export function linkRelations(typeName: string): LinkRelation[]
{
    const found: LinkRelation[] = []

    for (const relation of config().meta.relations)
    {
        const link = asLinkRelation(typeName, relation)
        if (link)
        {
            found.push(link)
        }
    }

    return found
}

/**
 * The many-to-many relation the given field of the given type is, or null if that field is no link array.
 *
 * @param typeName      GraphQL type name, known or not
 * @param field         field name on that type
 */
export function linkRelation(typeName: string, field: string): LinkRelation | null
{
    return linkRelations(typeName).find(link => link.field === field) ?? null
}

/**
 * The relation as a link relation of the given type, or null where it is none: it has to point at that type
 * from the many side, name a field there, and come from a link type.
 */
function asLinkRelation(typeName: string, relation: RelationInfo): LinkRelation | null
{
    if (
        relation.targetType !== typeName ||
        relation.targetField !== "MANY" ||
        !relation.rightSideObjectName ||
        !isLinkType(relation.sourceType)
    )
    {
        return null
    }

    // isLinkType() saw two relations out of the link type, and this is the one that is not us
    const other = relationsFrom(relation.sourceType).find(r => r !== relation)
    if (!other)
    {
        // both foreign keys of the link point back at us, so there is no other side to resolve to
        return null
    }

    return {
        field: relation.rightSideObjectName,
        sourceType: typeName,
        linkType: relation.sourceType,
        sourceField: relation.sourceFields[0],
        targetType: other.targetType,
        targetField: other.sourceFields[0],
        targetObject: other.leftSideObjectName
    }
}

/**
 * The relations leading out of the given type, i.e. the ones its own foreign keys make.
 */
function relationsFrom(typeName: string): RelationInfo[]
{
    return config().meta.relations.filter(r => r.sourceType === typeName)
}

/**
 * Whether the type holds nothing but its id, its version and the two foreign keys of its relations -- the
 * shape of a row that exists only to say two entities are associated.
 *
 * A field beyond those is something two users could disagree about, so a type carrying one is not recognized
 * here and has to be declared instead.
 */
function hasOnlyLinkFields(typeName: string): boolean
{
    const type = config().typesByName!.get(typeName)
    if (!type || type.kind !== "OBJECT")
    {
        return false
    }

    const allowed = new Set<string>(["id", VERSION])

    for (const relation of relationsFrom(typeName))
    {
        relation.sourceFields.forEach(f => allowed.add(f))

        if (relation.leftSideObjectName)
        {
            allowed.add(relation.leftSideObjectName)
        }
    }

    return type.fields.every(f => allowed.has(f.name))
}
