import {GraphQLType, GraphQLTypeRef} from "./GraphQLSchema";
import {findType, isQueryDocumentType, LIST, NON_NULL} from "./type-utils";
import {QueryDocument} from "./QueryDocument";
import config from "./config";
import {Temporal} from "./temporal";
import {GenericScalar} from "./GraphQL";
import i18n from "./i18n";

/**
 * Converts a value between its wire format and its live JavaScript form. Never
 * called with null or undefined: a null converts to null, and an input value the
 * schema declares non-null is rejected before any conversion, so a converter only
 * ever deals with actual values.
 *
 * @param value     value of the GraphQL type the converter is registered for
 * @param type      GraphQL type name the converter was invoked for. Handed in so
 *                  that one function can serve a family of types, e.g. all the
 *                  types derived from QueryDocument<T>.
 */
export type ConversionFn<I, O> = (value: I, type: string) => O

/**
 * Conversion of one GraphQL named type between its wire format and its live
 * JavaScript representation.
 *
 * The two directions are separate members instead of two positional arguments so
 * that a converter reads as what it is at the registration site and can grow
 * further members without breaking existing registrations.
 */
export type Converter<Wire = any, Live = any> = {
    /**
     * Converts the JSON value received from the server into a live value. Used on
     * query results, which arrive along a conversion map.
     */
    fromServer: ConversionFn<Wire, Live>

    /**
     * Converts a live value into the JSON format the server expects. Used on the
     * variables of a query, which are converted along the schema.
     *
     * Optional: a type that only ever appears in results needs none, and values of
     * it are passed through unchanged on the way out.
     */
    toServer?: ConversionFn<Live, Wire>

    /**
     * Turns a live value into the text a user reads, e.g. in a grid cell. Never
     * called with null or undefined, which display as nothing.
     *
     * Optional: a value of a type without one displays as String(value).
     */
    format?: (value: Live, type: string) => string

    /**
     * Orders two live values: negative if the first comes first, positive if the
     * second does, 0 for the same value. Never called with null or undefined. What
     * a condition evaluated in the browser (`lt`, `between`, ...) and a local sort
     * order values of the type by, see scalarCompare().
     *
     * Optional: numbers, strings and Booleans order without one. A type whose live
     * values are objects needs one to be ordered at all.
     */
    compare?: (a: Live, b: Live, type: string) => number

    /**
     * Turns a live value into the text an input edits, e.g. `2026-10-19` for a
     * date input. Not the same as format(): what a user reads and what an input
     * takes differ for a Timestamp, and a Currency amount reads as `1.15` either
     * way but is held as `11500`. Never called with null or undefined, which edit
     * as an empty input.
     *
     * Optional along with fromInput(): a type without either has no input, see
     * inputText().
     */
    toInput?: (value: Live, type: string) => string

    /**
     * Turns what the user typed back into a live value. Called with the text
     * trimmed and never with blank text, which is null. Throws an Error whose
     * message the user can read where the text is no value of the type.
     */
    fromInput?: (text: string, type: string) => Live
}

const converterRegistry: { [type: string]: Converter } = {}

/**
 * Registers a converter for the given GraphQL named type.
 *
 * Any named type can have one -- scalars are the common case (a Timestamp is an
 * ISO-8601 string on the wire and a Temporal.Instant in the application), but object
 * types work just as well, which is how the QueryDocument<T> derived types become
 * QueryDocument instances.
 *
 * Registering a second converter for a type replaces the first, so applications can
 * override the converters QLive brings along.
 *
 * @param type          GraphQL type name, e.g. "Timestamp"
 * @param converter     converter for that type
 */
export function registerConverter<Wire, Live>(type: string, converter: Converter<Wire, Live>): void
{
    converterRegistry[type] = converter as Converter
}

/**
 * Returns the converter registered for the given GraphQL type name, if any.
 *
 * @param type      GraphQL type name
 *
 * @returns converter or null
 */
export function getConverter(type: string): Converter | null
{
    return converterRegistry[type] ?? null
}

