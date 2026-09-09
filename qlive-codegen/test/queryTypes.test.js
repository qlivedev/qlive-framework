import {execFileSync} from "node:child_process"
import fs from "node:fs"
import path from "node:path"
import {fileURLToPath} from "node:url"
import {describe, expect, it} from "vitest"

import {buildSchema} from "graphql"
import {analyzeSourceTree} from "@quinscape/qlive-ts/vite"

import {
    analyzeGraphQLQuery,
    analyzeModule,
    isQueryDocumentType,
    renderModule,
    renderResultType,
    updateGraphQLQueryTypes,
    withDocumentMethodsImport
} from "../tooling/queryTypes.js"

/*
 * The expectations here are the ones GraphQLQueryTypingServiceTest asserts on the Java side, against
 * the same schema. That is the point of the file: the dev backend and this generator write into the
 * same modules, so a query either renders identically on both sides or every `pnpm generate` fights
 * the last `pnpm dev`.
 */

const packageDir = path.dirname(fileURLToPath(new URL("../package.json", import.meta.url)))
const fixtureDir = path.join(packageDir, "test", "query-fixture")
const schemaFile = path.join(fixtureDir, "schema.graphql")
const generator = path.join(packageDir, "tooling", "generateQueryTypes.js")

/* Written to rather than read from: the generator rewrites the sources it is pointed at. */
const sourceRoot = path.join(packageDir, "test", ".tmp", "src")

/*
 * Built here rather than through @graphql-tools/load, which the CLI uses: that one is external to the
 * Vitest module graph while this file is not, and graphql resolves to its CJS build for the one and its
 * ESM build for the other -- two realms, and every type check across them fails. The CLI's own schema
 * loading is covered by the end-to-end case below, which runs it as its own process.
 */
const schema = buildSchema(fs.readFileSync(schemaFile, "utf8"))


/**
 * Renders the result type of one query, the way the service does for a module it is handed. The source
 * offsets are irrelevant here -- nothing is patched back -- so they only have to be a pair.
 */
function queryTransform(query)
{
    const {selectedOperations} = analyzeGraphQLQuery(
        schema,
        {calls: {GraphQLQuery: [[query]]}, indexes: {GraphQLQuery: [[0, 1]]}},
        "./sub/Q_Test"
    )
    return selectedOperations.length ? renderResultType(schema, selectedOperations) : null
}


describe("query result types", () => {

    it("renders the selection of an aliased operation", () => {
        expect(queryTransform(`query Q_Test($config: QueryConfig!) {
                xxx: queryTestFooDocument(config: $config) {
                    type
                    config
                    rows {
                        name
                        owner {
                            login
                        }
                    }
                }
            }`)).toBe(
`Pick<TestFooDocument,"type" | "config"> & {
    rows : Array<Pick<TestFoo,"name"> & {
        owner : Pick<TestUser,"login">
    }>
}`
        )
    })


    it("names a completely selected sub object instead of picking it apart", () => {
        expect(queryTransform(`query Q_Test($config: QueryConfig!) {
                queryTestFooDocument(config: $config) {
                    type
                    config
                    rows {
                        name
                        fooType {
                            name
                            ordinal
                        }
                    }
                }
            }`)).toBe(
`Pick<TestFooDocument,"type" | "config"> & {
    rows : Array<Pick<TestFoo,"name" | "fooType">>
}`
        )
    })


    it("re-declares an aliased field rather than picking it", () => {
        expect(queryTransform(`query Q_Test($config: QueryConfig!) {
                queryTestFooDocument(config: $config) {
                    type
                    config
                    rows {
                        name
                        fooType {
                            name
                            id: ordinal
                        }
                    }
                }
            }`)).toBe(
`Pick<TestFooDocument,"type" | "config"> & {
    rows : Array<Pick<TestFoo,"name"> & {
        fooType : Pick<TestFooType,"name"> & {
            id : Int
        }
    }>
}`
        )
    })


    it("marks a nullable field optional", () => {
        expect(queryTransform(`query Q_Test($config: QueryConfig!) {
                queryTestFooDocument(config: $config) {
                    type
                    config
                    rows {
                        name
                        desc: description
                    }
                }
            }`)).toBe(
`Pick<TestFooDocument,"type" | "config"> & {
    rows : Array<Pick<TestFoo,"name"> & {
        desc? : String
    }>
}`
        )
    })


    it("refuses fragments, saying where", () => {
        const withSpread = () => queryTransform(`query Q_Test($config: QueryConfig!) {
                queryTestFooDocument(config: $config) {
                    type
                    config
                    rows {
                        ...RowFields
                    }
                }
            }
            fragment RowFields on TestFoo {
                name
            }`)

        expect(withSpread).toThrow(/fragments are not supported/)
        // says where, so the message is actionable without hunting for the query
        expect(withSpread).toThrow(/\.\/sub\/Q_Test/)
        expect(withSpread).toThrow(/query Q_Test/)
        expect(withSpread).toThrow(/a fragment spread in TestFoo/)

        expect(() => queryTransform(`query Q_Test($config: QueryConfig!) {
                queryTestFooDocument(config: $config) {
                    type
                    config
                    rows {
                        ... on TestFoo { name }
                    }
                }
            }`)).toThrow(/an inline fragment in TestFoo/)
    })
})


