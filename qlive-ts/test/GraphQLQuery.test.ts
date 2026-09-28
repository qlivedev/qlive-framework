import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import {Temporal} from "temporal-polyfill";
import {init} from "../src/config";
import {GraphQLQuery} from "../src/GraphQLQuery";
import {QueryDocument} from "../src/QueryDocument";
import {queryResult, testConfig, testCsrfToken, testAuthentication} from "./fixtures/testConfig";
import {respondWith, sentVariables} from "./fixtures/graphqlMock";
import {GraphQLResponseError, GraphQLTransportError} from "../src/util/graphql";

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

beforeAll(async () => {
    await init({
        config: testConfig,
        csrfToken: testCsrfToken(),
        authentication: testAuthentication(),
        data: {}
    })
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

    it("registers itself with the document, so it can be updated", async () => {
        respondWith({data: queryResult(), errors: []})

        expect(GraphQLQuery.access(await Q_Foo.execute({config: {}}))).toBe(Q_Foo)
    })

    it("rejects with the GraphQL errors rather than converting them", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {})
        const errors = [
            {message: "Not authenticated", locations: [], extensions: {classification: "UNAUTHENTICATED"}},
            {message: "boom"}
        ]
        respondWith({data: null, errors})

        const error = await Q_Foo.execute({config: {}}).catch(e => e)

        expect(error).toBeInstanceOf(GraphQLResponseError)
        expect(error.message).toBe("GraphQL error: Not authenticated; boom")
        expect(error.errors).toEqual(errors)
        expect(error.hasClassification("UNAUTHENTICATED")).toBe(true)
        expect(error.hasClassification("FORBIDDEN")).toBe(false)
    })

    it("rejects with the status where no GraphQL response came back", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {})
        respondWith(null)

        const unreachable = new TypeError("NetworkError when attempting to fetch resource.")
        vi.stubGlobal("fetch", vi.fn().mockRejectedValue(unreachable))
        const down = await Q_Foo.execute({config: {}}).catch(e => e)
        expect(down).toBeInstanceOf(GraphQLTransportError)
        expect(down.status).toBeNull()
        expect(down.cause).toBe(unreachable)

        const notJson = new SyntaxError("JSON.parse: unexpected character")
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue({status: 503, json: () => Promise.reject(notJson)}))
        const starting = await Q_Foo.execute({config: {}}).catch(e => e)
        expect(starting).toBeInstanceOf(GraphQLTransportError)
        expect(starting.status).toBe(503)
        expect(starting.message).toBe("The server answered 503 with something other than JSON")
        expect(starting.cause).toBe(notJson)

        vi.stubGlobal("fetch", vi.fn().mockResolvedValue({status: 404, json: () => Promise.resolve({path: "/graphql"})}))
        const elsewhere = await Q_Foo.execute({config: {}}).catch(e => e)
        expect(elsewhere).toBeInstanceOf(GraphQLTransportError)
        expect(elsewhere.status).toBe(404)
    })
})
