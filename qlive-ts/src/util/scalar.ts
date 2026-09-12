import {GenericScalar} from "../GraphQL";

/**
 * Whether two values of the same scalar type are the same value.
 *
 * A converted value is an object rather than a primitive -- a Timestamp is a Temporal.Instant -- and two of
 * them holding the same instant are not the same object. What knows they are equal is the value itself, so
 * an equals() is asked wherever there is one.
 *
 * A scalar that converts to a plain object or an array has neither identity nor an equals(), and comes back
 * false here. That is the safe direction for what this is used for -- a value the user typed stays a change
 * rather than being dropped as one -- and the cost is a write and a version for a value that did not move.
 *
 * @param a     one value, in the live form of its type
 * @param b     the other
 */
export function scalarEqual(a: unknown, b: unknown): boolean
{
    if (Object.is(a, b))
    {
        return true
    }

    if (a === null || b === null || typeof a !== "object" || typeof b !== "object")
    {
        return false
    }

    return typeof (a as any).equals === "function" && (a as any).equals(b)
}


/**
 * Whether two scalars are the same type carrying the same value.
 *
 * The type names have to agree: "Int" 1 and "Float" 1 are two values a domain distinguishes, and what the
 * server coerces the value along is the name. Two nulls are the same value, one null is not.
 *
 * @param a     one scalar, or null
 * @param b     the other
 */
export function genericScalarEqual(a: GenericScalar | null, b: GenericScalar | null): boolean
{
    if (a === null || b === null)
    {
        return a === b
    }

    return a.type === b.type && scalarEqual(a.value, b.value)
}
