import {GraphQLType, GraphQLTypeRef} from "./GraphQLSchema";
import {findType, isQueryDocumentType, LIST, NON_NULL} from "./type-utils";
import {QueryDocument} from "./QueryDocument";
import config from "./config";
import {Temporal} from "temporal-polyfill";

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
 * One selection of a conversion map: the type of the value at one position of a
 * query result, plus the nodes of whatever was selected below it.
 *
 * The types are resolved when the map is generated, so converting along a map needs
 * neither the schema nor the query. That is what takes aliases out of the picture:
 * the child nodes are keyed by the key the value actually appears under in the
 * result, and each one names its type outright, so a field selected as
 * "desc: description" converts as the Timestamp, String, ... it is.
 *
 * List modifiers are not represented. A list is recognised by the value being an
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
 * Conversion map of one query, as generated from the query source.
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

    const converter = converterRegistry[type]
    const wire = converter?.toServer ? converter.toServer(value, type) : value

    return convertInputFields(wire, findType(type), path)
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
 * Timestamps are ISO-8601 instants on the wire.
 */
registerConverter<string, Temporal.Instant>(
    "Timestamp",
    {
        fromServer: value => Temporal.Instant.from(value),
        toServer: value => value.toString()
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
