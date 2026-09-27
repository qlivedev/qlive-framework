import {buildClientSchema, printSchema} from "graphql"


/**
 * Prints the SDL of an introspection result, which is graphql's own round trip and nothing besides.
 *
 * No sorting and no header: the file belongs to whoever generates it, and imposing a shape on it would
 * only put this tool at odds with the IDE plugin that does the same job.
 *
 * @param {Object} introspection  the "data" of an introspection query, i.e. { __schema: ... }
 *
 * @returns {string} SDL
 */
export function schemaSDL(introspection)
{
    return printSchema(buildClientSchema(withoutBlankDescriptions(introspection)))
}


/**
 * Reads a blank description as no description.
 *
 * graphql-java answers "" for a field nothing documents, where the spec's null says the same thing.
 * Printed as it arrives, that is an empty """""" block above every undocumented field.
 *
 * Only the "description" property is touched. A field *named* description is the value of a "name"
 * key, which this never looks at.
 */
function withoutBlankDescriptions(introspection)
{
    return JSON.parse(
        JSON.stringify(introspection),
        (key, value) => key === "description" && typeof value === "string" && !value.trim() ? null : value
    )
}
