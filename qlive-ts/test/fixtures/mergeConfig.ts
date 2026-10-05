import {ManyToManyInfo, QLiveConfig, RelationInfo} from "../../src/config";
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
 * A domain shaped for the merge meta data: the Bar / BarLink / Baz triple as qlive-test has it, declared a
 * many-to-many and configured as ordinary relations besides, a link type carrying a field of its own, and
 * enough unversioned types for every "no" to have a subject.
 *
 * The relations are the shape the server writes them in, i.e. sourceFields and targetFields hold GraphQL field
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

function manyToMany(
    linkType: string,
    type: string,
    linkField: string,
    field: string,
    otherType: string,
    otherLinkField: string,
    otherField: string,
    writable: boolean
): ManyToManyInfo
{
    return {
        linkType,
        left: {type, linkField, field},
        right: {type: otherType, linkField: otherLinkField, field: otherField},
        writable
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

            // versioned, and one end of a many-to-many
            object("Bar", [
                field("id", NOT_NULL(STRING)),
                field("name", NOT_NULL(STRING)),
                field("num", NOT_NULL(NAMED("Int"))),
                field("description", STRING),
                field("created", NOT_NULL(TIMESTAMP)),
                field("version", STRING),
                field("bazLinks", LIST_OF(OBJECT("BarLink"))),
                field("bazs", NOT_NULL(LIST_OF(OBJECT("Baz"))))
            ]),
            object("Baz", [
                field("id", NOT_NULL(STRING)),
                field("name", NOT_NULL(STRING)),
                field("version", STRING),
                field("barLinks", LIST_OF(OBJECT("BarLink"))),
                field("bars", NOT_NULL(LIST_OF(OBJECT("Bar"))))
            ]),

            // the link table, whose foreign keys are configured as ordinary relations as well
            object("BarLink", [
                field("id", NOT_NULL(STRING)),
                field("version", STRING),
                field("barId", NOT_NULL(STRING)),
                field("bar", NOT_NULL(OBJECT("Bar"))),
                field("bazId", NOT_NULL(STRING)),
                field("baz", NOT_NULL(OBJECT("Baz")))
            ]),

            // versioned, with two relations out and fields of its own
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

            // a many-to-many whose link carries a required field of its own, which makes it read-only
            object("Corge", [
                field("id", NOT_NULL(STRING)),
                field("version", STRING),
                field("corgeLinks", LIST_OF(OBJECT("CorgeLink"))),
                field("graults", NOT_NULL(LIST_OF(OBJECT("Grault"))))
            ]),
            object("Grault", [
                field("id", NOT_NULL(STRING)),
                field("version", STRING),
                field("corgeLinks", LIST_OF(OBJECT("CorgeLink"))),
                field("corges", NOT_NULL(LIST_OF(OBJECT("Corge"))))
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

            // QLive's write mutation and the types it travels in, as the framework declares them
            object("MutationType", [
                {
                    ...field("mergeWorkingSet", NOT_NULL(OBJECT("MergeResult"))),
                    args: [
                        inputValue("changes", NOT_NULL(LIST_OF(NAMED("EntityChangeInput", "INPUT_OBJECT")))),
                        inputValue("links", NOT_NULL(LIST_OF(NAMED("LinkChangeInput", "INPUT_OBJECT")))),
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
            inputObject("LinkChangeInput", [
                inputValue("type", NOT_NULL(STRING)),
                inputValue("id", NOT_NULL(STRING)),
                inputValue("field", NOT_NULL(STRING)),
                inputValue("added", NOT_NULL(LIST_OF(STRING))),
                inputValue("removed", NOT_NULL(LIST_OF(STRING)))
            ]),
            inputObject("FieldChangeInput", [
                inputValue("field", NOT_NULL(STRING)),
                inputValue("value", NAMED("GenericScalar"))
            ]),
            inputObject("MergeConfigInput", [
                inputValue("conflictValues", NAMED("Boolean"))
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
            Baz: {meta: {merge: {autoMerge: false}}}
        },
        genericTypes: [
            {
                type: "BarDocument",
                typeParameters: ["Bar"],
                genericType: "io.github.qlivedev.model.QueryDocument"
            }
        ],
        relations: [
            relation("BarLink", "Bar", ["barId"], "bar", "bazLinks", "MANY"),
            relation("BarLink", "Baz", ["bazId"], "baz", "barLinks", "MANY"),
            relation("Foo", "AppUser", ["ownerId"], "owner", "foos", "MANY"),
            relation("Foo", "FooType", ["type"], "fooType", undefined, "NONE"),
            relation("CorgeLink", "Corge", ["corgeId"], "corge", "corgeLinks", "MANY"),
            relation("CorgeLink", "Grault", ["graultId"], "grault", "corgeLinks", "MANY")
        ],
        manyToMany: [
            manyToMany("BarLink", "Bar", "barId", "bazs", "Baz", "bazId", "bars", true),
            manyToMany("CorgeLink", "Corge", "corgeId", "graults", "Grault", "graultId", "corges", false)
        ]
    }
}

/**
 * A Bar document as the server sends it: two rows, the first associated with a Baz, everything a working set
 * needs to register them selected. The association is there twice, as the link row and as the Baz itself.
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
                ],
                bazs: [
                    {id: "baz-1", name: "Baz #1", version: "zv1"}
                ]
            },
            {
                id: "bar-2",
                name: "Bar #2",
                num: 2,
                description: "second",
                created: "2026-01-03T00:00:00Z",
                version: "v2",
                bazLinks: [],
                bazs: []
            }
        ]
    }
}
