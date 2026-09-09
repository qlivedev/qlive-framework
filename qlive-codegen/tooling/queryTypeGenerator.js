/*
 * The query result type generation, wrapped so that a long-running process can hold on to it.
 *
 * Loading and building the schema is the expensive half and does not change while a dev server runs,
 * so the track-usage plugin creates one of these once and calls update() per editing round. The CLI
 * creates one, calls update() once and exits.
 */

import fs from "node:fs"
import path from "node:path"

import {GraphQLFileLoader} from "@graphql-tools/graphql-file-loader"
import {loadSchema} from "@graphql-tools/load"

import {DEFAULT_TYPES_MODULE, updateGraphQLQueryTypes} from "./queryTypes.js"


/**
 * Builds the generator for one application.
 *
 * @param {Object} options
 * @param {string} options.schemaPath   GraphQL schema the queries are checked against
 * @param {string} options.sourceRoot   directory the module ids of an analysis are relative to
 * @param {string} [options.typesModule] module the generated domain types live in, relative to the
 *                                       source root and without extension. Default: "types"
 *
 * @returns {Promise<{update: (analysis: Object) => Object}>} generator whose update() takes a
 * track-usage analysis -- whole or a slice of changed modules -- and reports what it rewrote
 */
export async function createQueryTypeGenerator({schemaPath, sourceRoot, typesModule = DEFAULT_TYPES_MODULE})
{
    if (!fs.existsSync(schemaPath))
    {
        throw new Error("Could not find schema " + schemaPath)
    }
    if (!fs.existsSync(sourceRoot))
    {
        throw new Error("Could not find source root " + sourceRoot)
    }

    const schema = await loadSchema(schemaPath, {loaders: [new GraphQLFileLoader()]})
    const resolvedRoot = path.resolve(sourceRoot)

    return {
        update: analysis => updateGraphQLQueryTypes(schema, analysis, resolvedRoot, typesModule)
    }
}
