import {GenericScalar} from "../GraphQL";
import {getConverter} from "../converter";

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


/**
 * The scalar types whose values are numbers, whatever form a value of them takes: a number, a bigint, or a string
 * holding a decimal, as a BigDecimal arrives.
 */
const NUMERIC_TYPES = new Set(["Int", "Short", "Byte", "Long", "Float", "BigInteger", "BigDecimal"])

/**
 * Whether the scalar type is a number type, `Int`, `Long`, `BigDecimal` and so on.
 *
 * @internal
 */
export function isNumericType(type: string): boolean
{
    return NUMERIC_TYPES.has(type)
}

/**
 * A number as sign, digits and exponent: `"-1.50"` is `{negative: true, digits: "15", exponent: -1}`. The digits
 * carry no leading or trailing zeros, and zero has none at all.
 */
interface Decimal
{
    negative: boolean
    digits: string
    exponent: number
}

const DECIMAL = /^([+-]?)(\d*)(?:\.(\d*))?(?:[eE]([+-]?\d+))?$/

function decimalOf(value: number | bigint | string): Decimal
{
    const match = DECIMAL.exec(String(value).trim())
    if (!match || (match[2] === "" && !match[3]))
    {
        throw new Error("Not a number: " + JSON.stringify(String(value)))
    }
    const [, sign, whole, fraction = "", exponent = "0"] = match
    let digits = (whole + fraction).replace(/^0+/, "")
    let shift = Number(exponent) - fraction.length
    const trailing = digits.length - digits.replace(/0+$/, "").length
    digits = digits.substring(0, digits.length - trailing)
    shift += trailing
    return {negative: sign === "-" && digits !== "", digits, exponent: digits === "" ? 0 : shift}
}

/**
 * Orders two magnitudes, the digits ignoring the sign.
 */
function compareMagnitude(a: Decimal, b: Decimal): number
{
    if (a.digits === "" || b.digits === "")
    {
        return a.digits.length - b.digits.length
    }
    const size = (a.digits.length + a.exponent) - (b.digits.length + b.exponent)
    if (size !== 0)
    {
        return size
    }
    const length = Math.max(a.digits.length, b.digits.length)
    const digitsA = a.digits.padEnd(length, "0")
    const digitsB = b.digits.padEnd(length, "0")
    return digitsA < digitsB ? -1 : digitsA > digitsB ? 1 : 0
}

/**
 * Orders two numbers by value, whatever form each of them takes: numbers, bigints and decimal strings compare
 * exactly against each other, the way the server compares a `Long` with a `BigDecimal`.
 *
 * @internal
 */
export function compareNumbers(a: number | bigint | string, b: number | bigint | string): number
{
    if (typeof a === "number" && typeof b === "number")
    {
        return a < b ? -1 : a > b ? 1 : 0
    }
    const decimalA = decimalOf(a)
    const decimalB = decimalOf(b)
    if (decimalA.negative !== decimalB.negative)
    {
        return decimalA.negative ? -1 : 1
    }
    const magnitude = Math.sign(compareMagnitude(decimalA, decimalB))
    return decimalA.negative ? -magnitude : magnitude
}

function isNumberLike(value: unknown): value is number | bigint | string
{
    return typeof value === "number" || typeof value === "bigint" || typeof value === "string"
}

/**
 * Orders text the way a user reads it, which is the closest the browser gets to a database's collation.
 */
const collator = new Intl.Collator()

/**
 * Orders two values of a scalar type: negative if the first comes first, positive if the second does, 0 for the same
 * value. What a condition evaluated in the browser compares by, and a local sort orders by.
 *
 * - A type whose converter has a `compare()` is ordered by it, see Converter. QLive registers one for Timestamp.
 * - Numbers of the number types compare by value across their forms, see isNumericType().
 * - `String` and `ID` values follow the user's locale, so `"a" < "B"`. A database orders by its collation, which the
 *   browser doesn't know; the locale is the nearest guess, and where they differ a server sort and a local one
 *   differ too. Text of other types, a `Date` among them, compares by character code, which orders ISO dates right.
 * - Booleans order `false` first.
 *
 * Nulls aren't values here; a caller decides where they go.
 *
 * @param type  GraphQL scalar type name the values are of
 * @param a     one value, in the live form of its type
 * @param b     the other
 *
 * @throws Error for values that type has no order for
 */
export function scalarCompare(type: string, a: unknown, b: unknown): number
{
    const compare = getConverter(type)?.compare
    if (compare)
    {
        return compare(a, b, type)
    }
    if (isNumericType(type) && isNumberLike(a) && isNumberLike(b) ||
        (typeof a === "number" || typeof a === "bigint") && (typeof b === "number" || typeof b === "bigint"))
    {
        return compareNumbers(a as number | bigint | string, b as number | bigint | string)
    }
    if (typeof a === "string" && typeof b === "string")
    {
        return type === "String" || type === "ID" ? collator.compare(a, b) : a < b ? -1 : a > b ? 1 : 0
    }
    if (typeof a === "boolean" && typeof b === "boolean")
    {
        return Number(a) - Number(b)
    }
    throw new Error(
        "Cannot order values of " + type + " (" + typeof a + ", " + typeof b + "). Register a converter with a " +
        "compare() for the type."
    )
}
