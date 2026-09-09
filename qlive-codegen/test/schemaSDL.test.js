import fs from "node:fs"
import path from "node:path"
import {fileURLToPath} from "node:url"
import {describe, expect, it} from "vitest"

import {buildSchema, introspectionFromSchema, printSchema} from "graphql"

import {schemaSDL} from "../tooling/schemaSDL.js"

/*
 * The generator is a fetch and this round trip. What can go wrong is the round trip, and introspection
 * data is all it takes to exercise it -- there is nothing about an HTTP hop worth standing a server up
 * for.
 */

const packageDir = path.dirname(fileURLToPath(new URL("../package.json", import.meta.url)))
const schema = buildSchema(fs.readFileSync(path.join(packageDir, "test", "query-fixture", "schema.graphql"), "utf8"))


describe("schemaSDL", () => {

    it("prints back the schema the introspection describes", () => {
        const printed = schemaSDL(introspectionFromSchema(schema))

        expect(printSchema(buildSchema(printed))).toBe(printSchema(schema))
    })


    it("keeps the descriptions, which types.d.ts carries into the application", () => {
        expect(schemaSDL(introspectionFromSchema(schema)))
            .toContain("Generated for com.dataciders.qlive.model.QueryDocument<TestFoo>")
    })


    it("reads a blank description as none, the way graphql-java reports one", () => {
        const introspection = introspectionFromSchema(schema)

        for (const type of introspection.__schema.types)
        {
            for (const field of type.fields ?? [])
            {
                field.description = field.description ?? ""
            }
        }

        // six quotes: an empty block string, which is what a "" description prints as
        expect(schemaSDL(introspection)).not.toContain('"'.repeat(6))
    })
})
