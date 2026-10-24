// @vitest-environment jsdom
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import config, {init, QLiveConfig} from "../../src/config";
import {issueAction, Refusal, Subject} from "../../src/action";
import {GraphQLQuery} from "../../src/GraphQLQuery";
import {QueryDocument} from "../../src/QueryDocument";
import {WorkingSet} from "../../src/merge/WorkingSet";
import {Form} from "../../src/form/Form";
import {Field} from "../../src/form/Field";
import {ActionRefusals} from "../../src/form/ActionRefusals";
import type {FieldState} from "../../src/form/useField";
import {deriveLayout} from "../../src/form/layout";
import {ActionDraft, useActionDraft, useRefusals} from "../../src/useActionDraft";
import {barDocument, mergeConfig} from "../fixtures/mergeConfig";
import {respondWith, sentVariables} from "../fixtures/graphqlMock";
import {
    field,
    inputObject,
    inputValue,
    NAMED,
    NOT_NULL,
    object,
    STRING,
    testAuthentication
} from "../fixtures/testConfig";

/**
 * A form over an action: its row fills a subject, its other fields are the action's parameters, named by the
 * parameter, and each field shows the refusals that blame it.
 */

declare module "../../src/action" {
    interface Actions {
        signBar: {
            args: { bar: Subject, comment: string, notify: boolean, address?: { street: string } | null },
            result: boolean
        }
        moveBar: { args: { bar: Subject, target: Subject }, result: boolean }
        renameBar: { args: { bar: Subject, name: string }, result: boolean }
    }
}

const SUBJECT = NOT_NULL(NAMED("SubjectRefInput", "INPUT_OBJECT"))

