// @vitest-environment jsdom
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import {init} from "../../src/config";
import {GraphQLQuery} from "../../src/GraphQLQuery";
import {QueryDocument} from "../../src/QueryDocument";
import {WorkingSet, WorkingSetSnapshot} from "../../src/merge/WorkingSet";
import {useWorkingSet} from "../../src/merge/useWorkingSet";
import {barDocument, mergeConfig} from "../fixtures/mergeConfig";
import {respondWith} from "../fixtures/graphqlMock";

/**
 * The three lines over the store. A working set is read exactly the way a query document is, so what is
 * tested here is that the store and the hook meet: an edit re-renders, and an unchanged working set hands
 * out the same snapshot again.
 */

const Q_BARS = new GraphQLQuery<QueryDocument<any>>(
    `query Q_Bars($config: QueryConfig!) {
        queryBarDocument(config: $config) {
            type
            config
            rowCount
            rows { id name num version }
        }
    }`
)

/** the snapshot the last render was given, and how often the view rendered */
let snapshot: WorkingSetSnapshot | null = null
let renderCount = 0

function BarForm({ws, row}: { ws: WorkingSet, row: any })
{
    snapshot = useWorkingSet(ws)
    renderCount++

    const bar = ws.edit(row)

    return (
        <input value={bar.name} onChange={e => { bar.name = e.target.value }}/>
    )
}

let container: HTMLElement
let root: Root

function render(element: React.ReactNode)
{
    act(() => {
        root.render(element)
    })
}

beforeAll(async () => {
    await init({config: mergeConfig, csrfToken: mergeConfig.csrfToken!, data: {}})
})

beforeEach(() => {
    // react-dom asks for this before it will let act() drive a render
    vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true)

    container = document.createElement("div")
    document.body.appendChild(container)
    root = createRoot(container)

    snapshot = null
    renderCount = 0
})

afterEach(() => {
    act(() => {
        root.unmount()
    })
    container.remove()
    vi.unstubAllGlobals()
})


async function editableBar()
{
    respondWith({data: {queryBarDocument: barDocument()}, errors: []})

    const document = await Q_BARS.execute({config: {offset: 0, pageSize: 10, condition: null, sortFields: []}})
    const ws = new WorkingSet()
    ws.register(document)

    return {ws, row: document.rows[0]}
}


describe("useWorkingSet", () => {

    it("renders the draft and reports a working set nobody has touched", async () => {

        const {ws, row} = await editableBar()
        render(<BarForm ws={ws} row={row}/>)

        expect(container.querySelector("input")!.value).toBe("Bar #1")
        expect(snapshot!.dirty).toBe(false)
        expect(snapshot!.conflicts).toEqual([])
    })

    it("re-renders on a write to the draft", async () => {

        const {ws, row} = await editableBar()
        render(<BarForm ws={ws} row={row}/>)

        act(() => { ws.edit(row).name = "Changed" })

        expect(container.querySelector("input")!.value).toBe("Changed")
        expect(snapshot!.dirty).toBe(true)
    })

    it("hands out the same snapshot again where nothing changed", async () => {

        const {ws, row} = await editableBar()
        render(<BarForm ws={ws} row={row}/>)

        const first = snapshot
        render(<BarForm ws={ws} row={row}/>)

        // rendered twice, on the same object both times -- what keeps a memoized child from churning
        expect(renderCount).toBe(2)
        expect(snapshot).toBe(first)
    })
})