/**
 * The text a user reads for a live value of the given GraphQL type: nothing for null or undefined, what the
 * type's converter formats it as where it has a format(), String(value) otherwise.
 *
 * @param value     live value, as in a query result
 * @param type      GraphQL type name of the value, e.g. "Timestamp"
 *
 * @returns display text
 */
export function formatValue(value: unknown, type: string): string
{
    if (value === null || value === undefined)
    {
        return ""
    }
    const format = converterRegistry[type]?.format
    return format ? format(value, type) : String(value)
}

/**
 * A type's way between live values and input text: its converter's, or a built-in
 * one for the scalars whose live value is their wire value.
 */
type InputConversion = {
    toInput: (value: any, type: string) => string
    fromInput: (text: string, type: string) => unknown
}

/**
 * The text types, whose text is the value as it is, blank included.
 */
const TEXT_TYPES = new Set(["String", "ID"])

const INTEGER = /^[+-]?\d+$/

/**
 * Number.MAX_SAFE_INTEGER as a bigint, the largest a Number or Currency holds exactly.
 */
const MAX_SAFE = BigInt(Number.MAX_SAFE_INTEGER)

/**
 * Parses a whole number in the given range.
 */
function wholeNumber(text: string, min: bigint, max: bigint): bigint
{
    if (!INTEGER.test(text))
    {
        throw new Error(i18n("Not a whole number"))
    }
    const value = BigInt(text)
    if (value < min || value > max)
    {
        throw new Error(i18n("Not between {0} and {1}", String(min), String(max)))
    }
    return value
}

function integerInput(min: bigint, max: bigint): InputConversion
{
    return {
        toInput: value => String(value),
        fromInput: text => Number(wholeNumber(text, min, max))
    }
}

/**
 * The fraction digits a Currency amount holds: it counts 1/10000th units.
 */
const CURRENCY_DIGITS = 4

const DECIMAL_TEXT = /^([+-]?)(\d*)(?:\.(\d*))?$/

const BUILT_IN_INPUT: { [type: string]: InputConversion } = {
    String: {toInput: value => value, fromInput: text => text},
    ID: {toInput: value => value, fromInput: text => text},
    Boolean: {
        toInput: value => String(value),
        fromInput: text => {
            if (text !== "true" && text !== "false")
            {
                throw new Error(i18n("Not true or false"))
            }
            return text === "true"
        }
    },
    Byte: integerInput(-128n, 127n),
    Short: integerInput(-32768n, 32767n),
    Int: integerInput(-2147483648n, 2147483647n),
    Number: integerInput(-MAX_SAFE, MAX_SAFE),
    Float: {
        toInput: value => String(value),
        fromInput: text => {
            const value = Number(text)
            if (!Number.isFinite(value))
            {
                throw new Error(i18n("Not a number"))
            }
            return value
        }
    },
    Currency: {
        // whole units with at least two decimals, the way an amount is written: 11500 is "1.15", 10000 "1.00"
        toInput: (value: number) => {
            const units = BigInt(value)
            const magnitude = units < 0n ? -units : units
            const scale = 10n ** BigInt(CURRENCY_DIGITS)
            const fraction = String(magnitude % scale).padStart(CURRENCY_DIGITS, "0").replace(/0+$/, "").padEnd(2, "0")
            return (units < 0n ? "-" : "") + String(magnitude / scale) + "." + fraction
        },
        fromInput: text => {
            const match = DECIMAL_TEXT.exec(text)
            if (!match || (match[2] === "" && !match[3]))
            {
                throw new Error(i18n("Not a number"))
            }
            const [, sign, whole, fraction = ""] = match
            if (fraction.length > CURRENCY_DIGITS)
            {
                throw new Error(i18n("At most {0} decimal places", String(CURRENCY_DIGITS)))
            }
            const magnitude = BigInt(whole || "0") * 10n ** BigInt(CURRENCY_DIGITS) +
                BigInt(fraction.padEnd(CURRENCY_DIGITS, "0"))
            if (magnitude > MAX_SAFE)
            {
                throw new Error(i18n("Amount too large"))
            }
            return Number(sign === "-" ? -magnitude : magnitude)
        }
    }
}