const actionConfig: QLiveConfig = {
    ...mergeConfig,
    schema: {
        types: [
            ...mergeConfig.schema.types.filter(t => t.name !== "MutationType"),
            inputObject("SubjectRefInput", [
                inputValue("id", NOT_NULL(STRING)),
                inputValue("version", STRING)
            ]),
            inputObject("AddressInput", [
                inputValue("street", NOT_NULL(STRING))
            ]),
            object("MutationType", [
                ...mergeConfig.schema.types.find(t => t.name === "MutationType")!.fields!,
                {
                    ...field("signBar", NOT_NULL(NAMED("Boolean"))),
                    args: [
                        inputValue("bar", SUBJECT),
                        inputValue("comment", NOT_NULL(STRING)),
                        inputValue("notify", NOT_NULL(NAMED("Boolean"))),
                        inputValue("address", NAMED("AddressInput", "INPUT_OBJECT"))
                    ]
                },
                {
                    ...field("moveBar", NOT_NULL(NAMED("Boolean"))),
                    args: [inputValue("bar", SUBJECT), inputValue("target", SUBJECT)]
                },
                {
                    ...field("renameBar", NOT_NULL(NAMED("Boolean"))),
                    args: [inputValue("bar", SUBJECT), inputValue("name", NOT_NULL(STRING))]
                }
            ])
        ]
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

let container: HTMLElement
let root: Root

function render(element: React.ReactNode)
{
    act(() => {
        root.render(element)
    })
}

function input(path: string): HTMLInputElement
{
    const found = container.querySelector<HTMLInputElement>(`[id$="-${path}"]`)
    if (!found)
    {
        throw new Error("No input for " + path)
    }
    return found
}

/** types into a controlled input the way React notices */
function type(target: HTMLInputElement, text: string)
{
    act(() => {
        const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!
        setter.call(target, text)
        target.dispatchEvent(new Event("input", {bubbles: true}))
    })
}

async function editableBar()
{
    respondWith({data: {queryBarDocument: barDocument()}, errors: []})

    const document = await Q_BARS.execute({config: {offset: 0, pageSize: 10, condition: null, sortFields: []}})
    const ws = new WorkingSet()
    ws.register(document)

    return {ws, bar: ws.edit(document.rows[0]) as any}
}

function refusal(messageKey: string, field: string | null): Refusal
{
    return {code: "RULE", messageKey, field, errorClass: null}
}

/** answers the next request with a refusal of the given reasons */
function refuseWith(...refusals: Refusal[])
{
    respondWith({
        data: null,
        errors: refusals.map(r => ({
            message: r.messageKey,
            path: ["signBar"],
            extensions: {...r, classification: "REFUSED"}
        }))
    })
}

/** the draft the last render of a view was given */
let sign: ActionDraft<"signBar">

beforeAll(async () => {
    await init({
        config: actionConfig,
        csrfToken: actionConfig.csrfToken!,
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
    config().translations = {"signBar.comment": "Comment", "Bar.description": "Description"}
})

afterEach(() => {
    act(() => {
        root.unmount()
    })
    container.remove()
    vi.unstubAllGlobals()
})


describe("deriveLayout with an action", () => {

    it("adds the action's input parameters a field edits, and nothing else of it", async () => {
        const {bar} = await editableBar()

        const layout = deriveLayout("Bar", bar, "signBar")

        expect(layout.action).toBe("signBar")
        expect(layout.nodes.map(node => (node as any).field).slice(-2)).toEqual(["comment", "notify"])
    })

    it("lays out the parameters alone without a row", () => {
        expect(deriveLayout(null, null, "signBar")).toEqual({
            domainType: null,
            action: "signBar",
            nodes: [{field: "comment"}, {field: "notify"}]
        })
    })
})


describe("a form with an action", () => {

    function SignView({bar, form = true}: { bar: object, form?: boolean })
    {
        sign = useActionDraft("signBar")
        return (
            <>
                { form && <Form row={ bar } action={ sign }/> }
                <ActionRefusals action={ sign }/>
            </>
        )
    }

    it("fills its subject with the row while it shows it", async () => {
        const {bar} = await editableBar()

        render(<SignView bar={ bar }/>)
        expect(sign.args.bar).toBe(bar)
        expect(sign.missing).toEqual(["comment"])

        render(<SignView bar={ bar } form={ false }/>)
        expect(sign.args.bar).toBeNull()
    })

    it("edits the action's parameters by their names", async () => {
        const {bar} = await editableBar()
        render(<SignView bar={ bar }/>)

        expect(container.querySelector(`label[for$="-comment"]`)!.textContent).toBe("Comment")
        type(input("comment"), "fine")

        expect(sign.args.comment).toBe("fine")
        expect(sign.complete).toBe(true)
        // the row is not what the parameter is written to
        expect(bar.comment).toBeUndefined()
    })

    it("holds the action back while the row has changes that aren't saved", async () => {
        const {bar} = await editableBar()
        render(<SignView bar={ bar }/>)
        type(input("comment"), "fine")

        type(input("name"), "Bar #1 renamed")

        expect(sign.unsaved).toEqual(["bar"])
        expect(sign.complete).toBe(false)
        await expect(sign.issue()).rejects.toThrowError(/save 'bar' first/)
    })

    it("shows each refusal at the field it blames, and the rest apart", async () => {
        const {bar} = await editableBar()
        render(<SignView bar={ bar }/>)
        type(input("comment"), "ok")

        refuseWith(
            refusal("Bar.descriptionRequired", "bar.description"),
            refusal("Bar.commentRequired", "comment"),
            refusal("Bar.tooMany", null)
        )
        await act(() => sign.issue())

        const refusalsAt = (path: string) =>
            Array.from(input(path).closest(".qlive-field")!.querySelectorAll(".qlive-refusals li"), li => li.textContent)

        expect(refusalsAt("description")).toEqual(["[Bar.descriptionRequired]"])
        expect(refusalsAt("comment")).toEqual(["[Bar.commentRequired]"])
        expect(sign.unclaimed).toEqual([refusal("Bar.tooMany", null)])
        expect(Array.from(container.querySelectorAll(":scope > .qlive-refusals li"), li => li.textContent))
            .toEqual(["[Bar.tooMany]"])
    })

    it("names a field inside an input object by its path", async () => {
        const {bar} = await editableBar()

        function AddressView()
        {
            sign = useActionDraft("signBar")
            return <Form row={ bar } action={ sign }><Field path="address.street"/></Form>
        }
        render(<AddressView/>)

        type(input("address.street"), "Main St")

        expect(sign.args.address).toEqual({street: "Main St"})
    })

    it("edits the parameters alone without a row", () => {
        function CommentView()
        {
            sign = useActionDraft("signBar")
            return <Form action={ sign }><Field path="comment"/></Form>
        }
        render(<CommentView/>)

        type(input("comment"), "fine")

        expect(sign.args.comment).toBe("fine")
        expect(sign.missing).toEqual(["bar"])
    })

    it("refuses what a path can't be", async () => {
        const {bar} = await editableBar()

        function Probe({action, path, row, subject}: { action: any, path: string, row?: object, subject?: string })
        {
            const draft = useActionDraft(action)
            return <Form row={ row } action={ draft } subject={ subject }><Field path={ path }/></Form>
        }
        const silenced = vi.spyOn(console, "error").mockImplementation(() => {})

        // a row field and a parameter of one name
        expect(() => render(<Probe action="renameBar" row={ bar } path="name"/>)).toThrowError(/can't tell apart/)
        // another subject is a row the view sets
        expect(() => render(<Probe action="moveBar" row={ bar } subject="bar" path="target"/>))
            .toThrowError(/a subject/)
        // two subjects, and the form doesn't say which its row fills
        expect(() => render(<Probe action="moveBar" row={ bar } path="name"/>)).toThrowError(/subject="\.\.\."/)
        // without a row, there are only parameters
        expect(() => render(<Probe action="signBar" path="name"/>)).toThrowError(/names none/)

        silenced.mockRestore()
    })
})


describe("widget overrides with an action", () => {

    function Stars({field}: { field: FieldState })
    {
        return <span className="stars">{ String(field.value) }</span>
    }

    function Slider({field}: { field: FieldState })
    {
        return <span className="slider">{ String(field.value) }</span>
    }

    /** the class of the widget the field of the given path renders, by its label */
    function widgetAt(path: string): string | null
    {
        const field = container.querySelector(`label[for$="-${path}"]`)!.closest(".qlive-field")!
        return field.querySelector(".stars") ? "stars" : field.querySelector(".slider") ? "slider" : null
    }

    function SignForm({bar}: { bar?: object })
    {
        sign = useActionDraft("signBar")
        return <Form row={ bar } action={ sign }/>
    }

    it("matches the parameters of the action it names, and the row's fields by their type", async () => {
        const {bar} = await editableBar()
        config().widgets = [
            {action: "signBar", path: "comment", widget: Stars},
            {domainType: "Bar", path: "name", widget: Slider},
            // a parameter is no field of the row, and a row's field none of the action
            {domainType: "Bar", path: "comment", widget: Slider},
            {action: "signBar", path: "name", widget: Stars}
        ]
        render(<SignForm bar={ bar }/>)

        expect(widgetAt("comment")).toBe("stars")
        expect(widgetAt("name")).toBe("slider")
    })

    it("matches without a row, and not for another action", () => {
        config().widgets = [
            {action: "renameBar", path: "comment", widget: Slider},
            {action: "signBar", fieldType: "Boolean", widget: Stars}
        ]
        render(<SignForm/>)

        expect(widgetAt("comment")).toBeNull()
        expect(widgetAt("notify")).toBe("stars")
    })

    it("ranks an action above a domain type and below a layout", async () => {
        const {bar} = await editableBar()
        config().widgets = [
            {action: "signBar", fieldType: "String", widget: Stars},
            {fieldType: "String", widget: Slider}
        ]
        render(<SignForm bar={ bar }/>)

        // the comment is the action's, the row's Strings are nobody's in particular
        expect(widgetAt("comment")).toBe("stars")
        expect(widgetAt("name")).toBe("slider")

        function LayoutForm()
        {
            sign = useActionDraft("signBar")
            const layout = {name: "signing", domainType: "Bar", action: "signBar", nodes: [{field: "comment"}]}
            return <Form row={ bar } action={ sign } layout={ layout }/>
        }
        config().widgets = [
            {layout: "signing", path: "comment", widget: Slider},
            {action: "signBar", path: "comment", widget: Stars}
        ]
        render(<LayoutForm/>)

        expect(widgetAt("comment")).toBe("slider")
    })

    it("refuses an action next to another key of where", async () => {
        const {bar} = await editableBar()
        const silenced = vi.spyOn(console, "error").mockImplementation(() => {})

        config().widgets = [{action: "signBar", domainType: "Bar", widget: Slider}]
        expect(() => render(<SignForm bar={ bar }/>)).toThrowError(/one of a layout, a domain type and an action/)

        silenced.mockRestore()
    })
})


describe("what an action wrote", () => {

    function wrote(prev: string)
    {
        return respondWith({
            data: {signBar: true},
            extensions: {
                written: [{
                    type: "Bar",
                    id: "bar-1",
                    version: "v2",
                    prev,
                    deleted: false,
                    fields: {
                        description: {type: "String", value: "by the action"},
                        // not selected, so nothing the working set holds
                        approved: {type: "Timestamp", value: "2026-10-24T12:00:00.000Z"}
                    },
                    links: []
                }]
            }
        })
    }

    it("moves a working set holding the row on to the version the action left", async () => {
        const {ws, bar} = await editableBar()

        wrote("v1")
        await issueAction("signBar", {bar, comment: "ok", notify: false})

        expect(bar.version).toBe("v2")
        expect(bar.description).toBe("by the action")
        expect(ws.accessor(bar).field("description").status).toBe("remoteChanged")
        expect("approved" in bar).toBe(false)

        // the next action holds to it
        const fetchMock = respondWith({data: {signBar: true}})
        await issueAction("signBar", {bar, comment: "again", notify: false})
        expect(sentVariables(fetchMock).bar).toEqual({id: "bar-1", version: "v2"})
    })

    it("leaves the version where the action wrote over somebody else's write", async () => {
        const {bar} = await editableBar()

        wrote("v1b")
        await issueAction("signBar", {bar, comment: "ok", notify: false})

        expect(bar.version).toBe("v1")
        expect(bar.description).toBe("by the action")
    })
})


describe("useRefusals", () => {

    it("claims the refusals it shows", async () => {
        const {bar} = await editableBar()
        let shown: Refusal[] = []

        function Reasons()
        {
            shown = useRefusals(sign, "bar")
            return null
        }

        function View()
        {
            sign = useActionDraft("signBar", {bar, comment: "fine"})
            return <Reasons/>
        }
        render(<View/>)

        refuseWith(refusal("Action.subjectChanged", "bar"))
        await act(() => sign.issue())

        expect(shown).toEqual([refusal("Action.subjectChanged", "bar")])
        expect(sign.unclaimed).toEqual([])
    })
})
