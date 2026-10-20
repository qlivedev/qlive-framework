// @vitest-environment jsdom
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import config, {init} from "../../src/config";
import {WorkingSet} from "../../src/merge/WorkingSet";
import {Form} from "../../src/form/Form";
import {Field} from "../../src/form/Field";
import {relatedRows} from "../../src/form/relatedRows";
import {mergeConfig} from "../fixtures/mergeConfig";
import {respondWith} from "../fixtures/graphqlMock";
import {testAuthentication} from "../fixtures/testConfig";

let container: HTMLElement
let root: Root

function render(element: React.ReactNode)
{
    act(() => {
        root.render(element)
    })
}

function input(): HTMLInputElement
{
    return container.querySelector<HTMLInputElement>("input[role=combobox]")!
}

function options(): string[]
{
    return Array.from(container.querySelectorAll("[role=option]")).map(option => option.textContent!)
}

function note(): string | null
{
    return container.querySelector(".qlive-picker-note")?.textContent ?? null
}

/** types into the picker the way React notices, and waits for the search to be answered */
async function typeAndWait(text: string)
{
    act(() => {
        const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!
        setter.call(input(), text)
        input().dispatchEvent(new Event("input", {bubbles: true}))
    })
    await act(() => new Promise(resolve => setTimeout(resolve, 300)))
}

function press(key: string)
{
    act(() => {
        input().dispatchEvent(new KeyboardEvent("keydown", {key, bubbles: true}))
    })
}

/** the body the request the given mock recorded posted */
function sentBody(fetchMock: ReturnType<typeof vi.fn>, call: number = 0)
{
    return JSON.parse(fetchMock.mock.calls[call][1].body)
}

const USERS = [{id: "u-1", login: "admin"}, {id: "u-2", login: "adele"}]

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
    config().meta.types.AppUser = {meta: {nameFields: ["login"]}}
})

afterEach(() => {
    act(() => {
        root.unmount()
    })
    container.remove()
    vi.unstubAllGlobals()
    delete config().meta.types.AppUser
})


describe("relatedRows", () => {

    it("posts the type and the config, the condition as JSON, and reads the rows", async () => {

        const fetchMock = respondWith({type: "AppUser", rows: USERS, rowCount: 2, config: {}})

        const found = await relatedRows("AppUser", {pageSize: 20})

        expect(fetchMock.mock.calls[0][0]).toMatch(/\/api\/related-rows$/)
        expect(fetchMock.mock.calls[0][1].headers[config().csrfToken!.header]).toBe(config().csrfToken!.value)
        expect(sentBody(fetchMock)).toEqual({type: "AppUser", config: {pageSize: 20}})
        expect(found).toEqual({type: "AppUser", rows: USERS, rowCount: 2})
    })

    it("rejects with the reason the server refused for", async () => {

        vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
            status: 400,
            json: () => Promise.resolve({error: "No foreign key references 'Qux'."})
        }))

        await expect(relatedRows("Qux")).rejects.toThrow("No foreign key references 'Qux'.")
    })
})


