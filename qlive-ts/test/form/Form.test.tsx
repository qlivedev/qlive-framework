// @vitest-environment jsdom
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import config, {init} from "../../src/config";
import {GraphQLQuery} from "../../src/GraphQLQuery";
import {QueryDocument} from "../../src/QueryDocument";
import {WorkingSet} from "../../src/merge/WorkingSet";
import {Form} from "../../src/form/Form";
import {Field, fieldText} from "../../src/form/Field";
import {FormContext, useFormContext} from "../../src/form/FormContext";
import {deriveLayout, registerComponent} from "../../src/form/layout";
import {FieldState, useField} from "../../src/form/useField";
import {registerWidget} from "../../src/form/widgets";
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


describe("deriveLayout", () => {

    it("takes the fields the row has, in its order", async () => {

        // the mock answers with the whole fixture, many-to-many and link rows included
        const {bar} = await editableBar()

        expect(deriveLayout("Bar", bar).nodes.map(node => (node as any).field))
            .toEqual(["id", "name", "num", "description", "created", "version", "bazes"])
    })

    it("edits a foreign key as its relation, once, and drops lists that aren't many-to-many", () => {

        const row = {
            id: "foo-1",
            name: "Foo",
            ownerId: "u-1",
            owner: {id: "u-1", login: "admin"},
            type: "A",
            version: "v1"
        }

        expect(deriveLayout("Foo", row).nodes.map(node => (node as any).field))
            .toEqual(["id", "name", "owner", "fooType", "version"])

        const bar = {id: "bar-1", bazLinks: [], bazes: []}
        expect(deriveLayout("Bar", bar).nodes.map(node => (node as any).field)).toEqual(["id", "bazes"])
    })

    it("gives a new row every field of its type", () => {

        const ws = new WorkingSet()
        const qux = ws.create("Qux", {name: "new"})

        expect(deriveLayout("Qux", qux).nodes.map(node => (node as any).field)).toEqual(["id", "name"])
    })
})


describe("Form", () => {

    it("renders the derived layout, the keys and version as text", async () => {

        const {bar} = await editableBar()
        render(<Form row={ bar }/>)

        expect(fieldInput("name").value).toBe("Bar #1")
        expect(query("label[for$='-name']").textContent).toBe("Name")
        expect(fieldInput("num").inputMode).toBe("numeric")
        expect(fieldInput("created").type).toBe("datetime-local")
        expect(query("[id$='-id']").tagName).toBe("SPAN")
        expect(query("[id$='-id']").textContent).toBe("bar-1")
        expect(query("[id$='-version']").className).toBe("qlive-field-text")
    })

    it("writes text that parses to the draft and keeps it as typed", async () => {

        const {ws, bar} = await editableBar()
        render(<Form row={ bar }/>)

        type(fieldInput("num"), " 12")

        expect(bar.num).toBe(12)
        expect(ws.dirty).toBe(true)
        expect(fieldInput("num").value).toBe(" 12")
        expect(query(".qlive-field-control:has([id$='-num'])").className).toContain("qlive-changed")
    })

    it("keeps text that doesn't parse out of the draft, with an error", async () => {

        const {bar} = await editableBar()
        let form: FormContext | null = null
        function Probe()
        {
            form = useFormContext()
            return <Field path="num"/>
        }
        render(<Form row={ bar }><Probe/></Form>)

        type(fieldInput("num"), "12x")

        expect(bar.num).toBe(1)
        expect(fieldInput("num").value).toBe("12x")
        expect(fieldInput("num").getAttribute("aria-invalid")).toBe("true")
        expect(query(".qlive-field-error").textContent).toBe("[Not a whole number]")
        expect([...form!.invalid]).toEqual([["num", "12x"]])

        type(fieldInput("num"), "13")

        expect(bar.num).toBe(13)
        expect(form!.invalid.size).toBe(0)
        expect(container.querySelector(".qlive-field-error")).toBeNull()
    })

    it("shows a value set elsewhere in place of the text typed for the old one", async () => {

        const {bar} = await editableBar()
        render(<Form row={ bar }/>)

        type(fieldInput("num"), "012")
        act(() => { bar.num = 7 })

        expect(fieldInput("num").value).toBe("7")
    })

    it("starts a field in the mode its node gives and changes it with setMode", async () => {

        const {bar} = await editableBar()
        let form: FormContext | null = null
        function Probe()
        {
            form = useFormContext()
            return null
        }
        registerComponent("Probe", Probe)
        render(<Form row={ bar } layout={ {type: "Bar", nodes: [{field: "name", mode: "readOnly"}, {component: "Probe"}]} }/>)

        expect(fieldInput("name").readOnly).toBe(true)

        act(() => form!.setMode("name", "disabled"))

        expect(fieldInput("name").disabled).toBe(true)
        expect(fieldInput("name").readOnly).toBe(false)

        act(() => form!.setMode("name", "text"))

        expect(query("[id$='-name']").tagName).toBe("SPAN")
        expect(query("[id$='-name']").textContent).toBe("Bar #1")
    })

    it("renders sections and the application's components", async () => {

        const {bar} = await editableBar()
        function Box({children}: { children: React.ReactNode })
        {
            return <div className="box">{ children }</div>
        }
        render(
            <Form row={ bar } layout={ {
                type: "Bar",
                nodes: [{section: "Bar.main", nodes: [{field: "name"}]}, {component: Box, nodes: [{field: "num"}]}]
            } }/>
        )

        expect(query("fieldset legend").textContent).toBe("[Bar.main]")
        expect(query("fieldset [id$='-name']")).toBeTruthy()
        expect(query(".box [id$='-num']")).toBeTruthy()
    })

    it("refuses a layout with children, and a layout of another type", async () => {

        const {bar} = await editableBar()
        vi.spyOn(console, "error").mockImplementation(() => {})

        expect(() => render(<Form row={ bar } layout={ {type: "Bar", nodes: []} }><span/></Form>))
            .toThrowError(/layout or its children/)
        expect(() => render(<Form row={ bar } layout={ {type: "Baz", nodes: []} }/>))
            .toThrowError(/layout of Baz/)
    })

    it("offers the conflict buttons around a layout's widgets", async () => {

        const {ws, row, bar} = await editableBar()
        act(() => { bar.name = "Mine" })
        render(<Form row={ bar }/>)

        act(() => {
            ws.storedState({type: "Bar", id: "bar-1", version: "v9", fields: {name: "Theirs"}})
        })

        const buttons = container.querySelectorAll(".qlive-field-resolve button")
        expect(Array.from(buttons).map(b => b.textContent)).toEqual(["[Yours: {0}:Mine]", "[Saved: {0}:Theirs]"])

        act(() => (buttons[1] as HTMLButtonElement).click())

        expect(fieldInput("name").value).toBe("Theirs")
        expect(ws.accessor(row).field("name").status).toBe("resolved")
    })

    it("offers no conflict buttons in a hand-written form unless given", async () => {

        const {ws, bar} = await editableBar()
        act(() => { bar.name = "Mine" })
        render(<Form row={ bar }><Field path="name"/></Form>)

        act(() => {
            ws.storedState({type: "Bar", id: "bar-1", version: "v9", fields: {name: "Theirs"}})
        })

        expect(container.querySelector(".qlive-field-resolve")).toBeNull()
    })
})


