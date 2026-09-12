// @vitest-environment jsdom
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {init} from "../../src/config";
import {maskOf} from "../../src/merge/fieldMask";
import {WorkingSet} from "../../src/merge/WorkingSet";
import {GraphQLQuery} from "../../src/GraphQLQuery";
import {initPubSub} from "../../src/pubsub";
import {watchDocument, watchWorkingSet} from "../../src/push/entityVersion";
import {QueryDocument} from "../../src/QueryDocument";
import {connected, FakeWebSocket, lastSocket} from "../fixtures/fakeWebSocket";
import {respondWith, sentVariables} from "../fixtures/graphqlMock";
import {barDocument, mergeConfig} from "../fixtures/mergeConfig";
import {testAuthentication} from "../fixtures/testConfig";

const ME = testAuthentication().id


const Q_BARS = new GraphQLQuery<QueryDocument<any>>(
    `query Q_Bars($config: QueryConfig!) {
        queryBarDocument(config: $config) {
            type
            config
            rowCount
            rows {
                id
                name
                num
                description
                created
                version
                bazLinks {
                    id
                    version
                    barId
                    bazId
                    baz { id name version }
                }
            }
        }
    }`
)

/**
 * The document a view holds, as a real QueryDocument rather than the plain object the merge fixture
 * yields: a watcher subscribes to it and follows its updates, and only the store has either.
 */
async function loadBars(): Promise<QueryDocument<any>>
{
    respondWith({data: {queryBarDocument: barDocument()}, errors: []})

    return await Q_BARS.execute({config: {offset: 0, pageSize: 10, condition: null, sortFields: []}})
}


/**
 * The fixture's rows as a working set takes them: a document is what register() walks, and running its
 * query again is part of a merge that landed.
 */
function registered()
{
    return {...barDocument(), update: async function () { return this }}
}


/** The Subscribe frames sent on the current socket, oldest first. */
function subscribes(): any[]
{
    return lastSocket().messages().filter(m => m.type === "Subscribe")
}


/** Delivers one EntityVersion message to the subscription of the given id. */
function publish(id: string, entityType: string, entityId: string, fields: string[], ownerId = "somebody")
{
    lastSocket().receive({
        type: "Topic",
        topic: "EntityVersion",
        ids: [id],
        payload: {
            id: "ver-1",
            entityType,
            entityId,
            prev: null,
            fieldMask: maskOf(entityType, fields).toString(),
            fieldLayout: "layout-1",
            ownerId,
            created: "2026-09-11T10:00:00Z"
        }
    })
}


beforeEach(async () => {
    FakeWebSocket.instances = []
    vi.stubGlobal("WebSocket", FakeWebSocket)

    await init({
        config: mergeConfig,
        csrfToken: mergeConfig.csrfToken!,
        authentication: testAuthentication(),
        data: {}
    })

    initPubSub()
})


afterEach(() => {
    initPubSub()
    vi.unstubAllGlobals()
})


describe("the condition a store subscribes with", () => {

    it("names the rows on screen, the fields they were read with, and not me", async () => {

        const doc = await loadBars()
        watchDocument(doc)
        connected()

        expect(subscribes()).toHaveLength(1)

        const condition = subscribes()[0].condition

        // The motivating subscription from the merge design, one clause group per type the walk reached,
        // all of it under a single "somebody else wrote this".
        const json = JSON.stringify(condition)

        expect(json).toContain("bar-1")
        expect(json).toContain("bar-2")
        expect(json).toContain("baz-1")
        expect(json).toContain("link-1")
        expect(json).toContain(maskOf("Bar", ["name", "num", "description", "created", "id", "version"]).toString())
        expect(json).toContain(ME)
        expect(json).toContain("ownerId")
        expect(json).toContain("bitAnd")
    })


    it("subscribes to nothing while a store holds nothing", () => {

        watchWorkingSet(new WorkingSet())

        expect(FakeWebSocket.instances).toHaveLength(0)
    })
})


describe("following what is on screen", () => {

    it("subscribes anew when the id set turns over, and drops the old one after", async () => {

        const doc = await loadBars()
        watchDocument(doc)
        connected()

        expect(subscribes()).toHaveLength(1)

        const first = subscribes()[0].id

        // a page turn: the query runs again and the document holds other rows
        respondWith({
            data: {queryBarDocument: {...barDocument(), rows: [{...barDocument().rows[0], id: "bar-9"}]}},
            errors: []
        })

        await doc.update({offset: 10})

        const sent = lastSocket().messages()
        const second = subscribes()[1]

        expect(second).toBeDefined()
        expect(JSON.stringify(second.condition)).toContain("bar-9")

        // Subscribed anew before the old registration is dropped: between the two the client hears a
        // message twice, and the other order would have it hear nothing at all.
        const order = sent.map(m => m.type + " " + m.id)

        expect(order.indexOf("Subscribe " + second.id)).toBeLessThan(order.indexOf("Unsubscribe " + first))
    })


    it("holds one subscription while what is held does not move", () => {

        const rows = registered()
        const ws = new WorkingSet()
        ws.register(rows)

        watchWorkingSet(ws)
        connected()

        expect(subscribes()).toHaveLength(1)

        // an edit changes the working set and notifies, but not which rows it holds
        ws.edit(rows.rows[0]).name = "edited"

        expect(subscribes()).toHaveLength(1)
    })
})


