import {beforeAll, describe, expect, it} from "vitest";
import {Temporal} from "temporal-polyfill";
import {init, QLiveConfig} from "../src/config";
import {
    convertResultFromServer,
    convertSelectionFromServer,
    convertToServer,
    convertVariablesToServer,
    getConverter,
    QueryConversionMap,
    registerConverter
} from "../src/converter";
import {QueryDocument} from "../src/QueryDocument";
import data from "../src/data";
import {
    GraphQLField,
    GraphQLInputObjectType,
    GraphQLInputValue,
    GraphQLObjectType,
    GraphQLScalarType,
    GraphQLTypeRef
} from "../src/GraphQLSchema";

function scalar(name: string): GraphQLScalarType
{
    return {
        kind: "SCALAR",
        name,
        description: null,
        fields: null,
        inputFields: null,
        interfaces: null,
        enumValues: null,
        possibleTypes: null
    }
}

function field(name: string, type: GraphQLTypeRef): GraphQLField
{
    return {name, description: null, args: [], type, isDeprecated: false, deprecationReason: null}
}

function object(name: string, fields: GraphQLField[]): GraphQLObjectType
{
    return {
        kind: "OBJECT",
        name,
        description: null,
        fields,
        inputFields: null,
        interfaces: [],
        enumValues: null,
        possibleTypes: null
    }
}

function inputValue(name: string, type: GraphQLTypeRef): GraphQLInputValue
{
    return {name, description: null, type, defaultValue: null}
}

function inputObject(name: string, inputFields: GraphQLInputValue[]): GraphQLInputObjectType
{
    return {
        kind: "INPUT_OBJECT",
        name,
        description: null,
        fields: null,
        inputFields,
        interfaces: null,
        enumValues: null,
        possibleTypes: null
    }
}

const NAMED = (name: string, kind: "SCALAR" | "OBJECT" = "SCALAR"): GraphQLTypeRef => ({kind, name})
const NOT_NULL = (ofType: GraphQLTypeRef): GraphQLTypeRef => ({kind: "NON_NULL", name: null, ofType})
const LIST_OF = (ofType: GraphQLTypeRef): GraphQLTypeRef => ({kind: "LIST", name: null, ofType})

const TIMESTAMP = NAMED("Timestamp")
const STRING = NAMED("String")

const testConfig: QLiveConfig = {
    contextPath: "/",
    schema: {
        types: [
            scalar("String"),
            scalar("Int"),
            scalar("Timestamp"),
            scalar("QueryConfig"),
            object("AppUser", [
                field("id", NOT_NULL(STRING)),
                field("lastLogin", TIMESTAMP)
            ]),
            object("Foo", [
                field("id", NOT_NULL(STRING)),
                field("name", NOT_NULL(STRING)),
                field("created", NOT_NULL(TIMESTAMP)),
                field("owner", NAMED("AppUser", "OBJECT"))
            ]),
            inputObject("FooInput", [
                inputValue("name", NOT_NULL(STRING)),
                inputValue("created", NOT_NULL(TIMESTAMP)),
                inputValue("seenAt", LIST_OF(TIMESTAMP)),
                inputValue("config", NAMED("QueryConfig"))
            ]),
            object("FooDocument", [
                field("type", STRING),
                field("config", NAMED("QueryConfig")),
                field("rows", LIST_OF(NAMED("Foo", "OBJECT"))),
                field("rowCount", NAMED("Int"))
            ])
        ]
    },
    meta: {
        types: {},
        genericTypes: [
            {
                type: "FooDocument",
                typeParameters: ["Foo"],
                genericType: "de.quinscape.qlive.model.QueryDocument"
            }
        ],
        relations: []
    }
}

function fooDocument()
{
    return {
        type: "Foo",
        config: {offset: 0, pageSize: 10, condition: null, sortFields: []},
        rowCount: 1,
        rows: [
            {
                id: "abc",
                name: "Foo #1",
                created: "2026-09-04T10:15:30Z",
                desc: "aliased, no field of that name",
                owner: {
                    id: "user-1",
                    lastLogin: null
                }
            }
        ]
    }
}

