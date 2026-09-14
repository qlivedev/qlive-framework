// @vitest-environment jsdom
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import {init} from "../../src/config";
import {GraphQLQuery} from "../../src/GraphQLQuery";
import {QueryDocument} from "../../src/QueryDocument";
import {WorkingSet, WorkingSetSnapshot} from "../../src/merge/WorkingSet";
import {useWorkingSet} from "../../src/merge/useWorkingSet";
import {initPubSub} from "../../src/pubsub";
import {connected, FakeWebSocket, lastSocket} from "../fixtures/fakeWebSocket";
import {barDocument, mergeConfig} from "../fixtures/mergeConfig";
import {respondWith} from "../fixtures/graphqlMock";
import {testAuthentication} from "../fixtures/testConfig";

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

function BarForm({ws, row, watch}: { ws: WorkingSet, row: any, watch?: boolean })
{
    snapshot = useWorkingSet(ws, {watch})
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
    await init({
        config: mergeConfig,
        csrfToken: mergeConfig.csrfToken!,
        authentication: testAuthentication(),
        data: {}
    })
})

beforeEach(() => {
    // react-dom asks for this before it will let act() drive a render
    vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true)

    FakeWebSocket.instances = []
    vi.stubGlobal("WebSocket", FakeWebSocket)
    initPubSub()

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
    initPubSub()
    vi.unstubAllGlobals()
})


/** The subscription ids the module has live: every Subscribe it sent that it has not unsubscribed. */
function liveSubscriptions(): string[]
{
    if (FakeWebSocket.instances.length === 0)
    {
        return []
    }

    const gone = new Set(
        lastSocket().messages().filter(m => m.type === "Unsubscribe").map(m => m.id)
    )

    return lastSocket().messages()
        .filter(m => m.type === "Subscribe")
        .map(m => m.id)
        .filter(id => !gone.has(id))
}


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


/**
 * The flag that used to be a second hook. What it buys is tested where watchWorkingSet() is; what is
 * tested here is that the view drives it -- on while mounted, off when the view goes, and one subscription
 * however many components read the same set.
 */
describe("useWorkingSet({watch: true})", () => {

    it("watches nothing unless it is asked to", async () => {

        const {ws, row} = await editableBar()
        render(<BarForm ws={ws} row={row}/>)

        expect(liveSubscriptions()).toEqual([])
    })

    it("subscribes while the view is mounted and stops when it goes", async () => {

        const {ws, row} = await editableBar()
        render(<BarForm ws={ws} row={row} watch={true}/>)
        connected()

        expect(liveSubscriptions()).toHaveLength(1)

        render(<p>gone</p>)

        expect(liveSubscriptions()).toEqual([])
    })

    it("holds one subscription however many components read the set", async () => {

        const {ws, row} = await editableBar()
        render(
            <>
                <BarForm ws={ws} row={row} watch={true}/>
                <BarForm ws={ws} row={row} watch={true}/>
            </>
        )
        connected()

        expect(liveSubscriptions()).toHaveLength(1)
    })

    it("follows the flag, which a view may turn off again", async () => {

        const {ws, row} = await editableBar()
        render(<BarForm ws={ws} row={row} watch={true}/>)
        connected()

        expect(liveSubscriptions()).toHaveLength(1)

        render(<BarForm ws={ws} row={row} watch={false}/>)

        expect(liveSubscriptions()).toEqual([])
    })
})
