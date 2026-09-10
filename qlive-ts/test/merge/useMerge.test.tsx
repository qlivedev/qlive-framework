// @vitest-environment jsdom
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import {init} from "../../src/config";
import {GraphQLQuery} from "../../src/GraphQLQuery";
import {QueryDocument} from "../../src/QueryDocument";
import {WorkingSet} from "../../src/merge/WorkingSet";
import {MergeAccessor} from "../../src/merge/MergeAccessor";
import {useMerge} from "../../src/merge/useMerge";
import {barDocument, mergeConfig} from "../fixtures/mergeConfig";
import {respondWith} from "../fixtures/graphqlMock";

/**
 * The three lines over the accessor, and the form they are written for: one hook per row, a field list the
 * form does not spell out, and an input that carries a class it knows nothing about.
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

const FIELDS = ["name", "description"]

/** the accessor the last render was given, and how often the view rendered */
let accessor: MergeAccessor | null = null
let renderCount = 0

function BarForm({ws, row}: { ws: WorkingSet, row: any })
{
    const bar: any = ws.edit(row)
    const merge = useMerge(bar)

    accessor = merge
    renderCount++

    return (
        <div>
            {
                // nothing below is written per field, which is the point of an accessor over a hook
                FIELDS.map(name => {
                    const f = merge.field(name)

                    return (
                        <label key={name}>
                            <input
                                name={name}
                                className={f.className}
                                value={bar[name] ?? ""}
                                onChange={e => { bar[name] = e.target.value }}
                            />
                            {
                                f.status === "conflict" && (
                                    <button type="button" onClick={() => f.resolve("stored")}>
                                        {String(f.stored)}
                                    </button>
                                )
                            }
                        </label>
                    )
                })
            }
        </div>
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

function input(name: string): HTMLInputElement
{
    return container.querySelector(`input[name="${name}"]`)!
}

beforeAll(async () => {
    await init({config: mergeConfig, csrfToken: mergeConfig.csrfToken!, data: {}})
})

beforeEach(() => {
    vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true)

    container = document.createElement("div")
    document.body.appendChild(container)
    root = createRoot(container)

    accessor = null
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


describe("useMerge", () => {

    it("marks a field the moment it is written", async () => {

        const {ws, row} = await editableBar()
        render(<BarForm ws={ws} row={row}/>)

        expect(input("name").className).toBe("")

        act(() => { ws.edit(row).name = "Changed" })

        expect(input("name").className).toBe("qlive-changed")
        expect(input("name").value).toBe("Changed")
        expect(accessor!.changedFields()).toEqual(["name"])
    })

    it("marks a clash and takes the decision the form offers", async () => {

        const {ws, row} = await editableBar()
        act(() => { ws.edit(row).name = "Mine" })
        render(<BarForm ws={ws} row={row}/>)

        act(() => {
            ws.storedState({type: "Bar", id: "bar-1", version: "v9", fields: {name: "Theirs"}})
        })

        expect(input("name").className).toBe("qlive-conflict")
        expect(input("name").value).toBe("Mine")

        act(() => {
            container.querySelector("button")!.click()
        })

        expect(input("name").className).toBe("qlive-conflict-resolved")
        expect(input("name").value).toBe("Theirs")
    })

    it("re-renders the whole form against another view", async () => {

        const {ws, row} = await editableBar()
        act(() => { ws.edit(row).name = "Mine" })
        render(<BarForm ws={ws} row={row}/>)

        act(() => { ws.setView("stored") })

        // no input knows anything about the flag: the read goes through the draft
        expect(input("name").value).toBe("Bar #1")
    })

    it("hands out the same accessor again where nothing changed", async () => {

        const {ws, row} = await editableBar()
        render(<BarForm ws={ws} row={row}/>)

        const first = accessor
        render(<BarForm ws={ws} row={row}/>)

        expect(renderCount).toBe(2)
        expect(accessor).toBe(first)
    })

    it("refuses a row that is not a draft", async () => {

        const {row} = await editableBar()

        // the mistake that would otherwise show up as a form that never marks anything
        expect(() => useMerge(row)).toThrowError(/Not a draft/)
    })
})
