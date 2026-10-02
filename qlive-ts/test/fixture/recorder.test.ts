// @vitest-environment jsdom
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {init, QLiveBoostrap} from "../../src/config";
import {recordFixture} from "../../src/fixture/recorder";
import {keepBootstrap} from "../../src/fixture/notes";
import {GraphQLQuery} from "../../src/GraphQLQuery";
import inject from "../../src/inject";
import {QueryDocument} from "../../src/QueryDocument";
import {respondWith, sentVariables} from "../fixtures/graphqlMock";
import {fooDocument, testAuthentication, testConfig, testCsrfToken, atViewRoute} from "../fixtures/testConfig";

/** names its config variable something other than "config", which the recorder has to find by type */
const Q_Foo = new GraphQLQuery<QueryDocument<any>>(
    `query Q_Foo($cfg: QueryConfig!) {
        xxx: queryFooDocument(config: $cfg) {
            type
            config
            rowCount
            rows {
                id
                name
            }
        }
    }`
)

const INJECTED_CONFIG = {offset: 0, pageSize: 1, condition: {type: "Condition", name: "eq", operands: []}, sortFields: ["name"]}

/**
 * The bootstrap a page with one injected page of three Foos starts from, and a plain value next to it. Plain JSON,
 * as received: the shared test config is what init() leaves it, with a component in it.
 */
function bootstrap(): QLiveBoostrap
{
    const doc = fooDocument()
    return {
        config: JSON.parse(JSON.stringify(testConfig)),
        csrfToken: testCsrfToken(),
        authentication: testAuthentication(),
        data: {
            "home/Q_Foo": {
                data: {xxx: {...doc, config: INJECTED_CONFIG, rowCount: 3}},
                type: "FooDocument",
                meta: null
            },
            "home/Q_Count": {data: {countFoos: 3}, type: "Int", meta: null}
        }
    }
}

/**
 * The server's answer to the query for all rows, holding the given number of the three.
 */
function allRows(count: number)
{
    const doc = fooDocument()
    return {
        data: {
            xxx: {
                ...doc,
                config: {...INJECTED_CONFIG, pageSize: 0},
                rows: Array.from({length: count}, (_, i) => ({...doc.rows[0], id: "foo-" + i})),
                rowCount: 3
            }
        }
    }
}

beforeEach(async () => {
    atViewRoute()
    const received = bootstrap()
    keepBootstrap(structuredClone(received))
    await init(received)
})

afterEach(() => {
    vi.unstubAllGlobals()
})

describe("recordFixture", () => {

    it("queries every query document again for all its rows, keeping the injected config", async () => {
        inject(Q_Foo, {cfg: {pageSize: 1}})
        const fetchMock = respondWith(allRows(3))

        const fixture = await recordFixture()

        // the config the server answered with, not the delta the view gave -- all rows from the start
        expect(sentVariables(fetchMock)).toEqual({cfg: {...INJECTED_CONFIG, pageSize: 0}})

        const doc = fixture.data["home/Q_Foo"].data.xxx
        expect(doc.rows.map((row: any) => row.id)).toEqual(["foo-0", "foo-1", "foo-2"])
        expect(doc.rowCount).toBe(3)
        expect(doc.config).toEqual(INJECTED_CONFIG)
    })

    it("leaves injections that are no query document as they came", async () => {
        inject(Q_Foo)
        respondWith(allRows(3))

        const fixture = await recordFixture()

        expect(fixture.data["home/Q_Count"]).toEqual(bootstrap().data["home/Q_Count"])
    })

    it("records the route of the location and drops the session's CSRF token", async () => {
        inject(Q_Foo)
        respondWith(allRows(3))

        const fixture = await recordFixture()

        expect(fixture.route).toBe("home")
        expect(fixture.csrfToken.value).toBe("")
        expect(fixture.authentication).toEqual(testAuthentication())
    })

    it("drops the schema's descriptions", async () => {
        const received = bootstrap()
        received.config!.schema.types[0].description = "a scalar"
        keepBootstrap(received)

        inject(Q_Foo)
        respondWith(allRows(3))

        const fixture = await recordFixture()

        const descriptions = JSON.stringify(fixture.config!.schema).match(/"description":"[^"]*"/g)
        expect(descriptions).toBeNull()
        expect(fixture.config!.schema.types[0]).toHaveProperty("description", null)
    })

    it("fails where the server held the rows to a maxPageSize", async () => {
        inject(Q_Foo)
        respondWith(allRows(2))

        await expect(recordFixture()).rejects.toThrow(/'home\/Q_Foo' got 2 of 3 Foo rows/)
    })

    it("fails for a query document no view read", async () => {
        respondWith(allRows(3))

        await expect(recordFixture()).rejects.toThrow(/No view read the injection 'home\/Q_Foo'/)
    })
})
