import {beforeAll, describe, expect, it} from "vitest";
import {init} from "../../src/config";
import {GraphQLQuery} from "../../src/GraphQLQuery";
import {QueryDocument} from "../../src/QueryDocument";
import {WorkingSet} from "../../src/merge/WorkingSet";
import {MergeConflictField, MergeResult} from "../../src/merge/types";
import {barDocument, mergeConfig} from "../fixtures/mergeConfig";
import {respondWith, sentVariables} from "../fixtures/graphqlMock";
import {testAuthentication} from "../fixtures/testConfig";

/**
 * Layer 2: what a form reads a working set through, and what a decision about a clashing field does. No
 * React here -- the accessor is a plain object, which is the whole reason it is its own layer.
 */

const Q_BARS = new GraphQLQuery<QueryDocument<any>>(
    `query Q_Bars($config: QueryConfig!) {
        queryBarDocument(config: $config) {
            type
            config
            rowCount
            rows { id name num description created version }
        }
    }`
)

const CONFIG = {offset: 0, pageSize: 10, condition: null, sortFields: []}

function mergeResponse(result: MergeResult)
{
    return {data: {mergeWorkingSet: result}, errors: []}
}

/**
 * A merge that came back saying somebody else got there first, with the given fields as the ones that
 * moved. A field carrying a "mine" is one both writes changed; one without is a field only they touched.
 */
function conflictOn(fields: MergeConflictField[]): MergeResult
{
    return {
        status: "CONFLICT",
        conflicts: [{type: "Bar", id: "bar-1", storedVersion: "v9", deleted: false, fields}]
    }
}

function clash(field: string, mine: string, stored: string): MergeConflictField
{
    return {
        field,
        mine: {type: "String", value: mine},
        stored: {type: "String", value: stored},
        informational: false
    }
}

function theirsAlone(field: string, stored: string): MergeConflictField
{
    return {field, mine: null, stored: {type: "String", value: stored}, informational: true}
}

/**
 * A working set holding the two Bar rows, with the first one edited and, where a result is given, a merge
 * of it already come back.
 */
async function edited(result?: MergeResult)
{
    respondWith({data: {queryBarDocument: barDocument()}, errors: []})

    const document = await Q_BARS.execute({config: CONFIG})
    const ws = new WorkingSet()
    ws.register(document)

    const bar = ws.edit(document.rows[0])
    bar.name = "Mine"

    if (result)
    {
        respondWith(mergeResponse(result))
        await ws.merge()
    }

    return {ws, bar, row: document.rows[0], other: document.rows[1]}
}


beforeAll(async () => {
    await init({
        config: mergeConfig,
        csrfToken: mergeConfig.csrfToken!,
        authentication: testAuthentication(),
        data: {}
    })
})


