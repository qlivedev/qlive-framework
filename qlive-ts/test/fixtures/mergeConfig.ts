import {QLiveConfig, RelationInfo} from "../../src/config";
import {
    field,
    inputObject,
    inputValue,
    LIST_OF,
    NAMED,
    NOT_NULL,
    object,
    scalar,
    STRING,
    testCsrfToken,
    TIMESTAMP
} from "./testConfig";

/**
 * A domain shaped for the merge meta data: the Bar / BarLink / Baz triple as qlive-test has it, a fat link
 * type only a declaration can identify, and enough unversioned and non-link types for every "no" to have a
 * subject.
 *
 * The relations are the shape DomainQL writes them in, i.e. sourceFields and targetFields hold GraphQL field
 * names and not column names.
 */

const OBJECT = (name: string) => NAMED(name, "OBJECT")

function relation(
    sourceType: string,
    targetType: string,
    sourceFields: string[],
    leftSideObjectName: string,
    rightSideObjectName: string | undefined,
    targetField: "NONE" | "ONE" | "MANY"
): RelationInfo
{
    return {
        id: sourceType + "-" + leftSideObjectName,
        sourceType,
        targetType,
        sourceFields,
        targetFields: ["id"],
        leftSideObjectName,
        rightSideObjectName,
        sourceField: "OBJECT_AND_SCALAR",
        targetField,
        metaTags: []
    }
}

