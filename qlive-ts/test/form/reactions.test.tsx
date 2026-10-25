// @vitest-environment jsdom
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import config, {init, QLiveConfig} from "../../src/config";
import {GraphQLQuery} from "../../src/GraphQLQuery";
import {QueryDocument} from "../../src/QueryDocument";
import {WorkingSet} from "../../src/merge/WorkingSet";
import {Invariant} from "../../src/merge/invariants";
import {field, operation, value} from "../../src/FilterDSL";
import {Form} from "../../src/form/Form";
import {Field} from "../../src/form/Field";
import {FormContext, useFormContext} from "../../src/form/FormContext";
import type {Layout} from "../../src/form/layout";
import {Reaction, registerEffect} from "../../src/form/reactions";
import {barDocument, mergeConfig} from "../fixtures/mergeConfig";
import {respondWith} from "../fixtures/graphqlMock";
import {testAuthentication} from "../fixtures/testConfig";

/**
 * Reactions: Bar's description enabled for a large num, following it, and the like. Bar's num has to be positive,
 * an invariant of the class "numPositive".
 */

const NUM_POSITIVE: Invariant = {
    condition: field("num").gt(value(0, "Int")),
    field: "num",
    messageKey: "Bar.numPositive",
    errorClass: "numPositive"
}

