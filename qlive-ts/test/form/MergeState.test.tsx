// @vitest-environment jsdom
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import config, {init} from "../../src/config";
import {GraphQLQuery} from "../../src/GraphQLQuery";
import {QueryDocument} from "../../src/QueryDocument";
import {WorkingSet} from "../../src/merge/WorkingSet";
import {Form} from "../../src/form/Form";
import {Field} from "../../src/form/Field";
import {barDocument, mergeConfig} from "../fixtures/mergeConfig";
import {respondWith} from "../fixtures/graphqlMock";
import {testAuthentication} from "../fixtures/testConfig";

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

let container: HTMLElement
let root: Root

function render(element: React.ReactNode)
{
    act(() => {
        root.render(element)
    })
}

function query<E extends Element = HTMLElement>(selector: string): E
{
    const found = container.querySelector<E>(selector)
    if (!found)
    {
        throw new Error("Nothing matches " + selector)
    }
    return found
}

/** types into a controlled input the way React notices */
function type(input: HTMLInputElement, text: string)
{
    act(() => {
        const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!
        setter.call(input, text)
        input.dispatchEvent(new Event("input", {bubbles: true}))
    })
}

function fieldInput(path: string): HTMLInputElement
{
    return query<HTMLInputElement>(`[id$="-${path}"]`)
}

beforeAll(async () => {
    await init({
        config: mergeConfig,
        csrfToken: mergeConfig.csrfToken!,
        authentication: testAuthentication(),
        data: {}
    })
    config().translations = {"Bar.name": "Name", "Bar.num": "Number"}
})

beforeEach(() => {
    vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true)

    container = document.createElement("div")
    document.body.appendChild(container)
    root = createRoot(container)
    config().widgets = []
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

    return {ws, row: document.rows[0], bar: ws.edit(document.rows[0]) as any}
}


function described(input: HTMLElement): string
{
    return input.getAttribute("aria-describedby")!.split(" ").map(id => document.getElementById(id)!.textContent).join("|")
}


describe("the merge state for screen readers", () => {

    it("describes an input by what the merge did to its field, and marks it with the state's icon", async () => {

        const {ws, bar} = await editableBar()
        render(<Form row={ bar }/>)

        expect(described(fieldInput("name"))).toBe("")

        act(() => { bar.name = "Mine" })
        expect(described(fieldInput("name"))).toBe("[Changed by you]")
        expect(fieldInput("name").closest(".qlive-field-body")!.querySelector(".qlive-field-mark")!.className)
            .toBe("qlive-field-mark qlive-changed")

        act(() => {
            ws.storedState({type: "Bar", id: "bar-1", version: "v9", fields: {name: "Theirs", num: 9}})
        })
        expect(described(fieldInput("name"))).toBe("[Changed by someone else too, your value stands]")
        expect(described(fieldInput("num"))).toBe("[Changed by someone else]")
    })
})