describe("what a field is", () => {

    it("says nothing about a field nobody touched", async () => {

        const {ws, bar} = await edited()
        const field = ws.accessor(bar).field("description")

        expect(field.status).toBe("unchanged")
        expect(field.className).toBe("")
        expect(field.conflict).toBe(false)
        expect(field.resolution).toBe(null)
    })

    it("marks a field the user changed", async () => {

        const {ws, bar} = await edited()
        const field = ws.accessor(bar).field("name")

        expect(field.status).toBe("changed")
        expect(field.className).toBe("qlive-changed")
        expect(field.value).toBe("Mine")
        expect(field.mine).toBe("Mine")

        // nobody has said otherwise, so what is stored is still what the row was read with
        expect(field.stored).toBe("Bar #1")
    })

    it("marks a field both writes changed, with both values", async () => {

        const {ws, bar} = await edited(conflictOn([clash("name", "Mine", "Theirs")]))
        const field = ws.accessor(bar).field("name")

        expect(field.status).toBe("conflict")
        expect(field.className).toBe("qlive-conflict")
        expect(field.conflict).toBe(true)
        expect(field.mine).toBe("Mine")
        expect(field.stored).toBe("Theirs")

        // the user's value is the one standing: they typed it on purpose and the other party is not here
        expect(field.value).toBe("Mine")
        expect(bar.name).toBe("Mine")
    })

    it("takes a field only the other write changed", async () => {

        const {ws, bar} = await edited(
            conflictOn([clash("name", "Mine", "Theirs"), theirsAlone("description", "Theirs too")])
        )
        const field = ws.accessor(bar).field("description")

        expect(field.status).toBe("moved")
        expect(field.className).toBe("qlive-moved")

        // no opinion about it, so catching up is not a decision anybody needs to make
        expect(field.value).toBe("Theirs too")
        expect(bar.description).toBe("Theirs too")
    })

    it("marks a field that moved even where no value came with it", async () => {

        const {ws, bar} = await edited(conflictOn([
            {field: "name", mine: null, stored: null, informational: false}
        ]))
        const field = ws.accessor(bar).field("name")

        // a type that did not opt in to resolution names the fields and carries no values. That the field
        // moved is still worth marking, and what the form shows is what the row was read with
        expect(field.status).toBe("conflict")
        expect(field.stored).toBe("Bar #1")
    })

    it("says which fields are which", async () => {

        const {ws, bar} = await edited(
            conflictOn([clash("name", "Mine", "Theirs"), theirsAlone("description", "Theirs too")])
        )
        const merge = ws.accessor(bar)

        expect(merge.changedFields()).toEqual(["name"])
        expect(merge.conflictedFields()).toEqual(["name"])
        expect(merge.movedFields()).toEqual(["description"])
        expect(merge.resolvedFields()).toEqual([])
    })

    it("reaches another row of the same working set", async () => {

        const {ws, bar, other} = await edited()

        expect(ws.accessor(bar).of(other).id).toBe("bar-2")
        expect(ws.accessor(bar).of(other).field("name").status).toBe("unchanged")
    })

    it("says so where the row is gone", async () => {

        const {ws, bar} = await edited({
            status: "CONFLICT",
            conflicts: [{type: "Bar", id: "bar-1", storedVersion: null, deleted: true, fields: []}]
        })

        expect(ws.accessor(bar).gone).toBe(true)
    })
})


describe("deciding", () => {

    it("holds the user's value back where they took the stored one", async () => {

        const {ws, bar} = await edited(conflictOn([clash("name", "Mine", "Theirs")]))
        ws.accessor(bar).field("name").resolve("stored")

        const field = ws.accessor(bar).field("name")

        expect(field.status).toBe("resolved")
        expect(field.className).toBe("qlive-conflict-resolved")
        expect(field.resolution).toBe("stored")
        expect(bar.name).toBe("Theirs")

        // nothing of this row is left to write, so there is nothing to save
        expect(ws.dirty).toBe(false)

        // and nothing goes over: the value that is there is the one the user asked for
        const sent = respondWith(mergeResponse({status: "DONE", conflicts: []}))
        await ws.merge()

        expect(sent).toHaveBeenCalledTimes(0)
    })

    it("keeps the user's value where they took their own", async () => {

        const {ws, bar} = await edited(conflictOn([clash("name", "Mine", "Theirs")]))
        ws.accessor(bar).field("name").resolve("mine")

        expect(ws.accessor(bar).field("name").status).toBe("resolved")
        expect(bar.name).toBe("Mine")

        const second = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        // against the version that is in the database now, which is what makes the second save land
        expect(sentVariables(second).changes[0]).toMatchObject({
            id: "bar-1",
            version: "v9",
            changes: [{field: "name", value: {type: "String", value: "Mine"}}]
        })
    })

    it("gives a value back that was decided away", async () => {

        const {ws, bar} = await edited(conflictOn([clash("name", "Mine", "Theirs")]))

        ws.accessor(bar).field("name").resolve("stored")
        ws.accessor(bar).field("name").resolve("mine")

        // choosing theirs held ours back rather than throwing it away, so changing their mind is one call
        expect(bar.name).toBe("Mine")
        expect(ws.dirty).toBe(true)
    })

    it("takes a third value neither side had", async () => {

        const {ws, bar} = await edited(conflictOn([clash("name", "Mine", "Theirs")]))
        ws.accessor(bar).field("name").resolveWith("Both, then")

        const field = ws.accessor(bar).field("name")

        expect(field.status).toBe("resolved")
        expect(field.mine).toBe("Both, then")
        expect(field.stored).toBe("Theirs")
        expect(bar.name).toBe("Both, then")
    })

    it("makes a field typed over one nobody has decided about again", async () => {

        const {ws, bar} = await edited(conflictOn([clash("name", "Mine", "Theirs")]))
        ws.accessor(bar).field("name").resolve("stored")

        bar.name = "Third thought"

        // a value typed over a clash is a new value rather than a choice between the two that clashed
        expect(ws.accessor(bar).field("name").status).toBe("conflict")
        expect(bar.name).toBe("Third thought")
    })

    it("counts a change back to the stored value as no change", async () => {

        const {ws, bar} = await edited(conflictOn([clash("name", "Mine", "Theirs")]))

        bar.name = "Theirs"

        // what "no change" means is what is in the database, which moved when the other write landed
        expect(ws.dirty).toBe(false)
        expect(ws.accessor(bar).field("name").status).toBe("moved")
    })

    it("refuses a decision about a field nobody else wrote", async () => {

        const {ws, bar} = await edited()

        expect(() => ws.accessor(bar).field("name").resolve("stored"))
            .toThrowError(/Nothing to decide about Bar.name/)
    })

    it("forgets the decisions when the changes are taken back", async () => {

        const {ws, bar} = await edited(conflictOn([clash("name", "Mine", "Theirs")]))
        ws.accessor(bar).field("name").resolve("stored")

        ws.undo()

        // the decision was about a write that no longer exists. What the other party wrote is still there,
        // and a form still shows the field as one that moved
        expect(ws.accessor(bar).field("name").status).toBe("moved")
        expect(bar.name).toBe("Theirs")
    })
})