describe("RowPicker", () => {

    function ownedFoo()
    {
        const ws = new WorkingSet()
        return ws.create<any>("Foo", {name: "f", ownerId: "u-1"})
    }

    it("is the widget of a to-one relation, naming the row by the rows fetched, else by its key", async () => {

        const foo = ownedFoo()
        foo.ownerId = "u-3"
        render(<Form row={ foo }><Field path="owner"/></Form>)

        expect(input().value).toBe("u-3")
        expect(input().getAttribute("aria-expanded")).toBe("false")

        respondWith({type: "AppUser", rows: [{id: "u-3", login: "carol"}], rowCount: 1, config: {}})
        await relatedRows("AppUser")
        render(<Form row={ foo }><Field path="owner" label="Owner"/></Form>)

        expect(input().value).toBe("carol")
    })

    it("searches the name fields as typed and sets the key of the row chosen", async () => {

        const foo = ownedFoo()
        const fetchMock = respondWith({type: "AppUser", rows: USERS, rowCount: 2, config: {}})
        render(<Form row={ foo }><Field path="owner"/></Form>)

        await typeAndWait("ad")

        const {config: sent} = sentBody(fetchMock)
        expect(sent.condition).toEqual({
            type: "Condition",
            name: "containsIgnoreCase",
            operands: [{type: "FieldReference", name: "login"}, {type: "Value", scalarType: "String", value: "ad"}]
        })
        expect(sent.sortFields).toEqual(["login"])
        expect(options()).toEqual(["admin", "adele"])

        press("ArrowDown")
        press("Enter")

        expect(foo.ownerId).toBe("u-2")
        expect(options()).toEqual([])
        // named by the row the picker fetched, since the row's own relation object is the old owner
        expect(input().value).toBe("adele")
    })

    it("drops typed text that chose nothing, and keeps a required key", async () => {

        const foo = ownedFoo()
        respondWith({type: "AppUser", rows: [], rowCount: 0, config: {}})
        render(<Form row={ foo }><Field path="owner"/></Form>)

        await typeAndWait("")
        expect(note()).toBe("[Nothing found]")

        act(() => {
            input().dispatchEvent(new FocusEvent("focusout", {bubbles: true}))
        })

        expect(foo.ownerId).toBe("u-1")
        expect(input().value).toBe("admin")
    })

    it("says how many rows the page left out, and why the server refused", async () => {

        respondWith({type: "AppUser", rows: USERS, rowCount: 45, config: {}})
        render(<Form row={ ownedFoo() }><Field path="owner"/></Form>)

        await typeAndWait("a")
        expect(note()).toBe("[{0} more, type to narrow:43]")

        vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
            status: 400,
            json: () => Promise.resolve({error: "Invalid query config"})
        }))
        await typeAndWait("ad")
        expect(note()).toBe("Invalid query config")
    })

    it("opens no list in a mode other than normal", () => {

        render(<Form row={ ownedFoo() }><Field path="owner" mode="readOnly"/></Form>)

        press("ArrowDown")

        expect(input().readOnly).toBe(true)
        expect(input().getAttribute("aria-expanded")).toBe("false")
    })
})


describe("RowPicker over an enum", () => {

    function garply()
    {
        return new WorkingSet().create<any>("Garply", {size: "MEDIUM", color: "RED"})
    }

    it("is the widget of an enum field, searching the values' labels without asking the server", async () => {

        const fetchMock = vi.fn()
        vi.stubGlobal("fetch", fetchMock)
        const row = garply()
        render(<Form row={ row }><Field path="size"/></Form>)

        expect(input().value).toBe("[Size.MEDIUM]")

        press("ArrowDown")
        expect(options()).toEqual(["[Size.SMALL]", "[Size.MEDIUM]", "[Size.LARGE]"])

        await typeAndWait("lar")
        expect(options()).toEqual(["[Size.LARGE]"])
        press("Enter")

        expect(row.size).toBe("LARGE")
        expect(input().value).toBe("[Size.LARGE]")
        expect(fetchMock).not.toHaveBeenCalled()
    })

    it("clears a nullable value by emptying the input, and keeps a required one", async () => {

        const row = garply()
        render(<Form row={ row }><Field path="size"/><Field path="color"/></Form>)
        const [size, color] = Array.from(container.querySelectorAll<HTMLInputElement>("input[role=combobox]"))

        for (const picker of [size, color])
        {
            act(() => {
                const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!
                setter.call(picker, "")
                picker.dispatchEvent(new Event("input", {bubbles: true}))
            })
            act(() => {
                picker.dispatchEvent(new FocusEvent("focusout", {bubbles: true}))
            })
        }

        expect(row.size).toBe("MEDIUM")
        expect(row.color).toBe(null)
        expect(color.value).toBe("")
    })

    it("names the value by its label in text mode", () => {

        render(<Form row={ garply() }><Field path="color" mode="text"/></Form>)

        expect(container.querySelector(".qlive-field-text")!.textContent).toBe("[Color.RED]")
    })
})
