// @vitest-environment jsdom
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import config, {init, QLiveConfig} from "../../src/config";
import {GraphQLQuery} from "../../src/GraphQLQuery";
import {QueryDocument} from "../../src/QueryDocument";
import {WorkingSet} from "../../src/merge/WorkingSet";
import {Invariant} from "../../src/merge/invariants";
import type {RawValue} from "../../src/FilterDSL";
import {Form} from "../../src/form/Form";
import {barDocument, mergeConfig} from "../fixtures/mergeConfig";
import {respondWith, sentVariables} from "../fixtures/graphqlMock";
import {testAuthentication} from "../fixtures/testConfig";

/**
 * The invariants of a type, which the working set holds a row to before a merge sends it: Bar's num positive, its
 * name given, and a large num described.
 */

const field = (name: string) => ({type: "FieldReference" as const, name})
const value = (scalarType: string, v: RawValue) => ({type: "Value" as const, scalarType, value: v})

const NUM_POSITIVE: Invariant = {
    condition: {type: "Condition", name: "gt", operands: [field("num"), value("Int", 0)]},
    field: "num",
    messageKey: "Bar.numPositive",
    errorClass: null
}

const NAME_GIVEN: Invariant = {
    condition: {type: "Condition", name: "ne", operands: [field("name"), value("String", "")]},
    field: "name",
    messageKey: "Bar.nameGiven",
    errorClass: null
}

const LARGE_DESCRIBED: Invariant = {
    condition: {
        type: "Condition",
        name: "or",
        operands: [
            {type: "Condition", name: "isNotNull", operands: [field("description")]},
            {type: "Condition", name: "lt", operands: [field("num"), value("Int", 100)]}
        ]
    },
    field: "description",
    messageKey: "Bar.largeDescribed",
    errorClass: "large"
}

const invariantConfig: QLiveConfig = {
    ...mergeConfig,
    meta: {
        ...mergeConfig.meta,
        types: {
            ...mergeConfig.meta.types,
            Bar: {meta: {...mergeConfig.meta.types.Bar.meta, rules: [NUM_POSITIVE, NAME_GIVEN, LARGE_DESCRIBED]}}
        }
    }
}

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

async function editableBar()
{
    respondWith({data: {queryBarDocument: barDocument()}, errors: []})

    const document = await Q_BARS.execute({config: {offset: 0, pageSize: 10, condition: null, sortFields: []}})
    const ws = new WorkingSet()
    ws.register(document)

    return {ws, bar: ws.edit(document.rows[0]) as any}
}

const keys = (refusals: readonly { messageKey: string }[]) => refusals.map(refusal => refusal.messageKey)

let container: HTMLElement
let root: Root

beforeAll(async () => {
    await init({
        config: invariantConfig,
        csrfToken: invariantConfig.csrfToken!,
        authentication: testAuthentication(),
        data: {}
    })
})

beforeEach(() => {
    vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true)

    container = document.createElement("div")
    document.body.appendChild(container)
    root = createRoot(container)
    config().widgets = []
    config().translations = {}
})

afterEach(() => {
    act(() => {
        root.unmount()
    })
    container.remove()
    vi.unstubAllGlobals()
})


