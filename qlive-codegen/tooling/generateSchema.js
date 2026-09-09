#!/usr/bin/env node

/*
 * Writes an application's schema.graphql from a running QLive backend.
 *
 * Here to close a gap, not to own the file: an IDE's GraphQL plugin does the same job, and anyone who
 * has one should keep using it. This is for anyone who has not.
 */

import fs from "node:fs"

import {getIntrospectionQuery} from "graphql"

import {schemaSDL} from "./schemaSDL.js"

/** Where a QLive backend answers introspection: unauthenticated, CSRF-exempt, and dev profile only. */
const DEV_GRAPHQL_URI = "/_dev/graphql"


async function generateSchema(origin, file)
{
    const url = origin.replace(/\/+$/, "") + DEV_GRAPHQL_URI

    let response
    try
    {
        response = await fetch(url, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({query: getIntrospectionQuery()})
        })
    }
    catch (e)
    {
        // The one mistake worth naming: everything else arrives as a status.
        throw new Error(`Could not reach ${url} -- is the backend running? (${e.message})`)
    }

    if (!response.ok)
    {
        throw new Error(`${url} answered ${response.status} ${response.statusText}`)
    }

    const {data, errors} = await response.json()
    if (errors?.length)
    {
        throw new Error(url + " refused the introspection query: " + JSON.stringify(errors))
    }

    fs.writeFileSync(file, schemaSDL(data), "utf8")
    console.log(`Wrote ${file}`)
}


if (process.argv.length !== 4)
{
    console.error("Usage: generate-schema <backend-origin> <schema-file>")
    console.error("   e.g. generate-schema http://localhost:8080 schema.graphql")
    process.exit(1)
}

generateSchema(process.argv[2], process.argv[3]).catch(error => {
    console.error(error.message ?? error)
    process.exit(2)
})
