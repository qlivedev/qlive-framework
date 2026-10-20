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
            rows { id name num description created version bazes { id name version } }
        }
    }`
)

let container: HTMLElement
let root: Root

function render(element: React.ReactNode)
{
    act(() => {
        root.unmount()
        root = createRoot(container)
        root.render(element)
    })
}

function input(): HTMLInputElement | null
{
    return container.querySelector<HTMLInputElement>("input[role=combobox]")
}

function listed(): string[]
{
    return Array.from(container.querySelectorAll(".qlive-rowset-rows > li")).map(li => li.firstChild!.textContent!)
}

function options(): string[]
{
    return Array.from(container.querySelectorAll("[role=option]")).map(option => option.textContent!)
}

async function typeAndWait(text: string)
{
    act(() => {
        const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!
        setter.call(input(), text)
        input()!.dispatchEvent(new Event("input", {bubbles: true}))
    })
    await act(() => new Promise(resolve => setTimeout(resolve, 300)))
}

function press(key: string)
{
    act(() => {
        input()!.dispatchEvent(new KeyboardEvent("keydown", {key, bubbles: true}))
    })
}

async function editableBar()
{
    respondWith({data: {queryBarDocument: barDocument()}, errors: []})
    const document = await Q_BARS.execute({config: {offset: 0, pageSize: 10, condition: null, sortFields: []}})
    const ws = new WorkingSet()
    ws.register(document)
    return ws.edit(document.rows[0]) as any
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
    vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true)
    container = document.createElement("div")
    document.body.appendChild(container)
    root = createRoot(container)
    config().widgets = []
    config().meta.types.Baz = {meta: {...config().meta.types.Baz?.meta, nameFields: ["name"]}}
})

afterEach(() => {
    act(() => {
        root.unmount()
    })
    container.remove()
    vi.unstubAllGlobals()
    delete config().meta.types.Baz.meta!.nameFields
})


describe("RowSet", () => {

    it("is the widget of a many-to-many field, listing the rows and removing one", async () => {

        const bar = await editableBar()
        render(<Form row={ bar }><Field path="bazes"/></Form>)

        expect(listed()).toEqual(["Baz #1"])
        expect(input()!.placeholder).toBe("[Add...]")

        const remove = container.querySelector<HTMLButtonElement>(".qlive-rowset-remove")!
        expect(remove.getAttribute("aria-label")).toBe("[Remove {0}:Baz #1]")
        act(() => {
            remove.click()
        })

        expect(bar.bazes).toEqual([])
        expect(listed()).toEqual([])
    })

    it("adds a row the endpoint offers, leaving out the rows already associated", async () => {

        const bar = await editableBar()
        render(<Form row={ bar }><Field path="bazes"/></Form>)

        const fetchMock = respondWith({type: "Baz", rows: [{id: "baz-2", name: "Baz #2"}], rowCount: 1, config: {}})
        await typeAndWait("baz")

        const {config: sent} = JSON.parse(fetchMock.mock.calls[0][1].body)
        expect(sent.condition.name).toBe("and")
        expect(sent.condition.operands[1]).toEqual({
            type: "Condition",
            name: "not",
            operands: [{
                type: "Condition",
                name: "in",
                operands: [{type: "FieldReference", name: "id"}, {type: "Values", scalarType: "String", values: ["baz-1"]}]
            }]
        })
        expect(options()).toEqual(["Baz #2"])

        press("Enter")

        expect(bar.bazes.map((baz: any) => baz.id)).toEqual(["baz-1", "baz-2"])
        expect(listed()).toEqual(["Baz #1", "Baz #2"])
        // ready for the next one
        expect(input()!.value).toBe("")
    })

    it("is a group named by the label, describing what the picker is for", async () => {

        const bar = await editableBar()
        render(<Form row={ bar }><Field path="bazes"/></Form>)

        const group = container.querySelector(".qlive-rowset")!
        expect(group.getAttribute("role")).toBe("group")
        expect(document.getElementById(group.getAttribute("aria-labelledby")!)!.tagName).toBe("LABEL")
        expect(document.getElementById(input()!.getAttribute("aria-describedby")!)!.textContent)
            .toBe("[Searches the rows to add]")
    })

    it("moves focus on to the next row's button after a remove, else the one before, else the picker", async () => {

        const bar = await editableBar()
        act(() => {
            bar.bazes = [
                {id: "baz-1", name: "Baz #1", version: 1},
                {id: "baz-2", name: "Baz #2", version: 1},
                {id: "baz-3", name: "Baz #3", version: 1}
            ]
        })
        render(<Form row={ bar }><Field path="bazes"/></Form>)

        function removeFocused()
        {
            act(() => {
                (document.activeElement as HTMLButtonElement).click()
            })
        }

        act(() => {
            container.querySelectorAll<HTMLButtonElement>(".qlive-rowset-remove")[1].focus()
        })
        removeFocused()
        expect(listed()).toEqual(["Baz #1", "Baz #3"])
        expect(document.activeElement!.getAttribute("aria-label")).toBe("[Remove {0}:Baz #3]")

        removeFocused()
        expect(document.activeElement!.getAttribute("aria-label")).toBe("[Remove {0}:Baz #1]")

        removeFocused()
        expect(listed()).toEqual([])
        expect(document.activeElement).toBe(input())
    })

    it("lists the rows without buttons or picker where the field can't be edited", async () => {

        const bar = await editableBar()
        render(<Form row={ bar }><Field path="bazes" mode="readOnly"/></Form>)

        expect(listed()).toEqual(["Baz #1"])
        expect(container.querySelector(".qlive-rowset-remove")).toBeNull()
        expect(input()).toBeNull()
        expect(container.querySelector(".qlive-rowset")!.id).not.toBe("")
        expect(container.querySelector(".qlive-rowset")!.getAttribute("aria-labelledby")).not.toBeNull()
    })

    it("offers a catalog type's rows from the bootstrap", async () => {

        const bar = await editableBar()
        config().meta.types.Baz.meta!.catalog = true
        config().catalogs = {Baz: [{id: "baz-1", name: "Baz #1"}, {id: "baz-2", name: "Baz #2"}, {id: "baz-3", name: "Other"}]}
        const fetchMock = vi.fn()
        vi.stubGlobal("fetch", fetchMock)
        try
        {
            render(<Form row={ bar }><Field path="bazes"/></Form>)

            press("ArrowDown")
            expect(options()).toEqual(["Baz #2", "Other"])

            await typeAndWait("oth")
            press("Enter")

            expect(bar.bazes.map((baz: any) => baz.id)).toEqual(["baz-1", "baz-3"])
            expect(fetchMock).not.toHaveBeenCalled()
        }
        finally
        {
            delete config().meta.types.Baz.meta!.catalog
            delete config().catalogs
        }
    })
})