export const mergeConfig: QLiveConfig = {
    contextPath: "/",
    csrfToken: testCsrfToken(),
    schema: {
        types: [
            scalar("String"),
            scalar("Int"),
            scalar("Boolean"),
            scalar("Timestamp"),
            scalar("QueryConfig"),
            scalar("GenericScalar"),

            // versioned, and the source side of a many-to-many
            object("Bar", [
                field("id", NOT_NULL(STRING)),
                field("name", NOT_NULL(STRING)),
                field("num", NOT_NULL(NAMED("Int"))),
                field("description", STRING),
                field("created", NOT_NULL(TIMESTAMP)),
                field("version", STRING),
                field("bazLinks", LIST_OF(OBJECT("BarLink")))
            ]),
            object("Baz", [
                field("id", NOT_NULL(STRING)),
                field("name", NOT_NULL(STRING)),
                field("version", STRING),
                field("barLinks", LIST_OF(OBJECT("BarLink")))
            ]),

            // a link of the plain shape: an id, a version and the two foreign keys, nothing else
            object("BarLink", [
                field("id", NOT_NULL(STRING)),
                field("version", STRING),
                field("barId", NOT_NULL(STRING)),
                field("bar", NOT_NULL(OBJECT("Bar"))),
                field("bazId", NOT_NULL(STRING)),
                field("baz", NOT_NULL(OBJECT("Baz")))
            ]),

            // versioned, two relations out and fields of its own, so no shape makes it a link
            object("Foo", [
                field("id", NOT_NULL(STRING)),
                field("name", NOT_NULL(STRING)),
                field("version", STRING),
                field("ownerId", NOT_NULL(STRING)),
                field("owner", NOT_NULL(OBJECT("AppUser"))),
                field("type", NOT_NULL(STRING)),
                field("fooType", NOT_NULL(OBJECT("FooType")))
            ]),
            object("AppUser", [
                field("id", NOT_NULL(STRING)),
                field("login", NOT_NULL(STRING)),
                field("foos", LIST_OF(OBJECT("Foo")))
            ]),
            object("FooType", [
                field("id", NOT_NULL(STRING)),
                field("name", NOT_NULL(STRING))
            ]),

            // unversioned on purpose
            object("Qux", [
                field("id", NOT_NULL(STRING)),
                field("name", NOT_NULL(STRING))
            ]),

            // a link carrying a field of its own, which is why CorgeLink has to be declared one
            object("Corge", [
                field("id", NOT_NULL(STRING)),
                field("version", STRING),
                field("corgeLinks", LIST_OF(OBJECT("CorgeLink")))
            ]),
            object("Grault", [
                field("id", NOT_NULL(STRING)),
                field("version", STRING),
                field("corgeLinks", LIST_OF(OBJECT("CorgeLink")))
            ]),
            object("CorgeLink", [
                field("id", NOT_NULL(STRING)),
                field("version", STRING),
                field("corgeId", NOT_NULL(STRING)),
                field("corge", NOT_NULL(OBJECT("Corge"))),
                field("graultId", NOT_NULL(STRING)),
                field("grault", NOT_NULL(OBJECT("Grault"))),
                field("weight", NOT_NULL(NAMED("Int")))
            ]),

            // what a query hands a working set, and what a merge that landed fetches again
            object("BarDocument", [
                field("type", STRING),
                field("config", NAMED("QueryConfig")),
                field("rows", LIST_OF(OBJECT("Bar"))),
                field("rowCount", NAMED("Int"))
            ]),
            object("QueryType", [
                {
                    ...field("queryBarDocument", NOT_NULL(OBJECT("BarDocument"))),
                    args: [inputValue("config", NOT_NULL(NAMED("QueryConfig")))]
                }
            ]),

            // QLive's write mutation and the four types it travels in, as the framework declares them
            object("MutationType", [
                {
                    ...field("mergeWorkingSet", NOT_NULL(OBJECT("MergeResult"))),
                    args: [
                        inputValue("changes", NOT_NULL(LIST_OF(NAMED("EntityChangeInput", "INPUT_OBJECT")))),
                        inputValue("deletions", NOT_NULL(LIST_OF(NAMED("EntityDeletionInput", "INPUT_OBJECT")))),
                        inputValue("mergeConfig", NOT_NULL(NAMED("MergeConfigInput", "INPUT_OBJECT")))
                    ]
                }
            ]),
            inputObject("EntityChangeInput", [
                inputValue("type", NOT_NULL(STRING)),
                inputValue("id", NOT_NULL(STRING)),
                inputValue("version", STRING),
                inputValue("new", NOT_NULL(NAMED("Boolean"))),
                inputValue("changes", NOT_NULL(LIST_OF(NAMED("FieldChangeInput", "INPUT_OBJECT"))))
            ]),
            inputObject("EntityDeletionInput", [
                inputValue("type", NOT_NULL(STRING)),
                inputValue("id", NOT_NULL(STRING)),
                inputValue("version", STRING)
            ]),
            inputObject("FieldChangeInput", [
                inputValue("field", NOT_NULL(STRING)),
                inputValue("value", NAMED("GenericScalar"))
            ]),
            inputObject("MergeConfigInput", [
                inputValue("resolveConflicts", NAMED("Boolean"))
            ]),
            object("MergeResult", [
                field("status", NOT_NULL(NAMED("MergeStatus", "ENUM"))),
                field("conflicts", NOT_NULL(LIST_OF(OBJECT("MergeConflict"))))
            ]),
            object("MergeConflict", [
                field("type", NOT_NULL(STRING)),
                field("id", NOT_NULL(STRING)),
                field("storedVersion", STRING),
                field("deleted", NOT_NULL(NAMED("Boolean"))),
                field("fields", NOT_NULL(LIST_OF(OBJECT("MergeConflictField"))))
            ]),
            object("MergeConflictField", [
                field("field", NOT_NULL(STRING)),
                field("mine", NAMED("GenericScalar")),
                field("stored", NAMED("GenericScalar")),
                field("informational", NOT_NULL(NAMED("Boolean")))
            ])
        ]
    },
    meta: {
        types: {
            Bar: {meta: {merge: {resolve: true, ignoredFields: ["num"]}}},
            Baz: {meta: {merge: {autoMerge: false}}},
            CorgeLink: {meta: {merge: {linkType: true}}}
        },
        genericTypes: [
            {
                type: "BarDocument",
                typeParameters: ["Bar"],
                genericType: "com.dataciders.qlive.model.QueryDocument"
            }
        ],
        relations: [
            relation("BarLink", "Bar", ["barId"], "bar", "bazLinks", "MANY"),
            relation("BarLink", "Baz", ["bazId"], "baz", "barLinks", "MANY"),
            relation("Foo", "AppUser", ["ownerId"], "owner", "foos", "MANY"),
            relation("Foo", "FooType", ["type"], "fooType", undefined, "NONE"),
            relation("CorgeLink", "Corge", ["corgeId"], "corge", "corgeLinks", "MANY"),
            relation("CorgeLink", "Grault", ["graultId"], "grault", "corgeLinks", "MANY")
        ]
    }
}

/**
 * A Bar document as the server sends it: two rows, one of them with a link to a Baz, everything a working
 * set needs to register them selected.
 *
 * @param name      the name of the first row, so that a refreshed document can be told from the first one
 * @param version   the version of the first row, which is what a merge moves on
 */
export function barDocument(name: string = "Bar #1", version: string = "v1")
{
    return {
        type: "Bar",
        config: {offset: 0, pageSize: 10, condition: null, sortFields: []},
        rowCount: 2,
        rows: [
            {
                id: "bar-1",
                name,
                num: 1,
                description: null,
                created: "2026-01-02T03:04:05Z",
                version,
                bazLinks: [
                    {
                        id: "link-1",
                        version: "lv1",
                        barId: "bar-1",
                        bazId: "baz-1",
                        baz: {id: "baz-1", name: "Baz #1", version: "zv1"}
                    }
                ]
            },
            {
                id: "bar-2",
                name: "Bar #2",
                num: 2,
                description: "second",
                created: "2026-01-03T00:00:00Z",
                version: "v2",
                bazLinks: []
            }
        ]
    }
}
