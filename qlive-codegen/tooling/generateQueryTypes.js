#!/usr/bin/env node

/*
 * CLI that regenerates the result type of every GraphQLQuery in an application's source tree.
 *
 * The dev backend does this live, but only in the dev profile, so this is how a build -- or anyone
 * without a running backend -- brings the checked-in types back in line with the queries and the
 * schema. It writes into the source tree, exactly like the backend does, and reports what it touched.
 */

import fs from "node:fs"
import path from "node:path"

import {GraphQLFileLoader} from "@graphql-tools/graphql-file-loader"
import {loadSchema} from "@graphql-tools/load"
import {analyzeSourceTree} from "@quinscape/qlive-ts/vite"

import {updateGraphQLQueryTypes} from "./queryTypes.js"


async function generateQueryTypes(schemaPath, sourceRoot)
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

    // The same babel pass the Vite plugin runs, so the queries found here are the ones the dev
    // backend is handed -- same names, same source offsets, same "must be a literal" rule.
    const analysis = analyzeSourceTree({sourceRoot: path.resolve(sourceRoot)})

    const updated = updateGraphQLQueryTypes(schema, analysis, path.resolve(sourceRoot))

    console.log(
        updated.length
            ? `Updated query result types in:\n${updated.map(m => "    " + m).join("\n")}`
            : "Query result types are up-to-date"
    )
}


if (process.argv.length !== 4)
{
    console.error("Usage: generate-query-types <schema-file> <source-root>")
    process.exit(1)
}

generateQueryTypes(process.argv[2], process.argv[3]).catch(error => {
    console.error(error.message ?? error)
    process.exit(2)
})
