// @vitest-environment jsdom
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {Temporal} from "temporal-polyfill";
import {init, initFixture, isFixture, QLiveFixture} from "../../src/config";
import {field, value} from "../../src/FilterDSL";
import {GraphQLQuery} from "../../src/GraphQLQuery";
import inject from "../../src/inject";
import {initPubSub, PubSubConnection, subscribeToTopic} from "../../src/pubsub";
import {QueryDocument} from "../../src/QueryDocument";
import graphql from "../../src/util/graphql";
import {FakeWebSocket} from "../fixtures/fakeWebSocket";
import {respondWith} from "../fixtures/graphqlMock";
import {fooDocument, testAuthentication, testConfig, testCsrfToken, atViewRoute} from "../fixtures/testConfig";

const Q_Foo = new GraphQLQuery<QueryDocument<any>>(
    `query Q_Foo($config: QueryConfig!) {
        xxx: queryFooDocument(config: $config) {
            type
            config
            rowCount
            rows {
                id
                name
                created
            }
        }
    }`
)

const NAMES = ["Foo #3", "Foo #1", "Foo #5", "Foo #2", "Foo #4"]

/**
 * A fixture with all five Foos of the document, injected to show two of them by name.
 */
function fixture(): QLiveFixture
{
    const doc = fooDocument()
    return {
        config: testConfig,
        csrfToken: testCsrfToken(),
        authentication: testAuthentication(),
        data: {
            "home/Q_Foo": {
                data: {
                    xxx: {
                        ...doc,
                        config: {...doc.config, pageSize: 2, sortFields: ["name"]},
                        rows: NAMES.map((name, i) => ({...doc.rows[0], id: "foo-" + i, name})),
                        rowCount: NAMES.length
                    }
                },
                type: "FooDocument",
                meta: null
            }
        }
    }
}

beforeEach(async () => {
    atViewRoute()
    FakeWebSocket.instances = []
    vi.stubGlobal("WebSocket", FakeWebSocket)

    await initFixture(fixture())
    initPubSub()
})

afterEach(() => {
    initPubSub()
    vi.unstubAllGlobals()
})

describe("initFixture", () => {

    it("runs QLive on the fixture until init() runs it on a server again", async () => {
        expect(isFixture()).toBe(true)

        await init({...fixture(), data: {}})

        expect(isFixture()).toBe(false)
    })

    it("injects the page the injected config asks for out of all the rows", () => {
        const doc = inject(Q_Foo)

        expect(doc.rows.map(row => row.name)).toEqual(["Foo #1", "Foo #2"])
        expect(doc.rowCount).toBe(5)
        expect(doc.config).toEqual({offset: 0, pageSize: 2, condition: null, sortFields: ["name"]})
    })

    it("converts the rows as it does those a server sends", () => {
        expect(inject(Q_Foo).rows[0].created).toBeInstanceOf(Temporal.Instant)
    })

    it("pages, sorts and filters the fixture's rows in the browser", async () => {
        const fetchMock = respondWith({data: {}})
        const doc = inject(Q_Foo)

        await doc.update({offset: 2})
        expect(doc.rows.map(row => row.name)).toEqual(["Foo #3", "Foo #4"])

        await doc.update({offset: 0, sortFields: ["!name"]})
        expect(doc.rows.map(row => row.name)).toEqual(["Foo #5", "Foo #4"])

        await doc.update({condition: field("name").eq(value("Foo #2"))})
        expect(doc.rows.map(row => row.name)).toEqual(["Foo #2"])
        expect(doc.rowCount).toBe(1)

        expect(fetchMock).not.toHaveBeenCalled()
    })

    it("rejects GraphQL requests without sending them", async () => {
        const fetchMock = respondWith({data: {}})

        await expect(graphql(Q_Foo, {config: {}})).rejects.toThrow(/runs on a fixture.*Q_Foo/)
        expect(fetchMock).not.toHaveBeenCalled()
    })

    it("subscribes to nothing, so no socket opens", () => {
        const unsubscribe = subscribeToTopic("Foo", () => {})

        expect(FakeWebSocket.instances).toHaveLength(0)
        expect(PubSubConnection.getSnapshot().status).toBe("idle")

        unsubscribe()
    })
})