describe("a new row", () => {

    it("shows a broken invariant once the user changed a field it reads, and the rest only after a save", async () => {
        const ws = new WorkingSet()
        const bar = ws.create<any>("Bar")

        expect(ws.accessor(bar).refusals).toEqual([])

        bar.name = ""
        expect(keys(ws.accessor(bar).field("name").refusals)).toEqual(["Bar.nameGiven"])
        expect(ws.accessor(bar).field("num").refusals).toEqual([])

        const fetchMock = respondWith({data: null, errors: []})
        const result = await ws.merge()

        expect(fetchMock).not.toHaveBeenCalled()
        expect(result.status).toBe("REFUSED")
        // a num nobody set reads as null, and a comparison with null holds for nothing, as in SQL
        expect(keys(result.refusals)).toEqual(["Bar.numPositive", "Bar.nameGiven", "Bar.largeDescribed"])
        expect(keys(ws.accessor(bar).field("num").refusals)).toEqual(["Bar.numPositive"])
        expect(keys(ws.getSnapshot().refusals)).toEqual(["Bar.numPositive", "Bar.nameGiven", "Bar.largeDescribed"])
    })

    it("lets the refusals go as the user fixes the fields", async () => {
        const ws = new WorkingSet()
        const bar = ws.create<any>("Bar", {name: ""})
        respondWith({data: null, errors: []})
        await ws.merge()

        bar.num = 3
        bar.name = "Bar #3"

        expect(ws.accessor(bar).refusals).toEqual([])
    })
})


describe("an update", () => {

    it("is held to the invariants reading a field it changes, at once", async () => {
        const {ws, bar} = await editableBar()

        bar.description = "changed"
        expect(ws.accessor(bar).refusals).toEqual([])

        bar.num = 0
        expect(keys(ws.accessor(bar).field("num").refusals)).toEqual(["Bar.numPositive"])
    })

    it("sends the unchanged fields an invariant it touches reads", async () => {
        const {ws, bar} = await editableBar()
        bar.num = 5

        // a conflict, so that the working set doesn't go and read the rows again
        const fetchMock = respondWith({data: {mergeWorkingSet: {status: "CONFLICT", conflicts: [], refusals: []}}, errors: []})
        await ws.merge()

        expect(sentVariables(fetchMock).changes[0].changes).toEqual([
            {field: "num", value: {type: "Int", value: 5}},
            {field: "description", value: {type: "String", value: null}}
        ])
    })

    it("refuses where the merged row breaks one, without asking the server", async () => {
        const {ws, bar} = await editableBar()
        bar.num = 500

        const fetchMock = respondWith({data: null, errors: []})
        const result = await ws.merge()

        expect(fetchMock).not.toHaveBeenCalled()
        expect(result.refusals).toEqual([
            {type: "Bar", id: "bar-1", field: "description", messageKey: "Bar.largeDescribed", errorClass: "large"}
        ])
    })

    it("takes the server's refusal of a rule it doesn't know", async () => {
        const {ws, bar} = await editableBar()
        bar.name = "Renamed"

        respondWith({
            data: {
                mergeWorkingSet: {
                    status: "REFUSED",
                    conflicts: [],
                    refusals: [
                        {type: "Bar", id: "bar-1", field: "name", messageKey: "Bar.nameUnique", errorClass: null}
                    ]
                }
            },
            errors: []
        })
        const result = await ws.merge()

        expect(result.status).toBe("REFUSED")
        expect(keys(ws.accessor(bar).field("name").refusals)).toEqual(["Bar.nameUnique"])
        expect(ws.dirty).toBe(true)
    })

    it("forgets the refusals with the changes", async () => {
        const {ws, bar} = await editableBar()
        bar.num = 0
        respondWith({data: null, errors: []})
        await ws.merge()

        ws.undo()

        expect(ws.accessor(bar).refusals).toEqual([])
        expect(ws.getSnapshot().refusals).toEqual([])
    })
})


describe("a form", () => {

    it("shows a broken invariant at the field it blames", async () => {
        const {ws, bar} = await editableBar()

        act(() => {
            root.render(<Form row={ bar }/>)
        })
        act(() => {
            bar.num = 0
        })

        const refusalsAt = (name: string) => Array.from(
            container.querySelector(`[id$="-${name}"]`)!.closest(".qlive-field")!.querySelectorAll(".qlive-refusals li"),
            li => li.textContent
        )
        expect(refusalsAt("num")).toEqual(["[Bar.numPositive]"])
        expect(refusalsAt("name")).toEqual([])
        expect(ws.dirty).toBe(true)
    })
})
