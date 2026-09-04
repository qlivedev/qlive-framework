import {beforeAll, describe, expect, it} from "vitest";
import {Temporal} from "temporal-polyfill";
import {init} from "../src/config";
import {GraphQLQuery} from "../src/GraphQLQuery";
import inject from "../src/inject";
import {QueryDocument} from "../src/QueryDocument";
import {queryResult, testConfig} from "./fixtures/testConfig";

/** T of a query is what one execution of it yields: the value of its one selection */
const Q_Foo = new GraphQLQuery<QueryDocument<any>>(
    `query Q_Foo($config: QueryConfig!) {
        xxx: queryFooDocument(config: $config) {
            type
            config
            rowCount
            rows {
                id
                name
                desc: description
                created
                when: created
                owner {
                    id
                    lastLogin
                }
            }
        }
    }`
)

beforeAll(async () => {
    await init({
        config: testConfig,
        data: {
            Q_Foo: {data: queryResult(), type: "FooDocument", meta: null},
            Second: {data: queryResult(), type: "FooDocument", meta: null}
        }
    })
})

describe("inject", () => {

    it("declares a query before startup(), where there is no schema yet", () => {
        // Q_Foo above was constructed while this module was imported, so getting here
        // at all is the assertion: nothing may touch the config until it is used
        expect(Q_Foo.queryName).toBe("Q_Foo")
    })

    it("converts the injected data along the query it is injected with", () => {
        const doc = inject(Q_Foo)

        expect(doc).toBeInstanceOf(QueryDocument)
        expect(doc.rows[0].created).toBeInstanceOf(Temporal.Instant)
        // "when" is "created" under an alias: the wire data says nothing about that,
        // the query does
        expect(doc.rows[0].when).toBeInstanceOf(Temporal.Instant)
        // and "desc" is "description", a String, so it stays one
        expect(doc.rows[0].desc).toBe("2026-01-02T03:04:05Z")
        expect(doc.rows[0].owner.lastLogin).toBe(null)
    })

    it("converts an injection once, however often it is read", () => {
        expect(inject(Q_Foo)).toBe(inject(Q_Foo))
    })

    it("registers the query with the document, so it can be updated", () => {
        expect(GraphQLQuery.access(inject(Q_Foo))).toBe(Q_Foo)
    })

    it("takes the injection id from __id where the query is injected twice", () => {
        const doc = inject(Q_Foo, {__id: "Second"})

        expect(doc).toBeInstanceOf(QueryDocument)
        expect(doc).not.toBe(inject(Q_Foo))
    })
})