function inputConversion(type: string): InputConversion
{
    const converter = converterRegistry[type]
    if (converter?.toInput && converter.fromInput)
    {
        return converter as InputConversion
    }
    const builtIn = BUILT_IN_INPUT[type]
    if (!builtIn)
    {
        throw new Error(
            "No input for " + type + ". Register a converter for it with toInput() and fromInput()."
        )
    }
    return builtIn
}

/**
 * Whether values of the type have an input text, see inputText().
 *
 * @internal
 */
export function hasInput(type: string): boolean
{
    const converter = converterRegistry[type]
    return Boolean(converter?.toInput && converter.fromInput) || type in BUILT_IN_INPUT
}

/**
 * Turns a live value into the text an input edits: its converter's toInput() where it has one, the built-in
 * conversion of String, ID, Boolean, Byte, Short, Int, Number, Float and Currency otherwise. Null and undefined are
 * empty text.
 *
 * @param value     live value, as in a query result or a draft
 * @param type      GraphQL type name of the value, e.g. "Date"
 *
 * @returns input text
 *
 * @throws Error for a type without an input: BigDecimal until a decimal type is chosen, and an application's own
 *         scalar until its converter has toInput() and fromInput()
 */
export function inputText(value: unknown, type: string): string
{
    const conversion = inputConversion(type)
    return value === null || value === undefined ? "" : conversion.toInput(value, type)
}

/**
 * Turns what the user typed into a live value of the type, the reverse of inputText(). Blank text is null, except
 * for a String or an ID, whose text is the value as it is.
 *
 * @param text      input text
 * @param type      GraphQL type name of the value, e.g. "Date"
 *
 * @returns live value
 *
 * @throws Error with a message for the user where the text is no value of the type, and Error for a type without
 *         an input, see inputText()
 */
export function parseInput(text: string, type: string): unknown
{
    const conversion = inputConversion(type)
    if (TEXT_TYPES.has(type) && conversion === BUILT_IN_INPUT[type])
    {
        return text
    }
    const trimmed = text.trim()
    return trimmed === "" ? null : conversion.fromInput(trimmed, type)
}

/**
 * One selection of a conversion map: the type of the value at one position of a
 * query result, plus the nodes of whatever was selected below it.
 *
 * The types are resolved when the map is generated, so converting along a map needs
 * neither the schema nor the query. That is what takes aliases out of the picture:
 * the child nodes are keyed by the key the value actually appears under in the
 * result, and each one names its type outright, so a field selected as
 * "desc: description" converts as the Timestamp, String, ... it is.
 *
 * List modifiers are not represented. A list is recognized by the value being an
 * array and its elements convert against the same node, which covers nested lists
 * without the map having to spell out a depth.
 */
export type SelectionNode = {
    /**
     * GraphQL type name of the value at this position, modifiers stripped
     */
    type: string

    /**
     * Nodes of the selection below this one, keyed by result key. Absent for leaves,
     * which includes scalars whose wire format is an object graph of its own.
     */
    fields?: { [key: string]: SelectionNode }
}

/**
 * Conversion map of one query, as built from its source by buildConversionMap().
 */
export type QueryConversionMap = {
    /**
     * Nodes of the top-level selections, keyed by result key. An injected query has
     * exactly one, a query executed at runtime may have several.
     */
    selections: { [key: string]: SelectionNode }

    /**
     * GraphQL type name per variable of the query, e.g. { config: "QueryConfig" }.
     * Converting the variables needs nothing but the type: input values have neither
     * aliases nor a selection, so the schema describes them completely.
     */
    variables?: { [name: string]: string }
}

/**
 * Converts the fields of a composite result value along a selection node. Keys the
 * map does not mention are carried over untouched -- __typename, and anything the
 * result picked up that the map was not generated from.
 */
