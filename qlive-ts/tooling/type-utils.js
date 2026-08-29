
export function unwrapAll(type) {
    if (type.kind === NON_NULL || type.kind === LIST)
    {
        return unwrapAll(type.ofType);
    }
    return type;
}

export function unwrapNonNull(type)
{
    if (type.kind === NON_NULL)
    {
        return type.ofType;
    }
    return type;
}

export const LIST = "LIST"
export const NON_NULL = "NON_NULL"
/**
 * Returns true if the given type definition is a list or non-null list.
 *
 * @param {Object} type     GraphQL type definition
 * @returns {boolean}   true if list
 */
export function isListType(type)
{
    return unwrapNonNull(type).kind === LIST;
}
