import config, {ManyToManyEnd, ManyToManyInfo} from "../config";
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
}

/**
 * A many-to-many field as an edit sees it: the type it is on, the type whose rows it lists, and whether it can be
 * written.
 *
 * Read from the declaration in config().meta.manyToMany. Setting bar.bazes means associating the Bar with other
 * Bazes, which the merge writes as link rows and never as a change to Bar or Baz.
 */
export type ManyToManyField = {

    /**
     * The field, e.g. "bazes" on Bar.
     */
    field: string

    /**
     * The type the field is on, e.g. "Bar".
     */
    sourceType: string

    /**
     * The type whose rows the field lists, e.g. "Baz".
     */
    targetType: string

    /**
     * The link type the associations are rows of, e.g. "BarLink".
     */
    linkType: string

    /**
     * Whether the field can be written. False where a link row needs values of its own besides its two link
     * fields, which makes the link rows the thing to edit.
     */
    writable: boolean
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
 * The many-to-many field of the given type with the given name, or null where that field is none.
 *
 * @param typeName      GraphQL type name, known or not
 * @param field         field name on that type
 */
export function manyToManyField(typeName: string, field: string): ManyToManyField | null
{
    for (const declared of config().meta.manyToMany)
    {
        if (isEnd(declared.left, typeName, field))
        {
            return oriented(declared, declared.left, declared.right)
        }
        if (isEnd(declared.right, typeName, field))
        {
            return oriented(declared, declared.right, declared.left)
        }
    }

    return null
}

/**
 * The many-to-many fields of the given type, in the order they were declared.
 *
 * @param typeName      GraphQL type name, known or not
 */
export function manyToManyFields(typeName: string): ManyToManyField[]
{
    const found: ManyToManyField[] = []

    for (const declared of config().meta.manyToMany)
    {
        for (const [own, other] of [[declared.left, declared.right], [declared.right, declared.left]])
        {
            if (own.type === typeName && own.field)
            {
                found.push(oriented(declared, own, other))
            }
        }
    }

    return found
}

function isEnd(end: ManyToManyEnd, typeName: string, field: string): boolean
{
    return end.type === typeName && end.field === field
}

function oriented(declared: ManyToManyInfo, own: ManyToManyEnd, other: ManyToManyEnd): ManyToManyField
{
    return {
        field: own.field!,
        sourceType: own.type,
        targetType: other.type,
        linkType: declared.linkType,
        writable: declared.writable
    }
}
