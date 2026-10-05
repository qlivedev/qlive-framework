import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import {Temporal} from "temporal-polyfill";
import {init} from "../../src/config";
import {GraphQLQuery} from "../../src/GraphQLQuery";
import {QueryDocument} from "../../src/QueryDocument";
import {WorkingSet} from "../../src/merge/WorkingSet";
import {MergeResult} from "../../src/merge/types";
import {barDocument, mergeConfig} from "../fixtures/mergeConfig";
import {respondWith, sentVariables} from "../fixtures/graphqlMock";
import {testAuthentication} from "../fixtures/testConfig";

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
                bazs { id name version }
            }
        }
    }`
)

/**
 * A query with the same rows and no version selected on them, which is the mistake register() exists to
 * catch. What a version is the base for is the row's own fields, so this is a query that can still edit the
 * associations.
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
                bazs { id name version }
            }
        }
    }`
)


/**
 * Registers the rows of Q_BARS_UNVERSIONED, which are the fixture's minus the version on the Bar itself.
 */
async function loadUnversionedBars()
{
    const {version, bazLinks, ...selected} = barDocument().rows[0]
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
    await init({
        config: mergeConfig,
        csrfToken: mergeConfig.csrfToken!,
        authentication: testAuthentication(),
        data: {}
    })
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
        expect(ws.edit(document.rows[0].bazs[0]).name).toBe("Baz #1")

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

        ws.edit(document.rows[0]).bazs = []

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        const {changes, links, deletions} = sentVariables(fetchMock)

        // nothing of the Bar goes out, which is why it never needed a version: an association is a pair
        // and held to no version at all
        expect(changes).toEqual([])
        expect(deletions).toEqual([])
        expect(links).toEqual([{type: "Bar", id: "bar-1", field: "bazs", added: [], removed: ["baz-1"]}])
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

    it("edits associations to rows read without their version", async () => {

        // the Bazs come back without a version, which is the query that reads them only to show them. An
        // association names a Baz and writes nothing of it, so none is needed
        const bars: any = barDocument()
        bars.rows[0].bazs = bars.rows[0].bazs.map(({version, ...baz}: any) => baz)
        respondWith({data: {queryBarDocument: bars}, errors: []})

        const document = await Q_BARS.execute({config: CONFIG})
        const ws = new WorkingSet()
        ws.register(document)

        ws.edit(document.rows[0]).bazs = []

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        expect(sentVariables(fetchMock).links)
            .toEqual([{type: "Bar", id: "bar-1", field: "bazs", added: [], removed: ["baz-1"]}])
    })

    it("refuses a row it was never given", async () => {

        const ws = new WorkingSet()

        expect(() => ws.edit({id: "bar-1"})).toThrowError(/Not a row of this working set/)
    })


    it("follows a document whose query ran again", async () => {

        // A page turn, a sort, a filter: update() replaces the row objects, and a row is recognised by
        // identity. Without this the whole form breaks on the next render, every row of it at once.
        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        ws.edit(document.rows[0]).name = "Mine"

        respondWith(documentResponse("Bar #1 again"))
        await document.update({offset: 10})

        expect(() => ws.edit(document.rows[0])).not.toThrow()

        // the same entity, so what the user typed is still theirs -- the row came back, it did not become
        // a different row
        expect(ws.edit(document.rows[0]).name).toBe("Mine")
        expect(ws.dirty).toBe(true)
    })


    it("tells its subscribers about rows it hadn't announced when registering again", async () => {

        // A view drafting the rows of a page it just turned to binds them before anything registers them,
        // and a watch on the working set only hears about rows through a change.
        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const heard = vi.fn()
        ws.subscribe(heard)

        const next = {...barDocument(), rows: [{...barDocument().rows[1], id: "bar-3", bazLinks: [], bazs: []}]}
        respondWith({data: {queryBarDocument: next}, errors: []})
        await document.update({offset: 10})

        ws.edit(document.rows[0])
        expect(heard).not.toHaveBeenCalled()

        ws.register(document)
        expect(heard).toHaveBeenCalledTimes(1)
        expect(ws.held().find(held => held.type === "Bar")!.ids).toContain("bar-3")

        ws.register(document)
        expect(heard).toHaveBeenCalledTimes(1)
    })


    it("follows a snapshot's document, not the snapshot", async () => {

        // What a view registers is what useInjection() handed it, which is a still of the document. Its
        // rows are the array the document held then, so a working set holding one would be looking at the
        // rows of a page that has since been turned.
        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document.getSnapshot())

        respondWith(documentResponse("Bar #1 again"))
        await document.update({offset: 10})

        expect(() => ws.edit(document.rows[0])).not.toThrow()
    })


    it("holds its rows through the refresh a merge that landed does", async () => {

        // refresh() empties the working set and then awaits the refetch, and update() tells the views
        // inside that await -- so a view re-rendering there renders rows against a working set that has
        // been emptied and not yet walked again.
        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        ws.edit(document.rows[0]).name = "Mine"

        const rendered: string[] = []

        document.subscribe(() => {
            try
            {
                ws.edit(document.rows[0])
                rendered.push("ok")
            }
            catch (e)
            {
                rendered.push((e as Error).message)
            }
        })

        respondWith(mergeResponse({status: "DONE", conflicts: []}))
        const merging = ws.merge()

        respondWith(documentResponse("Bar #1 again", "v9"))
        await merging

        expect(rendered).toEqual(["ok"])
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

    it("lists the rows created in it, by type and oldest first", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const first = ws.create("Bar", {name: "First"})
        const second = ws.create("Bar", {name: "Second"})
        ws.create("Baz", {name: "Other type"})
        ws.delete(ws.create("Bar", {name: "Dropped"}))

        expect(ws.created("Bar")).toEqual([first, second])
        expect(ws.created("Bar")[0]).toBe(first)
    })

    it("says which rows are new and which are marked for deletion", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const created = ws.create("Bar", {name: "New"})
        ws.delete(document.rows[1])

        expect(ws.accessor(created)).toMatchObject({isNew: true, deleted: false})
        expect(ws.accessor(document.rows[1])).toMatchObject({isNew: false, deleted: true})
        expect(ws.accessor(document.rows[0])).toMatchObject({isNew: false, deleted: false})
    })

    it("has every field of its type on a new row, and what the query selected on a read one", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const created = ws.create<any>("Bar")
        expect("description" in created).toBe(true)
        expect(created.description).toBeUndefined()
        expect("nonsense" in created).toBe(false)

        const read = ws.edit(document.rows[0])
        expect("name" in read).toBe(true)
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
        expect(config).toEqual({conflictValues: true})
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

    it("declines the other user's values where it was made that way", async () => {

        const document = await loadBars()
        const ws = new WorkingSet({conflictValues: false})
        ws.register(document)

        ws.edit(document.rows[0]).name = "Changed"

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        expect(sentVariables(fetchMock).mergeConfig).toEqual({conflictValues: false})
    })
})