beforeAll(async () => {
    await init({
        config: testConfig,
        data: {
            Q_Foo: {data: queryResult(), type: "FooDocument", meta: null, conversion: Q_FOO_MAP}
        }
    })
})

/**
 * Q_Foo as the server would generate it from the query source: the top-level
 * selection is aliased to "xxx", "description" is selected as "desc" and the rows
 * carry a Timestamp. None of that is recoverable from the wire data alone.
 */
const Q_FOO_MAP: QueryConversionMap = {
    selections: {
        xxx: {
            type: "FooDocument",
            fields: {
                type: {type: "String"},
                config: {type: "QueryConfig"},
                rowCount: {type: "Int"},
                rows: {
                    type: "Foo",
                    fields: {
                        id: {type: "String"},
                        name: {type: "String"},
                        desc: {type: "Timestamp"},
                        created: {type: "Timestamp"},
                        owner: {
                            type: "AppUser",
                            fields: {
                                id: {type: "String"},
                                lastLogin: {type: "Timestamp"}
                            }
                        }
                    }
                }
            }
        }
    },
    variables: {
        config: "QueryConfig",
        since: "Timestamp",
        foo: "FooInput"
    }
}

function queryResult()
{
    return {
        xxx: {
            ...fooDocument(),
            rows: [{...fooDocument().rows[0], desc: "2026-01-02T03:04:05Z"}]
        }
    }
}

describe("result conversion", () => {

    it("converts scalars with the converter registered for their type", () => {
        const instant = convertSelectionFromServer<Temporal.Instant>(
            "2026-09-04T10:15:30Z",
            {type: "Timestamp"}
        )

        expect(instant).toBeInstanceOf(Temporal.Instant)
        expect(instant.toString()).toBe("2026-09-04T10:15:30Z")
    })

    it("passes null and undefined through without invoking the converter", () => {
        expect(convertSelectionFromServer(null, {type: "Timestamp"})).toBe(null)
        expect(convertSelectionFromServer(undefined, {type: "Timestamp"})).toBe(undefined)
    })

    it("leaves values of types without converter alone", () => {
        expect(convertSelectionFromServer("xxx", {type: "String"})).toBe("xxx")
    })

    it("converts aliased fields, which needs the map rather than the schema", () => {
        // "desc" is "description" selected under an alias -- the wire data has no
        // record of that, the map does
        const doc = convertSelectionFromServer<any>(queryResult().xxx, Q_FOO_MAP.selections.xxx)

        expect(doc.rows[0].desc).toBeInstanceOf(Temporal.Instant)
    })

    it("converts a single selection, as the injection path needs it", () => {
        const doc = convertSelectionFromServer<QueryDocument<any>>(
            queryResult().xxx,
            Q_FOO_MAP.selections.xxx
        )

        expect(doc).toBeInstanceOf(QueryDocument)
        expect(doc.type).toBe("Foo")
        expect(doc.rowCount).toBe(1)
        // the rows are converted before the document is created
        expect(doc.rows[0].created).toBeInstanceOf(Temporal.Instant)
        // null field of a converted type stays null
        expect(doc.rows[0].owner.lastLogin).toBe(null)
    })

    it("walks into lists", () => {
        const raw = queryResult().xxx
        raw.rows = [raw.rows[0], {...raw.rows[0], id: "def", created: "2026-09-05T08:00:00Z"}]

        const doc = convertSelectionFromServer<any>(raw, Q_FOO_MAP.selections.xxx)

        expect(doc.rows).toHaveLength(2)
        expect(doc.rows[0].created).toBeInstanceOf(Temporal.Instant)
        expect(doc.rows[1].created.toString()).toBe("2026-09-05T08:00:00Z")
    })

    it("converts a result by result key, as the runtime path needs it", () => {
        const result = convertResultFromServer<any>(
            {...queryResult(), other: {untouched: true}},
            Q_FOO_MAP
        )

        expect(result.xxx).toBeInstanceOf(QueryDocument)
        // a result key the map does not mention is passed through
        expect(result.other).toEqual({untouched: true})
    })

    it("needs no schema, so it converts types the schema does not have", () => {
        const converted = convertSelectionFromServer<any>(
            {when: "2026-01-02T03:04:05Z"},
            {type: "SomethingUnknown", fields: {when: {type: "Timestamp"}}}
        )

        expect(converted.when).toBeInstanceOf(Temporal.Instant)
    })

    it("passes a null through where the type allows it", () => {
        const doc = convertSelectionFromServer<any>(queryResult().xxx, Q_FOO_MAP.selections.xxx)

        expect(doc.rows[0].owner.lastLogin).toBe(null)
    })

    it("does not modify the value it converts", () => {
        const raw = queryResult()

        convertResultFromServer(raw, Q_FOO_MAP)

        expect(typeof raw.xxx.rows[0].created).toBe("string")
    })

    it("converts an injection along the map it came with", () => {
        const injection = data("Q_Foo")

        expect(injection.type).toBe("FooDocument")
        expect(injection.value.xxx).toBeInstanceOf(QueryDocument)
        expect(injection.value.xxx.rows[0].created).toBeInstanceOf(Temporal.Instant)
        expect(injection.value.xxx.rows[0].desc).toBeInstanceOf(Temporal.Instant)
    })

    it("keeps the map on the injection, for re-executing the query", () => {
        expect(data("Q_Foo").conversion).toBe(Q_FOO_MAP)
    })
})

