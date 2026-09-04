import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import {Temporal} from "temporal-polyfill";
import {init} from "../src/config";
import {GraphQLQuery} from "../src/GraphQLQuery";
import {QueryDocument} from "../src/QueryDocument";
import {queryResult, testConfig} from "./fixtures/testConfig";

const Q_Foo = new GraphQLQuery<QueryDocument<any>>(
    `query Q_Foo($config: QueryConfig!, $since: Timestamp) {
        xxx: queryFooDocument(config: $config) {
            type
            rowCount
            rows {
                id
                created
                when: created
            }
        }
    }`
)

/**
 * Stubs out what the server puts into the page and what it answers with. The suite
 * runs in node, so the globals graphql() reads are not there either.
 */
function respondWith(response: any)
{
    const fetchMock = vi.fn().mockResolvedValue({
        json: () => Promise.resolve(response)
    })

    vi.stubGlobal("fetch", fetchMock)
    vi.stubGlobal("window", {location: {origin: "http://localhost"}})
    vi.stubGlobal("contextPath", "")
    vi.stubGlobal("csrfToken", {header: "X-CSRF", value: "token"})

    return fetchMock
}

function sentVariables(fetchMock: ReturnType<typeof vi.fn>)
{
    return JSON.parse(fetchMock.mock.calls[0][1].body).variables
}

beforeAll(async () => {
    await init({config: testConfig, data: {}})
})

afterEach(() => {
    vi.unstubAllGlobals()
})

describe("GraphQLQuery", () => {

    it("refuses a query using fragments where that query is declared", () => {
        // no config needed for this: it is decided by the document alone, which is why
        // it can happen while the query module is being imported
        expect(() => new GraphQLQuery(`query Q_Frag { queryFooDocument { ...DocFields } }`))
            .toThrowError(/Q_Frag uses fragments/)
    })

    it("converts the variables on their way out", async () => {
        const fetchMock = respondWith({data: queryResult(), errors: []});

        await Q_Foo.execute({
            config: {pageSize: 10},
            since: Temporal.Instant.from("2026-01-02T03:04:05Z")
        })

        expect(sentVariables(fetchMock)).toEqual({
            config: {pageSize: 10},
            since: "2026-01-02T03:04:05Z"
        })
    })

    it("converts the result and hands back the value of the one selection", async () => {
        respondWith({data: queryResult(), errors: []})

        const doc = await Q_Foo.execute({config: {pageSize: 10}})

        // not the result keyed by "xxx", the document itself
        expect(doc).toBeInstanceOf(QueryDocument)
        expect(doc.rows[0].created).toBeInstanceOf(Temporal.Instant)
        expect(doc.rows[0].when).toBeInstanceOf(Temporal.Instant)
    })

    it("rejects with the GraphQL errors rather than converting them", async () => {
        respondWith({data: null, errors: [{message: "boom"}]})

        await expect(Q_Foo.execute({config: {}})).rejects.toThrowError(/boom/)
    })
})
