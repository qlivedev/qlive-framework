// @vitest-environment jsdom
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import config, {addFixture, init, initFixture, isFixture, QLiveConfig, QLiveFixture} from "../../src/config";
import {GraphQLQuery} from "../../src/GraphQLQuery";
import {useInjection} from "../../src/useInjection";
import {QueryDocumentSnapshot} from "../../src/QueryDocument";
import FixtureScope from "../../src/component/FixtureScope";
import {fooDocument, object, testAuthentication, testConfig, testCsrfToken} from "../fixtures/testConfig";

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

/** a view of each of two routes, injecting the same query, as qlive-test's grid views do */
function FooView()
{
    const foos = useInjection(Q_Foo)
    return <>{foos.rows.map(row => <i key={row.id}>{row.name}</i>)}</>
}

/** a copy, since init() writes into the config it is given */
function configOf(types: QLiveConfig["schema"]["types"] = testConfig.schema.types): QLiveConfig
{
    return {...testConfig, schema: {types: [...types]}}
}

/**
 * A fixture for the given route, holding one Foo of the given name.
 */
function fixture(route: string, name: string, cfg: QLiveConfig | null = configOf()): QLiveFixture
{
    const doc = fooDocument()
    return {
        config: cfg,
        csrfToken: testCsrfToken(),
        authentication: testAuthentication(),
        data: {
            [route + "/Q_Foo"]: {
                data: {xxx: {...doc, rows: [{...doc.rows[0], name}], rowCount: 1}},
                type: "FooDocument",
                meta: null
            }
        },
        route
    }
}

let container: HTMLElement
let root: Root

function render(element: React.ReactNode)
{
    act(() => {
        root.render(element)
    })
}

beforeEach(async () => {
    vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true)
    container = document.createElement("div")
    document.body.appendChild(container)
    root = createRoot(container)

    // a page that has run nothing yet, as far as the fixtures go
    await initFixture({...fixture("home", "unused"), data: {}})
})

afterEach(() => {
    act(() => {
        root.unmount()
    })
    container.remove()
    vi.unstubAllGlobals()
})

describe("FixtureScope", () => {

    it("keeps two views on different routes over one query on their own data", () => {
        render(
            <>
                <FixtureScope fixture={fixture("grid/sorting", "Sorted")}>
                    <FooView/>
                </FixtureScope>
                <FixtureScope fixture={fixture("grid/filters", "Filtered")}>
                    <FooView/>
                </FixtureScope>
            </>
        )

        expect(container.textContent).toBe("SortedFiltered")
    })

    it("initializes QLive on its fixture where nothing else has", async () => {
        vi.resetModules()
        const fresh = await import("../../src/config")
        const {default: FreshScope} = await import("../../src/component/FixtureScope")
        const {useInjection: freshUseInjection} = await import("../../src/useInjection")

        function FreshView()
        {
            return <>{freshUseInjection(Q_Foo).rows[0].name}</>
        }

        render(
            <FreshScope fixture={fixture("grid/sorting", "Sorted")}>
                <FreshView/>
            </FreshScope>
        )

        expect(container.textContent).toBe("Sorted")
        expect(fresh.isFixture()).toBe(true)
    })
})

describe("addFixture", () => {

    it("takes the same fixture twice", async () => {
        await addFixture(fixture("grid/sorting", "Sorted"))
        await expect(addFixture(fixture("grid/sorting", "Sorted"))).resolves.toBe(config())
    })

    it("refuses another recording for a route the page already holds", async () => {
        await addFixture(fixture("grid/sorting", "Sorted"))

        expect(() => addFixture(fixture("grid/sorting", "Sorted again"))).toThrow(/'grid\/sorting\/Q_Foo'/)
    })

    it("refuses a fixture of another schema, naming the type", async () => {
        const other = configOf([...testConfig.schema.types, object("Extra", [])])

        expect(() => addFixture(fixture("grid/sorting", "Sorted", other))).toThrow(/'Extra'/)
    })

    it("refuses a page that runs on a server", async () => {
        await init({...fixture("home", "Live"), data: {}})

        expect(isFixture()).toBe(false)
        expect(() => addFixture(fixture("grid/sorting", "Sorted"))).toThrow(/runs on a server/)
    })

    it("keeps the full config over a reduced one, whichever comes first", async () => {
        const full = config()

        await addFixture(fixture("login", "Reduced", configOf([])))
        expect(config()).toBe(full)

        await initFixture(fixture("login", "Reduced", configOf([])))
        expect(config().schema.types).toEqual([])
        const token = config().csrfToken

        await addFixture(fixture("grid/sorting", "Sorted"))
        expect(config().schema.types.length).toBe(testConfig.schema.types.length)
        // the token stays the one of the fixture that initialized the page
        expect(config().csrfToken).toBe(token)
        expect(config().typesByName!.has("Foo")).toBe(true)
    })
})
