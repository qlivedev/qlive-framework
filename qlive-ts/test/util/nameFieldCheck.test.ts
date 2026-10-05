import {afterEach, beforeAll, describe, expect, test, vi} from "vitest";
import {init} from "../../src/config";
import {GraphQLQuery} from "../../src/GraphQLQuery";
import {parseQuery} from "../../src/util/parseQuery";
import {warnMissingNameFields} from "../../src/util/nameFieldCheck";
import {testAuthentication, testConfig, testCsrfToken} from "../fixtures/testConfig";

/** The warnings checking the given query prints. */
function warningsFor(query: string): string[]
{
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {})
    warnMissingNameFields(parseQuery(query))
    return warn.mock.calls.map(args => args.join(" "))
}

beforeAll(async () => {
    await init({
        config: {
            ...testConfig,
            meta: {
                ...testConfig.meta,
                types: {
                    Foo: {meta: {nameFields: ["name", "description"]}},
                    AppUser: {meta: {nameFields: ["id"]}}
                }
            }
        },
        csrfToken: testCsrfToken(),
        authentication: testAuthentication(),
        data: {}
    })
})

afterEach(() => {
    vi.restoreAllMocks()
})

describe("warnMissingNameFields", () => {

    test("is quiet when every selected type has its name fields", () => {
        expect(warningsFor(`query Q_Foo($config: QueryConfig!) {
            queryFooDocument(config: $config) {
                rows { name description owner { id } }
            }
        }`)).toEqual([])
    })

    test("names every place a selection lacks name fields, nested ones too", () => {
        const warnings = warningsFor(`query Q_Foo($config: QueryConfig!) {
            xxx: queryFooDocument(config: $config) {
                rows { name owner { lastLogin } }
            }
        }`)

        expect(warnings).toHaveLength(1)
        expect(warnings[0]).toContain("Query Q_Foo")
        expect(warnings[0]).toContain("xxx.rows (Foo): description")
        expect(warnings[0]).toContain("xxx.rows.owner (AppUser): id")
    })

    test("does not count an aliased name field", () => {
        const warnings = warningsFor(`query Q_User {
            currentUser { userId: id }
        }`)

        expect(warnings[0]).toContain("currentUser (AppUser): id")
    })

    test("leaves types without name fields and leaf selections alone", () => {
        expect(warningsFor(`query Q_Count { countFoos }`)).toEqual([])
        expect(warningsFor(`query Q_Doc($config: QueryConfig!) {
            queryFooDocument(config: $config) { rowCount }
        }`)).toEqual([])
    })

    test("does not check mutations", () => {
        expect(warningsFor(`mutation M_Foo($foo: FooInput!) {
            updateFoo(foo: $foo) { id }
        }`)).toEqual([])
    })

    test("runs when a query builds its conversion map, once", () => {
        const warn = vi.spyOn(console, "warn").mockImplementation(() => {})
        const query = new GraphQLQuery(`query Q_Users { currentUser { lastLogin } }`)

        expect(warn).not.toHaveBeenCalled()

        void query.conversionMap
        void query.conversionMap

        expect(warn).toHaveBeenCalledTimes(1)
    })
})