describe("useField", () => {

    function stateOf(path: string, element: (probe: React.ReactNode) => React.ReactNode = p => p): FieldState
    {
        let state: FieldState | null = null
        function Probe()
        {
            state = useField(path)
            return null
        }
        render(element(<Probe/>))
        return state!
    }

    it("resolves a relation to its foreign key", () => {

        const ws = new WorkingSet()
        const foo = ws.create<any>("Foo", {name: "f", ownerId: "u-1"})
        const owner = stateOf("owner", probe => <Form row={ foo }>{ probe }</Form>)

        expect(owner).toMatchObject({kind: "toOne", scalarType: "AppUser", value: "u-1", required: true})
        expect(owner.merge.name).toBe("ownerId")
    })

    it("names a related row by its name fields, matched on the relation's target key", () => {

        const meta = config().meta
        const relation = meta.relations.find(r => r.sourceType === "Foo" && r.leftSideObjectName === "fooType")!
        const targetFields = relation.targetFields
        relation.targetFields = ["name"]
        meta.types.AppUser = {meta: {nameFields: ["login"]}}
        meta.types.FooType = {meta: {nameFields: ["name"]}}
        try
        {
            const row = {ownerId: "u-1", owner: {id: "u-1", login: "admin"}, type: "A", fooType: {name: "A"}}
            const toOne = (name: string, scalarType: string, value: unknown) =>
                ({kind: "toOne", type: "Foo", name, scalarType, value}) as FieldState

            expect(fieldText(toOne("owner", "AppUser", "u-1"), row)).toBe("admin")
            expect(fieldText(toOne("fooType", "FooType", "A"), row)).toBe("A")

            // a key changed away from the relation object the row was read with shows as the key
            expect(fieldText(toOne("owner", "AppUser", "u-2"), row)).toBe("u-2")
        }
        finally
        {
            relation.targetFields = targetFields
            delete meta.types.AppUser
            delete meta.types.FooType
        }
    })

    it("refuses a path through a relation, and a field outside a form", async () => {

        const {bar} = await editableBar()
        vi.spyOn(console, "error").mockImplementation(() => {})

        expect(() => stateOf("bazes.name", probe => <Form row={ bar }>{ probe }</Form>)).toThrowError(/field group/)
        expect(() => stateOf("name")).toThrowError(/inside <Form\/>/)
    })
})


describe("widgets", () => {

    function Stars({field}: { field: FieldState })
    {
        return <span className="stars">{ String(field.value) }</span>
    }

    function Slider({field}: { field: FieldState })
    {
        return <span className="slider">{ String(field.value) }</span>
    }

    it("takes the most specific override, the later of equals", async () => {

        const {bar} = await editableBar()
        config().widgets = [
            {layout: "Bar", field: "num", widget: Slider},
            {layout: "*", scalar: "Int", widget: Stars},
            {layout: "*", field: "num", widget: Stars}
        ]
        render(<Form row={ bar }/>)

        expect(query(".slider").textContent).toBe("1")

        config().widgets = [
            {layout: "*", scalar: "Int", widget: Stars},
            {layout: "*", scalar: "Int", widget: Slider}
        ]
        render(<Form key="again" row={ bar }/>)

        expect(query(".slider").textContent).toBe("1")
        expect(container.querySelector(".stars")).toBeNull()
    })

    it("prefers the widget a field names, by component or name", async () => {

        const {bar} = await editableBar()
        config().widgets = [{layout: "*", widget: Slider}]
        registerWidget("stars", Stars)
        render(
            <Form row={ bar }>
                <Field path="num" widget={ Stars }/>
                <Field path="name" widget="stars"/>
                <Field path="description"/>
            </Form>
        )

        expect(Array.from(container.querySelectorAll(".stars")).map(e => e.textContent)).toEqual(["1", "Bar #1"])
        expect(query(".slider").textContent).toBe("null")
    })
})