describe("a working set hears what a merge would have told it later", () => {

    it("marks the fields somebody else changed", () => {

        const rows = registered()
        const ws = new WorkingSet()
        ws.register(rows)

        watchWorkingSet(ws)
        connected()

        const row = rows.rows[0]
        ws.edit(row).name = "mine"

        publish(subscribes()[0].id, "Bar", "bar-1", ["name", "description"])

        const accessor = ws.accessor(row)

        // one the user also changed is a conflict, one they did not is simply taken
        expect(accessor.field("name").status).toBe("conflict")
        expect(accessor.field("description").status).toBe("remoteChanged")
        expect(accessor.field("num").status).toBe("unchanged")
    })


    it("shows the value the row was read with, no values having travelled", () => {

        const rows = registered()
        const ws = new WorkingSet()
        ws.register(rows)

        watchWorkingSet(ws)
        connected()

        const row = rows.rows[0]

        publish(subscribes()[0].id, "Bar", "bar-1", ["name"])

        const accessor = ws.accessor(row)

        expect(accessor.field("name").status).toBe("remoteChanged")

        // A field known to have changed and not known to what reads as the value the row was read with --
        // there being nothing else to show, and the status saying the rest.
        expect(accessor.field("name").stored).toBe("Bar #1")
        expect(ws.edit(row).name).toBe("Bar #1")
    })


    it("leaves the version where it stands, so the merge is still the one that decides", async () => {

        const rows = registered()

        const ws = new WorkingSet()
        ws.register(rows)

        watchWorkingSet(ws)
        connected()

        ws.edit(rows.rows[0]).name = "mine"

        publish(subscribes()[0].id, "Bar", "bar-1", ["name"])

        const fetchMock = respondWith(
            {data: {mergeWorkingSet: {status: "DONE", conflicts: []}}, errors: []}
        )

        await ws.merge()

        // Adopting the version that just landed would quietly take away the guard the next save runs
        // into, and the marks would be the only warning the user ever got. The write still goes out
        // against the version the row was read at, and the merge is still what refuses it.
        expect(sentVariables(fetchMock).changes[0].version).toBe("v1")
    })


    it("ignores a row it does not hold", () => {

        const ws = new WorkingSet()
        ws.register(registered())

        watchWorkingSet(ws)
        connected()

        expect(() => publish(subscribes()[0].id, "Bar", "bar-99", ["name"])).not.toThrow()
    })
})


describe("a document reports and decides nothing", () => {

    it("goes stale when a row it shows changed, naming the fields", async () => {

        const doc = await loadBars()
        const live = watchDocument(doc)
        connected()

        expect(live.getSnapshot().stale).toBe(false)

        publish(subscribes()[0].id, "Bar", "bar-1", ["name"])

        expect(live.getSnapshot().stale).toBe(true)
        expect(live.getSnapshot().remoteChanged).toEqual([{type: "Bar", id: "bar-1", fields: ["name"]}])
    })


    it("changes nothing about the document itself", async () => {

        const doc = await loadBars()
        watchDocument(doc)
        connected()

        publish(subscribes()[0].id, "Bar", "bar-1", ["name"])

        // No values travelled, so there is nothing to apply and nothing is applied.
        expect(doc.rows[0].name).toBe("Bar #1")
    })


    it("tells a listener, so a view re-renders", async () => {

        const doc = await loadBars()
        const live = watchDocument(doc)
        connected()

        let told = 0
        live.subscribe(() => told++)

        publish(subscribes()[0].id, "Bar", "bar-1", ["name"])

        expect(told).toBe(1)
    })


    it("hands out the same snapshot until something moves", async () => {

        const doc = await loadBars()
        const live = watchDocument(doc)
        connected()

        const first = live.getSnapshot()

        expect(live.getSnapshot()).toBe(first)

        publish(subscribes()[0].id, "Bar", "bar-1", ["name"])

        expect(live.getSnapshot()).not.toBe(first)
    })


    it("forgets what arrived once the view dismissed it", async () => {

        const doc = await loadBars()
        const live = watchDocument(doc)
        connected()

        publish(subscribes()[0].id, "Bar", "bar-1", ["name"])
        live.clear()

        expect(live.getSnapshot().stale).toBe(false)
    })


    it("stops when it is closed", async () => {

        const doc = await loadBars()
        const live = watchDocument(doc)
        const socket = connected()

        const id = subscribes()[0].id
        live.close()

        expect(socket.messages().filter(m => m.type === "Unsubscribe")).toEqual([
            {type: "Unsubscribe", topic: "EntityVersion", id}
        ])
    })
})
