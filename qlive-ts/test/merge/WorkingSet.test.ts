import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import {Temporal} from "temporal-polyfill";
import {init} from "../../src/config";
import {GraphQLQuery} from "../../src/GraphQLQuery";
import {QueryDocument} from "../../src/QueryDocument";
import {WorkingSet} from "../../src/merge/WorkingSet";
import {MergeResult} from "../../src/merge/types";
import {barDocument, mergeConfig} from "../fixtures/mergeConfig";
import {respondWith, sentVariables} from "../fixtures/graphqlMock";

/**
 * The client half of the merge: what a working set remembers about the rows it was given, what it records
 * when they are edited, and what it sends. The server it talks to is the mocked /graphql endpoint -- the
 * merge itself is covered against a real database in qlive-test.
 */

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
 * A query with the same rows and no version selected on them, which is the mistake register() exists to
 * catch. The links below them keep theirs: what a version is the base for is the row's own fields, so this
 * is a query that can still edit the associations.
 */
const Q_BARS_UNVERSIONED = new GraphQLQuery<QueryDocument<any>>(
    `query Q_BarsUnversioned($config: QueryConfig!) {
        queryBarDocument(config: $config) {
            type
            config
            rowCount
            rows {
                id
                name
                num
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
 * Registers the rows of Q_BARS_UNVERSIONED, which are the fixture's minus the version on the Bar itself.
 */
async function loadUnversionedBars()
{
    const {version, ...selected} = barDocument().rows[0]
    respondWith({data: {queryBarDocument: {...barDocument(), rows: [selected]}}, errors: []})

    return await Q_BARS_UNVERSIONED.execute({config: CONFIG})
}

const CONFIG = {offset: 0, pageSize: 10, condition: null, sortFields: []}

function documentResponse(name?: string, version?: string)
{
    return {data: {queryBarDocument: barDocument(name, version)}, errors: []}
}

function mergeResponse(result: MergeResult)
{
    return {data: {mergeWorkingSet: result}, errors: []}
}

/**
 * Runs the query against a mocked server and returns the document, which is what a view would hand to
 * register().
 */
async function loadBars()
{
    respondWith(documentResponse())

    return await Q_BARS.execute({config: CONFIG})
}

beforeAll(async () => {
    await init({config: mergeConfig, csrfToken: mergeConfig.csrfToken!, data: {}})
})

afterEach(() => {
    vi.unstubAllGlobals()
})


describe("registration", () => {

    it("takes the rows of a document and the rows below them", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        // every one of them can be edited, which is the whole of what registration is for
        expect(ws.edit(document.rows[0]).name).toBe("Bar #1")
        expect(ws.edit(document.rows[1]).name).toBe("Bar #2")
        expect(ws.edit(document.rows[0].bazLinks[0]).bazId).toBe("baz-1")
        expect(ws.edit(document.rows[0].bazLinks[0].baz).name).toBe("Baz #1")

        expect(ws.dirty).toBe(false)
    })

    it("refuses to write a field of a row whose version was not selected", async () => {

        const document = await loadUnversionedBars()
        const ws = new WorkingSet()

        // registering is fine, and so is asking for the draft: a query selects rows a view only displays as
        // readily as ones it edits, and the write is where a missing base actually costs something
        ws.register(document)
        const bar = ws.edit(document.rows[0])

        expect(() => bar.name = "Changed")
            .toThrowError(/Bar bar-1 was registered without its version.*Q_BarsUnversioned/s)
        expect(() => ws.delete(document.rows[0]))
            .toThrowError(/Bar bar-1 was registered without its version.*Q_BarsUnversioned/s)
    })

    it("edits the associations of a row whose version was not selected", async () => {

        const document = await loadUnversionedBars()
        const ws = new WorkingSet()
        ws.register(document)

        ws.edit(document.rows[0]).bazLinks = []

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        const {changes, deletions} = sentVariables(fetchMock)

        // nothing of the Bar goes out, which is why it never needed a version: an association is a row of
        // the link type and is held to the version of that row
        expect(changes).toEqual([])
        expect(deletions).toEqual([{type: "BarLink", id: "link-1", version: "lv1"}])
    })

    it("refuses to edit a row of a versioned type that has none", async () => {

        respondWith({
            data: {queryBarDocument: {...barDocument(), rows: [{...barDocument().rows[0], version: null}]}},
            errors: []
        })
        const document = await Q_BARS.execute({config: CONFIG})
        const ws = new WorkingSet()
        ws.register(document)

        expect(() => ws.edit(document.rows[0]).name = "Changed").toThrowError(/Bar bar-1 has no version/)
    })

    it("refuses to delete a link whose version was not selected", async () => {

        // the links come back without a version, which is the query that reads a link array to show it and
        // then finds itself editing it
        const bars: any = barDocument()
        bars.rows[0].bazLinks = bars.rows[0].bazLinks.map(({version, ...link}: any) => link)
        respondWith({data: {queryBarDocument: bars}, errors: []})

        const document = await Q_BARS.execute({config: CONFIG})
        const ws = new WorkingSet()
        ws.register(document)

        ws.edit(document.rows[0]).bazLinks = []

        await expect(ws.merge()).rejects.toThrowError(/BarLink link-1 was registered without its version/)
    })

    it("refuses a row it was never given", async () => {

        const ws = new WorkingSet()

        expect(() => ws.edit({id: "bar-1"})).toThrowError(/Not a row of this working set/)
    })
})


describe("drafts", () => {

    it("records a write and reads it back", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const bar = ws.edit(document.rows[0])
        bar.name = "Changed"

        expect(bar.name).toBe("Changed")
        expect(ws.dirty).toBe(true)

        // the row is not the draft, and nothing was written to it
        expect(bar).not.toBe(document.rows[0])
        expect(document.rows[0].name).toBe("Bar #1")
    })

    it("hands out one draft per row", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        expect(ws.edit(document.rows[0])).toBe(ws.edit(document.rows[0]))

        // and takes one back, so that calling it on a draft is free
        expect(ws.edit(ws.edit(document.rows[0]))).toBe(ws.edit(document.rows[0]))
    })

    it("stops being a change when the value comes back", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const bar = ws.edit(document.rows[0])
        bar.name = "Changed"
        bar.name = "Bar #1"

        expect(ws.dirty).toBe(false)
    })

    it("compares a converted value by what it holds, not by which object it is", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const bar = ws.edit(document.rows[0])
        bar.created = Temporal.Instant.from("2026-06-06T00:00:00Z")
        expect(ws.dirty).toBe(true)

        // a second Temporal.Instant of the same moment, which is not the one the row was registered with
        bar.created = Temporal.Instant.from("2026-01-02T03:04:05Z")
        expect(ws.dirty).toBe(false)
    })

    it("refuses to change what names the row or what the write is held to", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const bar = ws.edit(document.rows[0])

        expect(() => { bar.id = "other" }).toThrowError(/Cannot change Bar.id/)
        expect(() => { bar.version = "other" }).toThrowError(/Cannot change Bar.version/)
    })

    it("refuses a field the type does not have", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        expect(() => { ws.edit(document.rows[0]).nmae = "typo" })
            .toThrowError('Type "Bar" has no field "nmae"')
    })

    it("hands out the current values as a plain object", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const bar = ws.edit(document.rows[0])
        bar.name = "Changed"

        const raw = ws.raw(bar)

        expect(raw.name).toBe("Changed")
        expect(raw.num).toBe(1)
        expect(ws.raw(document.rows[0])).toEqual(raw)
    })

    it("notifies its subscribers of every change", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const seen: boolean[] = []
        ws.subscribe(() => seen.push(ws.getSnapshot().dirty))

        const bar = ws.edit(document.rows[0])
        bar.name = "Changed"
        bar.name = "Bar #1"

        expect(seen).toEqual([true, false])
    })
})


describe("what a merge sends", () => {

    it("sends one field change per changed field, in the scalar type of the field", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const bar = ws.edit(document.rows[0])
        bar.name = "Changed"
        bar.num = 42
        bar.created = Temporal.Instant.from("2026-06-06T00:00:00Z")

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        const {changes, deletions, mergeConfig: config} = sentVariables(fetchMock)

        expect(deletions).toEqual([])
        expect(config).toEqual({resolveConflicts: true})
        expect(changes).toEqual([
            {
                type: "Bar",
                id: "bar-1",
                version: "v1",
                new: false,
                changes: [
                    {field: "name", value: {type: "String", value: "Changed"}},
                    {field: "num", value: {type: "Int", value: 42}},
                    {field: "created", value: {type: "Timestamp", value: "2026-06-06T00:00:00Z"}}
                ]
            }
        ])
    })

    it("sends nothing at all when nothing was touched", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        const result = await ws.merge()

        expect(result).toEqual({status: "DONE", conflicts: []})
        expect(fetchMock).not.toHaveBeenCalled()
    })

    it("sends a created row whole, under an id it generated", async () => {

        const ws = new WorkingSet()
        const bar = ws.create<any>("Bar", {name: "New one", num: 7})
        bar.description = "and a bit more"

        expect(ws.dirty).toBe(true)
        expect(bar.id).toMatch(/^[0-9a-f-]{36}$/)

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        expect(sentVariables(fetchMock).changes).toEqual([
            {
                type: "Bar",
                id: bar.id,
                version: null,
                new: true,
                changes: [
                    {field: "name", value: {type: "String", value: "New one"}},
                    {field: "num", value: {type: "Int", value: 7}},
                    {field: "description", value: {type: "String", value: "and a bit more"}}
                ]
            }
        ])
    })

    it("sends a deletion under the version the row was read at", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        ws.delete(document.rows[1])

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        const {changes, deletions} = sentVariables(fetchMock)

        expect(changes).toEqual([])
        expect(deletions).toEqual([{type: "Bar", id: "bar-2", version: "v2"}])
    })

    it("drops a created row that was deleted again rather than telling the server about it", async () => {

        const ws = new WorkingSet()
        const bar = ws.create<any>("Bar", {name: "New one"})

        ws.delete(bar)

        expect(ws.dirty).toBe(false)

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        expect(fetchMock).not.toHaveBeenCalled()
    })

    it("says it cannot show a conflict where it was made that way", async () => {

        const document = await loadBars()
        const ws = new WorkingSet({resolveConflicts: false})
        ws.register(document)

        ws.edit(document.rows[0]).name = "Changed"

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        expect(sentVariables(fetchMock).mergeConfig).toEqual({resolveConflicts: false})
    })
})


describe("many-to-many", () => {

    it("turns a link taken out of the array into a deletion of the link row", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const bar = ws.edit<any>(document.rows[0])
        bar.bazLinks = bar.bazLinks.filter((link: any) => link.bazId !== "baz-1")

        expect(ws.dirty).toBe(true)
        expect(bar.bazLinks).toEqual([])

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        const {changes, deletions} = sentVariables(fetchMock)

        // the Bar itself has nothing to write -- what changed was an association, not a field of the row
        expect(changes).toEqual([])
        expect(deletions).toEqual([{type: "BarLink", id: "link-1", version: "lv1"}])
    })

    it("turns a link put into the array into an insert carrying both foreign keys", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const bar = ws.edit<any>(document.rows[1])
        bar.bazLinks = [...bar.bazLinks, {bazId: "baz-1"}]

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        const {changes, deletions} = sentVariables(fetchMock)

        expect(deletions).toEqual([])
        expect(changes).toHaveLength(1)
        expect(changes[0].id).toMatch(/^[0-9a-f-]{36}$/)
        expect(changes[0]).toMatchObject({
            type: "BarLink",
            version: null,
            new: true,
            changes: [
                {field: "barId", value: {type: "String", value: "bar-2"}},
                {field: "bazId", value: {type: "String", value: "baz-1"}}
            ]
        })
    })

    it("recognises an association by the row on the other side", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        // the short form, and the one a view can render: the association is the Baz, not its id
        const baz = document.rows[0].bazLinks[0].baz
        ws.edit<any>(document.rows[1]).bazLinks = [{baz}]

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        expect(sentVariables(fetchMock).changes[0].changes).toEqual([
            {field: "barId", value: {type: "String", value: "bar-2"}},
            {field: "bazId", value: {type: "String", value: "baz-1"}}
        ])
    })

    it("never writes the type on the other side", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const bar = ws.edit<any>(document.rows[0])
        bar.bazLinks = []
        ws.edit<any>(document.rows[1]).bazLinks = [{bazId: "baz-1"}]

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        const {changes, deletions} = sentVariables(fetchMock)

        expect(changes.map((c: any) => c.type)).toEqual(["BarLink"])
        expect(deletions.map((d: any) => d.type)).toEqual(["BarLink"])
    })

    it("is no change when the same associations come back", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const bar = ws.edit<any>(document.rows[0])
        bar.bazLinks = []
        expect(ws.dirty).toBe(true)

        // a different link row saying the same thing, which is the same association
        bar.bazLinks = [{bazId: "baz-1"}]

        expect(ws.dirty).toBe(false)
    })

    it("leaves a link row the application made itself to itself", async () => {

        // a link type carrying a field of its own cannot be written by a diff, so the application creates
        // the row. The diff sees it is already one of ours and does not insert it a second time.
        const ws = new WorkingSet()
        const corge = ws.create<any>("Corge", {})
        const link = ws.create<any>("CorgeLink", {corgeId: corge.id, graultId: "grault-1", weight: 3})

        corge.corgeLinks = [link]

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        const {changes} = sentVariables(fetchMock)

        expect(changes.map((c: any) => c.type)).toEqual(["Corge", "CorgeLink"])
        expect(changes[1].changes).toEqual([
            {field: "corgeId", value: {type: "String", value: corge.id}},
            {field: "graultId", value: {type: "String", value: "grault-1"}},
            {field: "weight", value: {type: "Int", value: 3}}
        ])
    })

    it("deletes a link once when it was both removed and deleted", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        ws.delete(document.rows[0].bazLinks[0])
        ws.edit<any>(document.rows[0]).bazLinks = []

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        expect(sentVariables(fetchMock).deletions).toEqual([{type: "BarLink", id: "link-1", version: "lv1"}])
    })

    it("refuses a link array the query did not select", async () => {

        const {bazLinks, ...selected} = barDocument().rows[0]
        respondWith({data: {queryBarDocument: {...barDocument(), rows: [selected]}}, errors: []})

        const document = await Q_BARS.execute({config: CONFIG})
        const ws = new WorkingSet()
        ws.register(document)

        expect(() => { ws.edit<any>(document.rows[0]).bazLinks = [] })
            .toThrowError(/Cannot change Bar.bazLinks.*did not\s+select it/s)
    })

    it("refuses a link that says nothing about the other side", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        expect(() => { ws.edit<any>(document.rows[1]).bazLinks = [{}] })
            .toThrowError(/A BarLink of Bar.bazLinks says nothing about which Baz it links to/)

        expect(() => { ws.edit<any>(document.rows[1]).bazLinks = "baz-1" as any })
            .toThrowError(/Cannot set Bar.bazLinks to something that is not an array/)
    })

    it("leaves an ordinary object field where it was", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        // BarLink.baz is a row of its own, not an association to be set
        expect(() => { ws.edit<any>(document.rows[0].bazLinks[0]).baz = {id: "baz-2"} })
            .toThrowError(/Cannot change BarLink.baz: it is not a scalar field/)
    })
})

describe("what comes back", () => {

    it("clears the changes and refreshes the documents when it landed", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        ws.edit(document.rows[0]).name = "Changed"

        // the merge first, then the query the refresh runs again -- with the row as the merge left it
        const fetchMock = respondWith(documentResponse("Changed", "v3"))
        fetchMock.mockResolvedValueOnce({
            json: () => Promise.resolve(mergeResponse({status: "DONE", conflicts: []}))
        })

        const result = await ws.merge()

        expect(result.status).toBe("DONE")
        expect(ws.dirty).toBe(false)
        expect(fetchMock).toHaveBeenCalledTimes(2)
        expect(document.rows[0].name).toBe("Changed")

        // the refreshed rows are registered, at the version the merge wrote
        ws.edit(document.rows[0]).name = "Changed again"

        const second = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        expect(sentVariables(second).changes[0].version).toBe("v3")
    })

    it("keeps the changes and moves the base on when it did not", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        ws.edit(document.rows[0]).name = "Changed"

        respondWith(mergeResponse({
            status: "CONFLICT",
            conflicts: [
                {
                    type: "Bar",
                    id: "bar-1",
                    storedVersion: "v9",
                    deleted: false,
                    fields: [
                        {
                            field: "name",
                            mine: {type: "String", value: "Changed"},
                            stored: {type: "String", value: "Somebody else"},
                            informational: false
                        }
                    ]
                }
            ]
        }))

        const result = await ws.merge()

        expect(result.status).toBe("CONFLICT")
        expect(ws.dirty).toBe(true)
        expect(ws.getSnapshot().conflicts).toHaveLength(1)
        expect(ws.getSnapshot().conflicts[0].fields[0].stored).toEqual({type: "String", value: "Somebody else"})

        // saving again is the user's to make, and it goes against what is in the database now
        const second = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        expect(sentVariables(second).changes[0]).toMatchObject({
            id: "bar-1",
            version: "v9",
            changes: [{field: "name", value: {type: "String", value: "Changed"}}]
        })
    })

    it("converts the values a conflict carries like any other value of their type", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        ws.edit(document.rows[0]).created = Temporal.Instant.from("2026-06-06T00:00:00Z")

        respondWith(mergeResponse({
            status: "CONFLICT",
            conflicts: [
                {
                    type: "Bar",
                    id: "bar-1",
                    storedVersion: "v9",
                    deleted: false,
                    fields: [
                        {
                            field: "created",
                            mine: {type: "Timestamp", value: "2026-06-06T00:00:00Z"} as any,
                            stored: {type: "Timestamp", value: "2026-07-07T00:00:00Z"} as any,
                            informational: false
                        }
                    ]
                }
            ]
        }))

        await ws.merge()

        expect(ws.getSnapshot().conflicts[0].fields[0].stored!.value)
            .toEqual(Temporal.Instant.from("2026-07-07T00:00:00Z"))
    })
})


describe("taking it back", () => {

    it("restores the registered values and forgets the conflicts", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const bar = ws.edit(document.rows[0])
        bar.name = "Changed"
        ws.delete(document.rows[1])
        const created = ws.create<any>("Bar", {name: "New one"})

        ws.undo()

        expect(ws.dirty).toBe(false)
        expect(bar.name).toBe("Bar #1")
        expect(() => ws.edit(created)).toThrowError(/no longer holds/)
    })

    it("drops the documents as well when it is cleared", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        ws.clear()

        expect(ws.dirty).toBe(false)
        expect(() => ws.edit(document.rows[0])).toThrowError(/Not a row of this working set/)
    })
})