describe("variable conversion", () => {

    it("converts the declared variables on their way out", () => {
        const variables = convertVariablesToServer(
            {
                since: Temporal.Instant.from("2026-01-02T03:04:05Z"),
                config: {offset: 0, pageSize: 10},
                notDeclared: "left alone"
            },
            Q_FOO_MAP
        )

        expect(variables.since).toBe("2026-01-02T03:04:05Z")
        // a scalar whose wire format is an object graph goes over as it is
        expect(variables.config).toEqual({offset: 0, pageSize: 10})
        expect(variables.notDeclared).toBe("left alone")
    })

    it("walks into input objects and their lists, along the schema", () => {
        const wire = convertToServer(
            {
                name: "Foo #1",
                created: Temporal.Instant.from("2026-01-02T03:04:05Z"),
                seenAt: [
                    Temporal.Instant.from("2026-01-03T00:00:00Z"),
                    null
                ]
            },
            "FooInput"
        )

        expect(wire.name).toBe("Foo #1")
        expect(wire.created).toBe("2026-01-02T03:04:05Z")
        expect(wire.seenAt).toEqual(["2026-01-03T00:00:00Z", null])
    })

    it("does not modify the live value it converts", () => {
        const live = {name: "Foo #1", created: Temporal.Instant.from("2026-01-02T03:04:05Z")}

        convertToServer(live, "FooInput")

        expect(live.created).toBeInstanceOf(Temporal.Instant)
    })

    it("passes undeclared input fields on for the server to reject", () => {
        const wire = convertToServer({name: "Foo #1", nope: 42}, "FooInput")

        expect(wire.nope).toBe(42)
    })

    it("rejects a null for a non-null input field, naming where it sat", () => {
        expect(() => convertToServer({name: null}, "FooInput"))
            .toThrowError("Null value for non-null String at name")

        expect(() => convertVariablesToServer({foo: {name: null}}, Q_FOO_MAP))
            .toThrowError("Null value for non-null String at foo.name")
    })

    it("passes a null through where the input type allows it", () => {
        const wire = convertToServer({name: "Foo #1", config: null}, "FooInput")

        expect(wire.config).toBe(null)
    })

    it("throws for a type the schema does not know", () => {
        expect(() => convertToServer({}, "Nonexistent")).toThrowError(/Nonexistent/)
    })
})

describe("converter registry", () => {

    it("lets applications override the converters", () => {
        const previous = getConverter("Timestamp")!

        registerConverter<string, string>("Timestamp", {fromServer: value => "custom:" + value})
        expect(convertSelectionFromServer("2026-09-04T10:15:30Z", {type: "Timestamp"}))
            .toBe("custom:2026-09-04T10:15:30Z")
        // no toServer: values of that type go out unchanged
        expect(convertToServer("whatever", "Timestamp")).toBe("whatever")

        registerConverter("Timestamp", previous)
    })
})