const reactionConfig: QLiveConfig = {
    ...mergeConfig,
    meta: {
        ...mergeConfig.meta,
        types: {
            ...mergeConfig.meta.types,
            Bar: {meta: {...mergeConfig.meta.types.Bar.meta, rules: [NUM_POSITIVE]}}
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

/** a layout of Bar's name, num and description, with the reactions */
function layout(...reactions: Reaction[]): Layout
{
    return {domainType: "Bar", nodes: [{field: "name"}, {field: "num"}, {field: "description"}], reactions}
}

/** description as num's text */
const DESCRIBED_BY_NUM: Reaction = {
    on: [{change: "num"}],
    effects: [{field: "description", value: operation("toString", [field("num")])}]
}

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

function input(path: string): HTMLInputElement
{
    return query<HTMLInputElement>(`[id$="-${path}"]`)
}

/** types into a controlled input the way React notices */
function type(path: string, text: string)
{
    act(() => {
        const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!
        setter.call(input(path), text)
        input(path).dispatchEvent(new Event("input", {bubbles: true}))
    })
}

beforeAll(async () => {
    await init({
        config: reactionConfig,
        csrfToken: reactionConfig.csrfToken!,
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
    vi.restoreAllMocks()
})


describe("a mode toggle", () => {

    const LARGE_DESCRIBED: Reaction = {
        on: [{init: true}, {change: "num"}],
        when: field("num").gt(value(10, "Int")),
        effects: [{field: "description", mode: "normal"}],
        otherwise: [{field: "description", mode: "disabled"}]
    }

    it("holds from the start, and follows the field it reads", async () => {

        const {bar} = await editableBar()
        render(<Form row={ bar } layout={ layout(LARGE_DESCRIBED) }/>)

        // num is 1 when the form opens
        expect(input("description").disabled).toBe(true)

        type("num", "11")
        expect(input("description").disabled).toBe(false)

        type("num", "3")
        expect(input("description").disabled).toBe(true)
    })

    it("is given to a form with children as a prop", async () => {

        const {bar} = await editableBar()
        render(
            <Form row={ bar } reactions={ [LARGE_DESCRIBED] }>
                <Field path="num"/>
                <Field path="description"/>
            </Form>
        )

        expect(input("description").disabled).toBe(true)
        type("num", "11")
        expect(input("description").disabled).toBe(false)
    })

    it("is a layout's alone where the form is given one", async () => {

        const {bar} = await editableBar()
        vi.spyOn(console, "error").mockImplementation(() => {})

        expect(() => render(<Form row={ bar } layout={ layout() } reactions={ [LARGE_DESCRIBED] }/>))
            .toThrow("A layout carries its reactions")
    })
})


describe("a value", () => {

    it("follows the field it reads, as the user changes it", async () => {

        const {bar} = await editableBar()
        render(<Form row={ bar } layout={ layout(DESCRIBED_BY_NUM) }/>)

        // no init event, so the description is as it was read
        expect(bar.description).toBeNull()

        type("num", "42")
        expect(bar.description).toBe("42")
        expect(input("description").value).toBe("42")
    })

    it("is a change like an edit, which the working set sends", async () => {

        const {ws, bar} = await editableBar()
        render(<Form row={ bar } layout={ layout(DESCRIBED_BY_NUM) }/>)

        type("num", "42")

        expect(ws.accessor(bar).changedFields()).toEqual(["description", "num"])
    })

    it("yields to what the user types, and comes back when its source changes", async () => {

        const {bar} = await editableBar()
        let form: FormContext | null = null
        function Probe()
        {
            form = useFormContext()
            return null
        }
        render(
            <Form row={ bar } reactions={ [DESCRIBED_BY_NUM] }>
                <Probe/>
                <Field path="num"/>
                <Field path="description"/>
            </Form>
        )

        type("num", "42")
        expect(form!.reacted("description")).toBe(true)
        expect(query(".qlive-field:has([id$='-description'])").className).toContain("qlive-field-reacted")

        type("description", "my own")
        expect(form!.reacted("description")).toBe(false)
        expect(query(".qlive-field:has([id$='-description'])").className).not.toContain("qlive-field-reacted")

        type("num", "43")
        expect(bar.description).toBe("43")
        expect(form!.reacted("description")).toBe(true)
    })

    it("is forgotten with the working set's undo()", async () => {

        const {ws, bar} = await editableBar()
        let form: FormContext | null = null
        function Probe()
        {
            form = useFormContext()
            return null
        }
        render(
            <Form row={ bar } reactions={ [DESCRIBED_BY_NUM] }>
                <Probe/>
                <Field path="num"/>
                <Field path="description"/>
            </Form>
        )

        type("num", "42")
        act(() => ws.undo())

        expect(bar.description).toBeNull()
        expect(form!.reacted("description")).toBe(false)
    })

    it("clears a field with a null value", async () => {

        const {bar} = await editableBar()
        bar.description = "set"
        const reaction: Reaction = {
            on: [{change: "name"}],
            effects: [{field: "description", value: value(null, "String")}]
        }
        render(<Form row={ bar } layout={ layout(reaction) }/>)

        type("name", "renamed")

        expect(bar.description).toBeNull()
    })
})


describe("an effect", () => {

    it("sets what a function returns, by name from a layout", async () => {

        const {bar} = await editableBar()
        registerEffect("shout", form => ((form.row as any).name as string).toUpperCase())
        const reaction: Reaction = {on: [{change: "name"}], effects: [{field: "description", effect: "shout"}]}
        render(<Form row={ bar } layout={ layout(reaction) }/>)

        type("name", "loud")

        expect(bar.description).toBe("LOUD")
    })

    it("may take its time, and the latest one started wins", async () => {

        const {bar} = await editableBar()
        const pending: { name: string, resolve: (value: string) => void }[] = []
        const lookup = (form: FormContext) => new Promise<string>(resolve => {
            pending.push({name: (form.row as any).name, resolve})
        })
        const reaction: Reaction = {on: [{change: "name"}], effects: [{field: "description", effect: lookup}]}
        render(<Form row={ bar } layout={ layout(reaction) }/>)

        type("name", "first")
        type("name", "second")
        expect(pending.map(p => p.name)).toEqual(["first", "second"])

        await act(async () => pending[1].resolve("from second"))
        expect(bar.description).toBe("from second")

        // the earlier lookup answers late, and is dropped
        await act(async () => pending[0].resolve("from first"))
        expect(bar.description).toBe("from second")
    })

    it("runs when focus leaves a field", async () => {

        const {bar} = await editableBar()
        const reaction: Reaction = {
            on: [{blur: "name"}],
            effects: [{field: "description", value: field("name").upper()}]
        }
        render(<Form row={ bar } layout={ layout(reaction) }/>)

        type("name", "quiet")
        expect(bar.description).toBeNull()

        act(() => {
            input("name").focus()
            input("num").focus()
        })
        expect(bar.description).toBe("QUIET")
    })
})


describe("errors", () => {

    it("set off a reaction where they appear, and another where they clear", async () => {

        const {bar} = await editableBar()
        const reactions: Reaction[] = [
            {on: [{error: "numPositive"}], effects: [{field: "name", mode: "readOnly"}]},
            {on: [{errorAt: "num", cleared: true}], effects: [{field: "name", mode: "normal"}]}
        ]
        render(<Form row={ bar } layout={ layout(...reactions) }/>)
        expect(input("name").readOnly).toBe(false)

        type("num", "-1")
        expect(input("name").readOnly).toBe(true)

        type("num", "5")
        expect(input("name").readOnly).toBe(false)
    })
})


describe("chains", () => {

    it("follow one reaction's value to the next", async () => {

        const {bar} = await editableBar()
        const NAMED_BY_DESCRIPTION: Reaction = {
            on: [{change: "description"}],
            effects: [{field: "name", value: field("description").upper()}]
        }
        render(<Form row={ bar } layout={ layout(DESCRIBED_BY_NUM, NAMED_BY_DESCRIPTION) }/>)

        type("num", "7")

        expect(bar.description).toBe("7")
        expect(bar.name).toBe("7")
    })

    it("settle where a value comes back equal to the one there is", async () => {

        const {bar} = await editableBar()
        const error = vi.spyOn(console, "error").mockImplementation(() => {})
        // a two-way link: each field follows the other
        const reactions: Reaction[] = [
            {on: [{change: "name"}], effects: [{field: "description", value: field("name")}]},
            {on: [{change: "description"}], effects: [{field: "name", value: field("description")}]}
        ]
        render(<Form row={ bar } layout={ layout(...reactions) }/>)

        type("name", "same")

        expect(bar.description).toBe("same")
        expect(error).not.toHaveBeenCalled()
    })

    it("are stopped where they don't settle", async () => {

        const {bar} = await editableBar()
        const error = vi.spyOn(console, "error").mockImplementation(() => {})
        // each one makes the other longer
        const reactions: Reaction[] = [
            {on: [{change: "name"}], effects: [{field: "description", value: field("name").concat(value("+"))}]},
            {on: [{change: "description"}], effects: [{field: "name", value: field("description")}]}
        ]
        render(<Form row={ bar } layout={ layout(...reactions) }/>)

        type("name", "x")

        expect(error).toHaveBeenCalledWith(expect.stringContaining("Reactions don't settle"))
    })
})


describe("references", () => {

    it("are checked when the form shows its row", async () => {

        const {bar} = await editableBar()
        vi.spyOn(console, "error").mockImplementation(() => {})
        const reaction: Reaction = {on: [{change: "nope"}], effects: []}

        expect(() => render(<Form row={ bar } layout={ layout(reaction) }/>)).toThrow("Field \"nope\" of Bar")
    })
})