function convertNodeFields(value: any, node: SelectionNode): any
{
    const fields = node.fields
    if (!fields || !value || typeof value !== "object")
    {
        return value
    }

    const out: { [name: string]: any } = {...value}
    const keys = Object.keys(fields)
    for (let i = 0; i < keys.length; i++)
    {
        const key = keys[i];
        if (key in out)
        {
            out[key] = convertNode(out[key], fields[key])
        }
    }
    return out
}

/**
 * Converts a result value along a selection node, bottom-up: the fields are
 * converted first, so the converter of the type itself sees converted field values.
 * A QueryDocument gets its rows with live Temporal.Instants already in them.
 */
function convertNode(value: any, node: SelectionNode): any
{
    if (value === undefined)
    {
        // not a value: a key the result does not carry at all
        return value
    }

    if (value === null)
    {
        // a queried value like any other, and nothing a converter could add to
        return null
    }

    if (Array.isArray(value))
    {
        return value.map(v => convertNode(v, node))
    }

    const converted = convertNodeFields(value, node)
    const converter = converterRegistry[node.type]
    return converter ? converter.fromServer(converted, node.type) : converted
}

/**
 * Converts the value of a single selection, which is what an injection delivers.
 *
 * @param value     wire value of the selection
 * @param node      selection node for it
 *
 * @returns converted value
 */
export function convertSelectionFromServer<T = any>(value: any, node: SelectionNode): T
{
    return convertNode(value, node)
}

/**
 * Converts a complete GraphQL result, whose keys are the top-level selections of the
 * query. Result keys the map does not mention are passed through unconverted.
 *
 * @param result    data of the GraphQL response
 * @param map       conversion map of the query
 *
 * @returns converted result
 */
export function convertResultFromServer<T = any>(result: any, map: QueryConversionMap): T
{
    if (!result || typeof result !== "object")
    {
        return result
    }

    const out: { [name: string]: any } = {...result}
    const keys = Object.keys(map.selections)
    for (let i = 0; i < keys.length; i++)
    {
        const key = keys[i];
        if (key in out)
        {
            out[key] = convertNode(out[key], map.selections[key])
        }
    }
    return out as T
}

/**
 * Converts the fields of an input object, returning a new object. The input is never
 * modified: these are the live objects of the application on their way out.
 */
function convertInputFields(value: any, type: GraphQLType, path: string): any
{
    if (type.kind === "SCALAR" || type.kind === "ENUM")
    {
        // A scalar value can very well be a JSON object graph of its own (Condition,
        // QueryConfig, ...). Those have no fields in the schema and belong to their
        // converter as a whole.
        return value
    }

    if (!value || typeof value !== "object")
    {
        return value
    }

    const out: { [name: string]: any } = {}
    const keys = Object.keys(value)
    for (let i = 0; i < keys.length; i++)
    {
        const key = keys[i];
        const inputField = type.inputFields?.find(f => f.name === key)

        // Input values carry no aliases, so a key the type does not declare is one
        // the server will reject anyway. Passing it on unconverted keeps that error
        // where it belongs, on the server.
        out[key] = inputField
            ? convertInputRef(value[key], inputField.type, path ? path + "." + key : key)
            : value[key]
    }
    return out
}

/**
 * Converts an input value along a type reference out of the schema. The modifiers
 * are only read, never constructed: LIST tells us we have a list of values to
 * convert one by one, NON_NULL rejects a null and is unwrapped to get at the type
 * below it.
 */
