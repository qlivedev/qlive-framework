import {objectFields} from "../type-utils";

/**
 * The bit each field of a type owns in a field mask: every field of the GraphQL type, alphabetically, the
 * position being the bit.
 *
 * The mirror of FieldLayout.of(QLiveDomain, String) on the Java side, and it has to stay one -- a mask is
 * positions, and two ends reading it under different orders name different fields, confidently and
 * silently. The order is the schema's own field list sorted, which both ends derive from the same schema
 * rather than agreeing on separately.
 *
 * A version record names the layout it was written under, and nothing here checks it: a pushed record was
 * written by the server this page's schema came from, moments ago. A client old enough to disagree about a
 * type's fields is a stale deployment, which breaks a great deal more than a mask.
 *
 * @param type      GraphQL type name
 *
 * @returns the field names in bit order
 */
export function fieldOrder(type: string): string[]
{
    return objectFields(type).map(field => field.name).sort()
}


/**
 * The mask naming the given fields of the given type.
 *
 * A name the type does not have is left out rather than refused, the same way the Java side leaves it out:
 * a caller naming fields is not in a position to know what the layout has.
 *
 * @param type      GraphQL type name
 * @param fields    field names
 *
 * @returns the mask, zero where none of the names is a field of the type
 */
export function maskOf(type: string, fields: Iterable<string>): bigint
{
    const order = fieldOrder(type)

    let mask = 0n

    for (const name of fields)
    {
        const index = order.indexOf(name)

        if (index >= 0)
        {
            mask |= 1n << BigInt(index)
        }
    }

    return mask
}


/**
 * The fields the given mask names, in bit order.
 *
 * Bits past the end of the field list are ignored -- they belong to fields this client does not have, and
 * one it cannot name is one it cannot show.
 *
 * @param type      GraphQL type name
 * @param mask      field mask
 *
 * @returns the field names
 */
export function maskedFields(type: string, mask: bigint): string[]
{
    const order = fieldOrder(type)
    const named: string[] = []

    for (let i = 0; i < order.length; i++)
    {
        if ((mask >> BigInt(i)) & 1n)
        {
            named.push(order[i])
        }
    }

    return named
}
