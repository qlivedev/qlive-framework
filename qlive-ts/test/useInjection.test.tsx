// @vitest-environment jsdom
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import {init} from "../src/config";
import {GraphQLQuery} from "../src/GraphQLQuery";
import {useInjection} from "../src/useInjection";
import {QueryDocumentSnapshot} from "../src/QueryDocument";
import {fooDocument, testConfig} from "./fixtures/testConfig";
import {respondWith} from "./fixtures/graphqlMock";

type Row = { id: string, name: string }

const Q_Foo = new GraphQLQuery<QueryDocumentSnapshot<Row>>(
    `query Q_Foo($config: QueryConfig!) {
        xxx: queryFooDocument(config: $config) {
            type
            config
            rowCount
            rows {
                id
                name
            }
        }
    }`
)

function injectionOf(name: string, offset: number = 0)
{
    const doc = fooDocument()

    return {
        xxx: {
            ... doc,
            config: {... doc.config, offset},
            rows: [{... doc.rows[0], name}]
        }
    }
}

/** the snapshot the last render of a view was given, per view name */
const rendered = new Map<string, QueryDocumentSnapshot<Row>>()
/** how often each view rendered */
const renderCount = new Map<string, number>()

function FooView({name}: { name: string })
{
    const foos = useInjection(Q_Foo)

    rendered.set(name, foos)
    renderCount.set(name, (renderCount.get(name) ?? 0) + 1)

    return (
        <ul>
            {foos.rows.map(row => <li key={row.id}>{row.name}</li>)}
        </ul>
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
        config: testConfig,
        data: {
            Q_Foo: {data: injectionOf("Foo #1"), type: "FooDocument", meta: null}
        }
    })
})

beforeEach(() => {
    // react-dom asks for this before it will let act() drive a render
    vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true)

    container = document.createElement("div")
    document.body.appendChild(container)
    root = createRoot(container)

    rendered.clear()
    renderCount.clear()
})

afterEach(() => {
    act(() => {
        root.unmount()
    })
    container.remove()
    vi.unstubAllGlobals()
})

describe("useInjection", () => {

    it("renders the data the server injected for the query", () => {
        render(<FooView name="one"/>)

        expect(container.textContent).toBe("Foo #1")
        expect(rendered.get("one")!.type).toBe("Foo")
    })

    it("hands every view the same snapshot of one injection", () => {
        render(<><FooView name="one"/><FooView name="two"/></>)

        expect(rendered.get("one")).toBe(rendered.get("two"))
    })

    it("re-renders every view of a document when it updates", async () => {
        render(<><FooView name="one"/><FooView name="two"/></>)

        const before = rendered.get("one")!

        respondWith({data: injectionOf("Foo #2", 10), errors: []})
        await act(async () => {
            await before.update({offset: 10})
        })

        expect(container.textContent).toBe("Foo #2Foo #2")
        expect(rendered.get("one")).not.toBe(before)
        expect(rendered.get("one")).toBe(rendered.get("two"))
        expect(rendered.get("one")!.config.offset).toBe(10)
    })

    it("hands out the same snapshot again where the document did not change", () => {
        render(<FooView name="one"/>)
        const first = rendered.get("one")

        render(<FooView name="one"/>)

        // the view rendered twice, but on the same object both times -- which is what
        // keeps a memoized child or an effect dependency from churning
        expect(renderCount.get("one")).toBe(2)
        expect(rendered.get("one")).toBe(first)
    })
})