function convertInputRef(value: any, type: GraphQLTypeRef, path: string): any
{
    if (value === undefined)
    {
        return value
    }

    if (type.kind === NON_NULL)
    {
        const ofType = type.ofType
        if (!ofType)
        {
            throw new Error("Type reference is truncated, cannot convert " + type.kind)
        }

        if (value === null)
        {
            // catching it here rather than letting the server reject the request
            throw new Error(`Null value for non-null ${ofType.name ?? ofType.kind} at ${path}`)
        }
        return convertInputRef(value, ofType, path)
    }

    if (value === null)
    {
        return null
    }

    if (type.kind === LIST)
    {
        const ofType = type.ofType
        if (!ofType)
        {
            throw new Error("Type reference is truncated, cannot convert " + type.kind)
        }

        return Array.isArray(value)
            ? value.map((v, i) => convertInputRef(v, ofType, path + "[" + i + "]"))
            : convertInputRef(value, ofType, path)
    }

    return convertInput(value, type.name, path)
}

/**
 * Converts a live value into the JSON format the server expects, walking it along
 * its GraphQL type.
 *
 * Top-down, the reverse of the way results are converted: the converter of the type
 * turns the live value into its wire shape first, then that shape's fields are
 * converted.
 *
 * @param value     live value
 * @param type      GraphQL type name of that value, e.g. "FooInput"
 *
 * @returns wire value
 */
export function convertToServer(value: any, type: string): any
{
    return convertInput(value, type, "")
}

/**
 * Converts a live value of the given named type, carrying the path it sits at along
 * for the error messages of the non-null check.
 */
function convertInput(value: any, type: string, path: string): any
{
    if (value === null || value === undefined)
    {
        // whether a null is allowed here is decided by the type reference pointing at
        // this type, which is where the modifiers are
        return value
    }

    const namedType = findType(type)

    if (Array.isArray(value) && namedType.kind !== "SCALAR" && namedType.kind !== "ENUM")
    {
        // A variable is known by its named type alone -- the conversion map drops the
        // modifiers the same way a selection node does -- so a list is recognized by
        // the value being an array, which covers nested lists without a declared depth.
        // A scalar is exempt: its value may be an array in its own right, and it
        // belongs to its converter whole.
        return value.map((v, i) => convertInput(v, type, path + "[" + i + "]"))
    }

    const converter = converterRegistry[type]
    const wire = converter?.toServer ? converter.toServer(value, type) : value

    return convertInputFields(wire, namedType, path)
}

/**
 * Converts the variables of a query into their wire format. Only the variables the
 * query declares are converted, along their declared type -- everything else is
 * passed on as it is.
 *
 * @param variables     variable values
 * @param map           conversion map of the query
 *
 * @returns variables in wire format
 */
export function convertVariablesToServer(
    variables: { [name: string]: any },
    map: QueryConversionMap
): { [name: string]: any }
{
    const types = map.variables
    if (!types)
    {
        return variables
    }

    const out: { [name: string]: any } = {...variables}
    const names = Object.keys(types)
    for (let i = 0; i < names.length; i++)
    {
        const name = names[i];
        if (name in out)
        {
            out[name] = convertInput(out[name], types[name], name)
        }
    }
    return out
}

/**
 * Timestamps are ISO-8601 instants on the wire, and display in the user's time zone and locale.
 */
registerConverter<string, Temporal.Instant>(
    "Timestamp",
    {
        fromServer: value => Temporal.Instant.from(value),
        toServer: value => value.toString(),
        format: value => value.toLocaleString(),
        compare: (a, b) => Temporal.Instant.compare(a, b),

        // as a datetime-local input takes it, in the user's time zone
        toInput: value => value.toZonedDateTimeISO(Temporal.Now.timeZoneId()).toPlainDateTime().toString(),
        fromInput: text => {
            try
            {
                return Temporal.PlainDateTime.from(text, {overflow: "reject"})
                    .toZonedDateTime(Temporal.Now.timeZoneId())
                    .toInstant()
            }
            catch (e)
            {
                throw new Error(i18n("Not a date and time"))
            }
        }
    }
)

/**
 * Longs and BigIntegers travel as decimal strings, since a JSON number past 2^53 is rounded before anything can convert
 * it, and are bigints in the application. A plain number is accepted on the way out.
 */
const MAX_LONG = 2n ** 63n - 1n