describe("query documents", () => {

    it("recognizes a QueryDocument derived type by its shape", () => {
        expect(isQueryDocumentType(schema.getType("TestFooDocument"))).toBe(true)
        expect(isQueryDocumentType(schema.getType("TestFoo"))).toBe(false)
        expect(isQueryDocumentType(schema.getType("QueryConfig"))).toBe(false)
        expect(isQueryDocumentType(undefined)).toBe(false)
    })


    it("mixes the document methods into a document result", () => {
        const rendered = renderModule(moduleInfo(`import { GraphQLQuery, } from "@quinscape/qlive-ts";\n\n`),
            "TestFooDocument", true)

        // the document is a QueryDocument instance in the application, so update() is in its type
        expect(rendered).toContain(
            "export type Q_TestResult = TestFooDocument & QueryDocumentMethods<Q_TestResult>"
        )
        // .. and the name it needs for that is imported without the user having to think of it
        expect(rendered).toContain(
            `import { GraphQLQuery, QueryDocumentMethods } from "@quinscape/qlive-ts";`
        )
    })


    it("leaves a plain result to itself", () => {
        const rendered = renderModule(moduleInfo(`import { GraphQLQuery } from "@quinscape/qlive-ts";\n\n`),
            "TestFoo", false)

        expect(rendered).toContain("export type Q_TestResult = TestFoo\n")
        expect(rendered).not.toContain("QueryDocumentMethods")
    })


    it("imports the document methods without piling up imports", () => {
        // joins an existing import, keeping its formatting
        expect(withDocumentMethodsImport(`import { GraphQLQuery } from "@quinscape/qlive-ts";\n`))
            .toBe(`import { GraphQLQuery, QueryDocumentMethods } from "@quinscape/qlive-ts";\n`)

        expect(withDocumentMethodsImport(`import {\n    GraphQLQuery,\n    inject\n} from "@quinscape/qlive-ts";\n`))
            .toBe(`import {\n    GraphQLQuery,\n    inject, QueryDocumentMethods\n} from "@quinscape/qlive-ts";\n`)

        // adds an import of its own if the module does not import from the package yet
        expect(withDocumentMethodsImport(`import { Foo } from "./types";\n`))
            .toBe(`import { QueryDocumentMethods } from "@quinscape/qlive-ts";\nimport { Foo } from "./types";\n`)

        // and leaves the module alone once the name is there, so repeated updates do not pile up imports
        const alreadyImported = `import { GraphQLQuery, QueryDocumentMethods } from "@quinscape/qlive-ts";\n`
        expect(withDocumentMethodsImport(alreadyImported)).toBe(alreadyImported)

        const typeOnly = `import type { QueryDocumentMethods } from "@quinscape/qlive-ts";\n`
        expect(withDocumentMethodsImport(typeOnly)).toBe(typeOnly)
    })
})


describe("module rewriting", () => {

    /* The whole way through: babel finds the query, the schema types it, the module gets it back. */
    function generateInto(template)
    {
        fs.rmSync(sourceRoot, {recursive: true, force: true})
        fs.mkdirSync(path.join(sourceRoot, "sub"), {recursive: true})
        fs.writeFileSync(path.join(sourceRoot, "sub", "Q_Test.ts"), template, "utf8")

        const analysis = analyzeSourceTree({sourceRoot})
        const updated = updateGraphQLQueryTypes(schema, analysis, sourceRoot)

        return {updated, source: fs.readFileSync(path.join(sourceRoot, "sub", "Q_Test.ts"), "utf8")}
    }


    /* The CLI, as a build runs it -- its own schema loading, its own argument handling. */
    function runGenerator(template)
    {
        fs.rmSync(sourceRoot, {recursive: true, force: true})
        fs.mkdirSync(path.join(sourceRoot, "sub"), {recursive: true})
        fs.writeFileSync(path.join(sourceRoot, "sub", "Q_Test.ts"), template, "utf8")

        execFileSync(process.execPath, [generator, schemaFile, sourceRoot], {stdio: "pipe"})

        return fs.readFileSync(path.join(sourceRoot, "sub", "Q_Test.ts"), "utf8")
    }

    const template = fs.readFileSync(path.join(fixtureDir, "Q_Test.template.ts"), "utf8")


    it("writes the result type into the module the query lives in", () => {
        const {updated, source} = generateInto(template)

        expect(updated).toEqual(["./sub/Q_Test"])
        expect(source).toBe(fs.readFileSync(path.join(fixtureDir, "Q_Test.expected.ts"), "utf8"))
    })


    it("leaves an up-to-date module untouched", () => {
        generateInto(template)
        expect(generateInto(fs.readFileSync(path.join(fixtureDir, "Q_Test.expected.ts"), "utf8")).updated)
            .toEqual([])
    })


    it("generates the same source through the CLI", () => {
        expect(runGenerator(template)).toBe(fs.readFileSync(path.join(fixtureDir, "Q_Test.expected.ts"), "utf8"))
    })


    it("says what to write when the constructor has no type parameter yet", () => {
        expect(() => generateInto(template.replace("new GraphQLQuery<any>", "new GraphQLQuery")))
            .toThrow(/new GraphQLQuery<any>\(\.\.\.\)/)
    })
})


function moduleInfo(prologue)
{
    return {
        variableName: "Q_Test",
        prologue,
        leftSideOfDefinition: "export const Q_Test",
        graphQLQueryDefinition: "new GraphQLQuery<Q_TestResult>(`query Q_Test { x }`)",
        epilogue: "\n"
    }
}