describe("many-to-many", () => {

    it("turns a row taken out of the field into an association lost", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const bar = ws.edit<any>(document.rows[0])
        bar.bazs = bar.bazs.filter((baz: any) => baz.id !== "baz-1")

        expect(ws.dirty).toBe(true)
        expect(bar.bazs).toEqual([])

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        const {changes, links, deletions} = sentVariables(fetchMock)

        // the Bar itself has nothing to write -- what changed was an association, not a field of the row
        expect(changes).toEqual([])
        expect(deletions).toEqual([])
        expect(links).toEqual([{type: "Bar", id: "bar-1", field: "bazs", added: [], removed: ["baz-1"]}])
    })

    it("turns a row put into the field into an association gained", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        // the row itself, which is also what a view renders the association through
        const baz = document.rows[0].bazs[0]
        const bar = ws.edit<any>(document.rows[1])
        bar.bazs = [...bar.bazs, baz]

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        expect(sentVariables(fetchMock).links)
            .toEqual([{type: "Bar", id: "bar-2", field: "bazs", added: ["baz-1"], removed: []}])
    })

    it("takes a row created in the same working set", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const baz = ws.create<any>("Baz", {name: "New Baz"})
        ws.edit<any>(document.rows[1]).bazs = [baz]

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        const {changes, links} = sentVariables(fetchMock)

        // the new row goes out as a row, and the association to it after it
        expect(changes).toMatchObject([{type: "Baz", id: baz.id, new: true}])
        expect(links).toEqual([{type: "Bar", id: "bar-2", field: "bazs", added: [baz.id], removed: []}])
    })

    it("associates a row created in the same working set", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const bar = ws.create<any>("Bar", {name: "New Bar", bazs: [document.rows[0].bazs[0]]})

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        const {changes, links} = sentVariables(fetchMock)

        // the field is no column of the new row, so it travels as an association and not as a field
        expect(changes[0].changes.map((c: any) => c.field)).toEqual(["name"])
        expect(links).toEqual([{type: "Bar", id: bar.id, field: "bazs", added: ["baz-1"], removed: []}])
    })

    it("never writes the type on the other side, nor the link type", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        ws.edit<any>(document.rows[0]).bazs = []
        ws.edit<any>(document.rows[1]).bazs = [{id: "baz-1"}]

        const fetchMock = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        const {changes, links, deletions} = sentVariables(fetchMock)

        expect(changes).toEqual([])
        expect(deletions).toEqual([])
        expect(links.map((l: any) => l.id)).toEqual(["bar-1", "bar-2"])
    })

    it("is no change when the same associations come back", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const bar = ws.edit<any>(document.rows[0])
        bar.bazs = []
        expect(ws.dirty).toBe(true)

        // another object for the same row, which is the same association
        bar.bazs = [{id: "baz-1"}]

        expect(ws.dirty).toBe(false)
    })

    it("refuses a field the query did not select", async () => {

        const {bazs, ...selected} = barDocument().rows[0]
        respondWith({data: {queryBarDocument: {...barDocument(), rows: [selected]}}, errors: []})

        const document = await Q_BARS.execute({config: CONFIG})
        const ws = new WorkingSet()
        ws.register(document)

        expect(() => { ws.edit<any>(document.rows[0]).bazs = [] })
            .toThrowError(/Cannot change Bar.bazs.*did not select it/s)
    })

    it("refuses a row that says nothing about which one it is", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        expect(() => { ws.edit<any>(document.rows[1]).bazs = [{name: "Baz #1"}] })
            .toThrowError(/A row in Bar.bazs has no id/)

        expect(() => { ws.edit<any>(document.rows[1]).bazs = "baz-1" as any })
            .toThrowError(/Cannot set Bar.bazs to something that is not an array/)
    })

    it("refuses a field whose link rows need values of their own", async () => {

        const ws = new WorkingSet()
        const corge = ws.create<any>("Corge", {})

        expect(() => { corge.graults = [{id: "grault-1"}] })
            .toThrowError(/Cannot change Corge.graults: a CorgeLink needs values of its own/)
    })

    it("leaves the link array of an ordinary relation where it was", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        // the same associations as link rows, which are rows to create and delete rather than a set to diff
        expect(() => { ws.edit<any>(document.rows[0]).bazLinks = [] })
            .toThrowError(/Cannot change Bar.bazLinks: it is not a scalar field/)
    })
})

describe("associations and conflicts", () => {

    it("sends the associations again with the second attempt after a conflict elsewhere", async () => {

        const document = await loadBars()
        const ws = new WorkingSet()
        ws.register(document)

        const bar = ws.edit<any>(document.rows[0])
        bar.name = "Mine"
        bar.bazs = []

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
                            mine: {type: "String", value: "Mine"},
                            stored: {type: "String", value: "Somebody else"},
                            informational: false
                        }
                    ]
                }
            ]
        }))
        await ws.merge()

        // nothing about an association can conflict, so it is not marked
        expect(ws.accessor(document.rows[0]).conflictedFields()).toEqual(["name"])

        // and asking for it again is harmless: removing what is gone leaves the database as asked
        const second = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        const {changes, links} = sentVariables(second)

        expect(changes).toMatchObject([{type: "Bar", id: "bar-1", version: "v9"}])
        expect(links).toEqual([{type: "Bar", id: "bar-1", field: "bazs", added: [], removed: ["baz-1"]}])
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