registerConverter<string, bigint>(
    "Long",
    {
        fromServer: value => BigInt(value),
        toServer: value => value.toString(),
        toInput: value => value.toString(),
        fromInput: text => wholeNumber(text, -MAX_LONG - 1n, MAX_LONG)
    }
)

registerConverter<string, bigint>(
    "BigInteger",
    {
        fromServer: value => BigInt(value),
        toServer: value => value.toString(),
        toInput: value => value.toString(),
        fromInput: text => {
            if (!INTEGER.test(text))
            {
                throw new Error(i18n("Not a whole number"))
            }
            return BigInt(text)
        }
    }
)

/**
 * Dates are ISO-8601 calendar dates on the wire, `2026-10-19`, and display in the user's locale.
 */
registerConverter<string, Temporal.PlainDate>(
    "Date",
    {
        fromServer: value => Temporal.PlainDate.from(value),
        toServer: value => value.toString(),
        format: value => value.toLocaleString(),
        compare: (a, b) => Temporal.PlainDate.compare(a, b),
        toInput: value => value.toString(),
        fromInput: text => {
            try
            {
                return Temporal.PlainDate.from(text, {overflow: "reject"})
            }
            catch (e)
            {
                throw new Error(i18n("Not a date"))
            }
        }
    }
)

/**
 * Splits a generic scalar's type name into the named type and whether it was a list of them, which is how
 * the server's coercing writes it: "Timestamp" or "[Timestamp]".
 */
function genericParts(type: string): { name: string, list: boolean }
{
    return type.startsWith("[") && type.endsWith("]")
        ? {name: type.substring(1, type.length - 1), list: true}
        : {name: type, list: false}
}


/**
 * Converts the value inside a generic scalar with the given per-value conversion, one element at a time
 * where the type name says the value is a list.
 *
 * The list is spelled out here rather than left to the array handling the rest of the module has: that
 * handling exists because a list modifier is not in the map, while here the value names its own type
 * outright -- and a scalar whose value is an array in its own right must not be taken apart.
 */
function convertGeneric(value: any, type: string, convert: (value: any, name: string) => any): any
{
    if (value === null || value === undefined)
    {
        return value
    }

    const {name, list} = genericParts(type)

    return list && Array.isArray(value) ? value.map(v => convert(v, name)) : convert(value, name)
}


/**
 * A generic scalar is a value of a type the schema does not say -- the value names it itself, which is what
 * lets one field accept every scalar the domain has. So it converts in two steps: this converter unpacks the
 * wrapper, and the type name it finds inside is what the value below converts along.
 *
 * The wrapper looks the same on both sides. What changes is the value in it, which is in the live form of
 * its type here and in the wire form of it on the way out, like every other value of that type.
 */
registerConverter<GenericScalar, GenericScalar>(
    "GenericScalar",
    {
        fromServer: wire => ({
            type: wire.type,
            value: convertGeneric(wire.value, wire.type, (v, name) => convertNode(v, {type: name}))
        }),
        toServer: live => ({
            type: live.type,
            value: convertGeneric(live.value, live.type, (v, name) => convertInput(v, name, ""))
        })
    }
)


/**
 * Converter shared by all the GraphQL types derived from QueryDocument<T>, e.g.
 * FooDocument. They are plain JSON objects on the wire and QueryDocument instances
 * in the application.
 *
 * One direction only: a document type is an output type, so a document never travels
 * to the server as a value. Sending one back means sending its config as a variable.
 */
const queryDocumentConverter: Converter<any, QueryDocument<any>> = {
    fromServer: doc => new QueryDocument(doc.type, doc.config, doc.rows, doc.rowCount)
}

/**
 * Registers the converters that can only be known once the QLive config is there.
 * Must run after the config is set and before the first conversion.
 *
 * Applications that registered their own converter for one of those types keep it.
 */
export function initConverters(): void
{
    const { queryDocumentTypes } = config()

    for (const type of queryDocumentTypes!)
    {
        if (!converterRegistry[type])
        {
            registerConverter(type, queryDocumentConverter)
        }
    }
}