describe("the view flag", () => {

    it("shows the user's own values, the stored ones, or the two folded together", async () => {

        const {ws, bar} = await edited(
            conflictOn([clash("name", "Mine", "Theirs"), theirsAlone("description", "Theirs too")])
        )

        expect(ws.view).toBe("merged")
        expect([bar.name, bar.description]).toEqual(["Mine", "Theirs too"])

        ws.setView("mine")
        expect([bar.name, bar.description]).toEqual(["Mine", null])

        ws.setView("stored")
        expect([bar.name, bar.description]).toEqual(["Theirs", "Theirs too"])

        ws.setView("merged")
        expect([bar.name, bar.description]).toEqual(["Mine", "Theirs too"])
    })

    it("is what the accessor reports as the field's value", async () => {

        const {ws, bar} = await edited(conflictOn([clash("name", "Mine", "Theirs")]))

        ws.setView("stored")

        const field = ws.accessor(bar).field("name")

        // value follows the flag; mine and stored are what they say whatever it is set to
        expect(field.value).toBe("Theirs")
        expect(field.mine).toBe("Mine")
        expect(field.stored).toBe("Theirs")
    })

    it("changes nothing about what a merge writes", async () => {

        const {ws, bar} = await edited(conflictOn([clash("name", "Mine", "Theirs")]))
        ws.setView("stored")

        const second = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        expect(sentVariables(second).changes[0].changes)
            .toEqual([{field: "name", value: {type: "String", value: "Mine"}}])
        expect(ws.raw(bar).name).toBe("Mine")
    })
})


describe("stored state as an input", () => {

    it("takes what somebody else wrote without a merge having failed", async () => {

        const {ws, bar} = await edited()

        // what a push message will do, and what a conflict does today
        ws.storedState({type: "Bar", id: "bar-1", version: "v9", fields: {description: "Moved"}})

        expect(ws.accessor(bar).field("description").status).toBe("moved")
        expect(bar.description).toBe("Moved")

        const second = respondWith(mergeResponse({status: "CONFLICT", conflicts: []}))
        await ws.merge()

        expect(sentVariables(second).changes[0].version).toBe("v9")
    })

    it("says nothing about a row it does not hold", async () => {

        const {ws} = await edited()

        expect(() => ws.storedState({type: "Bar", id: "somebody-elses", fields: {name: "x"}})).not.toThrow()
    })
})
