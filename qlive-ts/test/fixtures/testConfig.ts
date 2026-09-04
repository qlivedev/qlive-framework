import {
    GraphQLField,
    GraphQLInputObjectType,
    GraphQLInputValue,
    GraphQLObjectType,
    GraphQLScalarType,
    GraphQLTypeRef
} from "../../src/GraphQLSchema";
import {QLiveConfig} from "../../src/config";

/**
 * Schema and meta data the tests run against: a Foo with a Timestamp, its owner, the
 * FooDocument derived from QueryDocument<Foo>, an input object, and the QueryType the
 * conversion map resolves its top-level selections in.
 */

export function scalar(name: string): GraphQLScalarType
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

export function field(name: string, type: GraphQLTypeRef): GraphQLField
{
    return {name, description: null, args: [], type, isDeprecated: false, deprecationReason: null}
}

export function object(name: string, fields: GraphQLField[]): GraphQLObjectType
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

export function inputValue(name: string, type: GraphQLTypeRef): GraphQLInputValue
{
    return {name, description: null, type, defaultValue: null}
}

export function inputObject(name: string, inputFields: GraphQLInputValue[]): GraphQLInputObjectType
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

export const NAMED = (name: string, kind: "SCALAR" | "OBJECT" = "SCALAR"): GraphQLTypeRef => ({kind, name})
export const NOT_NULL = (ofType: GraphQLTypeRef): GraphQLTypeRef => ({kind: "NON_NULL", name: null, ofType})
export const LIST_OF = (ofType: GraphQLTypeRef): GraphQLTypeRef => ({kind: "LIST", name: null, ofType})

export const TIMESTAMP = NAMED("Timestamp")
export const STRING = NAMED("String")

export const testConfig: QLiveConfig = {
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
                field("description", STRING),
                field("created", NOT_NULL(TIMESTAMP)),
                field("owner", NAMED("AppUser", "OBJECT"))
            ]),
            inputObject("FooInput", [
                inputValue("name", NOT_NULL(STRING)),
                inputValue("created", NOT_NULL(TIMESTAMP)),
                inputValue("seenAt", LIST_OF(TIMESTAMP)),
                inputValue("config", NAMED("QueryConfig"))
            ]),
            object("QueryType", [
                {
                    ...field("queryFooDocument", NAMED("FooDocument", "OBJECT")),
                    args: [inputValue("config", NOT_NULL(NAMED("QueryConfig")))]
                },
                field("countFoos", NAMED("Int")),
                field("currentUser", NAMED("AppUser", "OBJECT"))
            ]),
            object("MutationType", [
                {
                    ...field("updateFoo", NAMED("Foo", "OBJECT")),
                    args: [inputValue("foo", NOT_NULL(NAMED("FooInput")))]
                }
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
                genericType: "com.dataciders.qlive.model.QueryDocument"
            }
        ],
        relations: []
    }
}

export function fooDocument()
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
                when: "2026-01-03T00:00:00Z",
                desc: "2026-01-02T03:04:05Z",
                owner: {
                    id: "user-1",
                    lastLogin: null
                }
            }
        ]
    }
}

/**
 * The document as it arrives in a query result: under the alias the query gave the
 * top-level selection.
 */
export function queryResult()
{
    return {
        xxx: fooDocument()
    }
}
