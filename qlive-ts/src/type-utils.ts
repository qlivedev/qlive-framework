import {GraphQLNamedTypeRef, GraphQLType, GraphQLTypeRef} from "./GraphQLSchema";
import config from "./config";

export const LIST = "LIST"
export const NON_NULL = "NON_NULL"

/**
 * Unwraps all LIST and NON_NULL modifiers off the given type reference and
 * returns the named type at the heart of it.
 *
 * @param type      GraphQL type reference
 * @returns the named type reference wrapped by the modifiers
 */
export function unwrapAll(type: GraphQLTypeRef): GraphQLNamedTypeRef
{
    if (type.kind === NON_NULL || type.kind === LIST)
    {
        if (!type.ofType)
        {
            // only happens with more modifiers on one type than the
            // introspection query nests levels
            throw new Error("Type reference is truncated, cannot unwrap " + type.kind)
        }
        return unwrapAll(type.ofType);
    }
    return type;
}

/**
 * Unwraps a NON_NULL modifier off the given type reference, if there is one.
 *
 * @param type      GraphQL type reference
 * @returns the wrapped type, or the type itself if it is nullable
 */
export function unwrapNonNull(type: GraphQLTypeRef): GraphQLTypeRef
{
    if (type.kind === NON_NULL)
    {
        return type.ofType ?? type;
    }
    return type;
}

/**
 * Returns true if the given type reference is a list or a non-null list.
 *
 * @param type      GraphQL type reference
 * @returns true if list
 */
export function isListType(type: GraphQLTypeRef): boolean
{
    return unwrapNonNull(type).kind === LIST;
}

/**
 * Returns true if the given type reference is non-null, i.e. a value of that
 * type is guaranteed to be present.
 *
 * @param type      GraphQL type reference
 * @returns true if non-null
 */
export function isNonNull(type: GraphQLTypeRef): boolean
{
    return type.kind === NON_NULL;
}

export function findType(name: string) : GraphQLType
{
    const { schema, meta } = config()

    const type = schema.types.find(t => t.name === name);
    if (!type)
    {
        throw new Error(`Unable to find type "${name}"`);
    }
    return type;
}
