import {init, RelationInfo} from "../../src/config";
import {
    field,
    LIST_OF,
    NAMED,
    NOT_NULL,
    object,
    scalar,
    STRING,
    testAuthentication,
    testConfig,
    testCsrfToken,
    TIMESTAMP
} from "./testConfig";

/**
 * Schema and meta data the grid tests run against: a Foo with a field of every kind a column has to tell apart --
 * String, numbers, Boolean, time, a to-one relation with name fields, one without, an object that is no relation,
 * and a list -- and a FooLink keyed by two fields.
 */

function relation(sourceType: string, leftSideObjectName: string, targetType: string): RelationInfo
{
    return {
        sourceType,
        leftSideObjectName,
        targetType,
        sourceField: "OBJECT_AND_SCALAR",
        targetField: "NONE",
        metaTags: [],
        sourceFields: [leftSideObjectName + "Id"],
        targetFields: ["id"]
    };
}

export function initGridConfig()
{
    return init({
        config: {
            ...testConfig,
            schema: {
                types: [
                    scalar("String"),
                    scalar("Int"),
                    scalar("Float"),
                    scalar("Boolean"),
                    scalar("Timestamp"),
                    scalar("Date"),
                    object("AppUser", [
                        field("id", NOT_NULL(STRING)),
                        field("login", NOT_NULL(STRING)),
                        field("lastLogin", TIMESTAMP)
                    ]),
                    object("FooType", [
                        field("name", NOT_NULL(STRING)),
                        field("ordinal", NOT_NULL(NAMED("Int")))
                    ]),
                    object("Tag", [
                        field("id", NOT_NULL(STRING))
                    ]),
                    object("Foo", [
                        field("id", NOT_NULL(STRING)),
                        field("name", NOT_NULL(STRING)),
                        field("num", NOT_NULL(NAMED("Int"))),
                        field("ratio", NAMED("Float")),
                        field("flag", NOT_NULL(NAMED("Boolean"))),
                        field("created", NOT_NULL(TIMESTAMP)),
                        field("day", NAMED("Date")),
                        field("ownerId", STRING),
                        field("owner", NAMED("AppUser", "OBJECT")),
                        field("fooType", NAMED("FooType", "OBJECT")),
                        field("embedded", NAMED("Tag", "OBJECT")),
                        field("tags", LIST_OF(NAMED("Tag", "OBJECT")))
                    ]),
                    object("FooLink", [
                        field("fooId", NOT_NULL(STRING)),
                        field("tagId", NOT_NULL(STRING))
                    ])
                ]
            },
            meta: {
                ...testConfig.meta,
                types: {
                    AppUser: {meta: {nameFields: ["login"]}},
                    Foo: {meta: {uniqueKeys: [{name: "pk_foo", fields: ["id"], primary: true, nullable: false}]}},
                    FooLink: {
                        meta: {
                            uniqueKeys: [{name: "pk_foo_link", fields: ["fooId", "tagId"], primary: true, nullable: false}]
                        }
                    }
                },
                relations: [
                    relation("Foo", "owner", "AppUser"),
                    relation("Foo", "fooType", "FooType")
                ]
            }
        },
        csrfToken: testCsrfToken(),
        authentication: testAuthentication(),
        data: {}
    });
}
