#!/usr/bin/env node

/*
 * CLI that regenerates the result type of every GraphQLQuery in an application's source tree.
 *
 * The track-usage plugin does the same thing per save while a dev server runs. This is the way to ask
 * for it: after a schema change, in CI, or from `pnpm generate`. It writes into the source tree and
 * reports what it touched.
 */

import path from "node:path"

import {analyzeSourceTree} from "@qlivedev/qlive-ts/vite"

import {createQueryTypeGenerator} from "./queryTypeGenerator.js"


async function generateQueryTypes(schemaPath, sourceRoot, typesModule)
{
    const generator = await createQueryTypeGenerator({schemaPath, sourceRoot, typesModule})

    // The same babel pass the track-usage plugin runs, so the queries found here are the ones the
    // dev server generates from -- same names, same source offsets, same "must be a literal" rule.
    const {updated, failed} = generator.update(analyzeSourceTree({sourceRoot: path.resolve(sourceRoot)}))

    console.log(
        updated.length
            ? `Updated query result types in:\n${updated.map(m => "    " + m).join("\n")}`
            : "Query result types are up-to-date"
    )

    if (failed.length)
    {
        // Named one by one and then refused: a query that does not fit the schema is exactly what this
        // is run to find out, and a build that carried on would hide it until the next typecheck.
        console.error(
            `\nCould not generate query result types for:\n` +
            failed.map(f => `    ${f.module}: ${f.message}`).join("\n")
        )
        process.exitCode = 2
    }
}


if (process.argv.length !== 4 && process.argv.length !== 5)
{
    console.error("Usage: generate-query-types <schema-file> <source-root> [types-module]")
    process.exit(1)
}

generateQueryTypes(process.argv[2], process.argv[3], process.argv[4]).catch(error => {
    console.error(error.message ?? error)
    process.exit(2)
})
