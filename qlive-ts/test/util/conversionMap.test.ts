import {beforeAll, describe, expect, test} from "vitest";
import {init} from "../../src/config";
import {parseQuery} from "../../src/util/parseQuery";
import {buildConversionMap} from "../../src/util/conversionMap";
import {testConfig, testCsrfToken} from "../fixtures/testConfig";

function mapOf(query: string)
{
    return buildConversionMap(parseQuery(query))
}

beforeAll(async () => {
    await init({config: testConfig, csrfToken: testCsrfToken(), data: {}})
})

describe("buildConversionMap", () => {

    test("resolves the selections against the schema", () => {
        const map = mapOf(`query Q_Foo($config: QueryConfig!) {
            xxx: queryFooDocument(config: $config) {
                type
                rows {
                    name
                    desc: description
                    owner { lastLogin }
                }
            }
        }`);

        expect(map).toEqual({
            variables: {config: "QueryConfig"},
            selections: {
                xxx: {
                    type: "FooDocument",
                    fields: {
                        type: {type: "String"},
                        rows: {
                            type: "Foo",
                            fields: {
                                name: {type: "String"},
                                // the alias the wire data cannot be read without
                                desc: {type: "String"},
                                owner: {
                                    type: "AppUser",
                                    fields: {
                                        lastLogin: {type: "Timestamp"}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        })
    })

    test("keys the nodes by result key, not by field name", () => {
        const map = mapOf(`query Q_Aliased { when: currentUser { at: lastLogin } }`);

        expect(map.selections.when.type).toBe("AppUser")
        expect(map.selections.when.fields).toEqual({at: {type: "Timestamp"}})
    })

    test("strips the modifiers off the variable types", () => {
        const map = mapOf(`query Q_Vars($a: Timestamp!, $b: [String!]!, $c: QueryConfig) { countFoos }`);

        expect(map.variables).toEqual({a: "Timestamp", b: "String", c: "QueryConfig"})
    })

    test("resolves mutations in the mutation type", () => {
        const map = mapOf(`mutation M_Update($foo: FooInput!) { updateFoo(foo: $foo) { created } }`);

        expect(map.selections.updateFoo).toEqual({
            type: "Foo",
            fields: {created: {type: "Timestamp"}}
        })
    })

    test("leaves a leaf without fields", () => {
        expect(mapOf(`query Q_Count { countFoos }`).selections.countFoos).toEqual({type: "Int"})
    })

    test("refuses a query using fragments rather than dropping their fields", () => {
        expect(() => mapOf(`query Q_Frag { queryFooDocument { ...DocFields } }`))
            .toThrowError(/Q_Frag: fragments are not supported/)
    })

    test("names the field the schema does not have", () => {
        expect(() => mapOf(`query Q_Typo { queryFooDocument { rowz } }`))
            .toThrowError(`Field "rowz" not found in FooDocument`)
    })

    test("has nothing to convert in a subscription", () => {
        expect(mapOf(`subscription S_Foo { fooChanged { id } }`)).toEqual({selections: {}, variables: {}})
    })
})
