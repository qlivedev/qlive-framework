import {QLiveConfig, RelationInfo} from "../../src/config";
import {field, LIST_OF, NAMED, NOT_NULL, object, scalar, STRING, testCsrfToken} from "./testConfig";

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

            // versioned, and the source side of a many-to-many
            object("Bar", [
                field("id", NOT_NULL(STRING)),
                field("name", NOT_NULL(STRING)),
                field("num", NOT_NULL(NAMED("Int"))),
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
            ])
        ]
    },
    meta: {
        types: {
            Bar: {meta: {merge: {resolve: true, ignoredFields: ["num"]}}},
            Baz: {meta: {merge: {autoMerge: false}}},
            CorgeLink: {meta: {merge: {linkType: true}}}
        },
        genericTypes: [],
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
