import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import {init} from "../src/config";
import {GraphQLQuery} from "../src/GraphQLQuery";
import {QueryDocument} from "../src/QueryDocument";
import {fooDocument, testConfig, testCsrfToken} from "./fixtures/testConfig";
import {respondWith, sentVariables} from "./fixtures/graphqlMock";

const Q_Doc = new GraphQLQuery<QueryDocument<any>>(
    `query Q_Doc($config: QueryConfig!) {
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

/**
 * A response carrying the fixture document with the given rows, under the config the
 * server would have echoed back.
 */
function documentWith(name: string, offset: number = 0)
{
    const doc = fooDocument()

    return {
        data: {
            xxx: {
                ... doc,
                config: {... doc.config, offset},
                rows: [{... doc.rows[0], name}]
            }
        },
        errors: []
    }
}

/** a document as one execution of Q_Doc produces it, with Q_Doc registered on it */
async function executed()
{
    respondWith(documentWith("Foo #1"))
    return await Q_Doc.execute({config: {}})
}

beforeAll(async () => {
    await init({config: testConfig, csrfToken: testCsrfToken(), data: {}})
})

afterEach(() => {
    vi.unstubAllGlobals()
})

describe("QueryDocument", () => {

    it("hands out the same snapshot as long as nothing changes", async () => {
        const doc = await executed()

        expect(doc.getSnapshot()).toBe(doc.getSnapshot())
        expect(doc.getSnapshot().rows).toBe(doc.rows)
    })

    it("re-executes its query with the merged config", async () => {
        const doc = await executed()

        const fetchMock = respondWith(documentWith("Foo #2", 10))
        await doc.update({offset: 10})

        // pageSize and the rest come from the config the document was executed with,
        // only offset is what the caller asked to change
        expect(sentVariables(fetchMock).config).toEqual({
            offset: 10,
            pageSize: 10,
            condition: null,
            sortFields: []
        })
    })

    it("gives every change a new snapshot and leaves the old one alone", async () => {
        const doc = await executed()
        const before = doc.getSnapshot()

        respondWith(documentWith("Foo #2", 10))
        const after = await doc.update({offset: 10})

        expect(after).not.toBe(before)
        expect(after).toBe(doc.getSnapshot())
        expect(after.rows[0].name).toBe("Foo #2")
        expect(after.config.offset).toBe(10)

        // what a view rendered from the old snapshot still describes what it rendered
        expect(before.rows[0].name).toBe("Foo #1")
        expect(before.config.offset).toBe(0)
    })

    it("tells its subscribers about a change", async () => {
        const doc = await executed()
        const seen = vi.fn()
        doc.subscribe(seen)

        respondWith(documentWith("Foo #2", 10))
        await doc.update({offset: 10})

        expect(seen).toHaveBeenCalledTimes(1)
        // the subscriber is told that something changed, and reads the change itself
        expect(doc.getSnapshot().rows[0].name).toBe("Foo #2")
    })

    it("stops telling a subscriber that unsubscribed", async () => {
        const doc = await executed()
        const seen = vi.fn()
        doc.subscribe(seen)()

        respondWith(documentWith("Foo #2", 10))
        await doc.update({offset: 10})

        expect(seen).not.toHaveBeenCalled()
    })

    it("carries update() on the snapshot, so what a view holds can move it on", async () => {
        const doc = await executed()
        const snapshot = doc.getSnapshot()

        respondWith(documentWith("Foo #2", 10))
        const after = await snapshot.update({offset: 10})

        expect(after).toBe(doc.getSnapshot())
        expect(after.rows[0].name).toBe("Foo #2")
    })

    it("refuses to update a document no query was registered with", async () => {
        const doc = new QueryDocument("Foo", fooDocument().config, [], 0)

        await expect(doc.update({offset: 10})).rejects.toThrowError(/no GraphQLQuery registered/)
    })
})
