import {beforeAll, describe, expect, it} from "vitest";
import {init} from "../../src/config";
import * as MergeMeta from "../../src/merge/meta";
import {mergeConfig} from "../fixtures/mergeConfig";

/**
 * The client's half of what a type says about merging it. Everything here is derived from the schema and the
 * type meta data the page already carries -- there is no merge-specific payload, and the answers have to be
 * the ones MergeMeta reaches on the Java side.
 */

beforeAll(async () => {
    await init({config: mergeConfig, csrfToken: mergeConfig.csrfToken!, data: {}})
})

describe("versioned types", () => {

    it("derives who takes part from the version field", () => {

        expect(MergeMeta.isVersioned("Bar")).toBe(true)
        expect(MergeMeta.isVersioned("BarLink")).toBe(true)

        expect(MergeMeta.isVersioned("Qux")).toBe(false)
        expect(MergeMeta.isVersioned("AppUser")).toBe(false)

        // a name that is no type of the schema, which is what a type name off the wire may be
        expect(MergeMeta.isVersioned("NoSuchType")).toBe(false)
    })

    it("lists them alphabetically", () => {

        expect(MergeMeta.versionedTypes()).toEqual(["Bar", "BarLink", "Baz", "Corge", "CorgeLink", "Foo", "Grault"])
    })
})

describe("declared meta data", () => {

    it("reads what a type declared", () => {

        expect(MergeMeta.resolvesConflicts("Bar")).toBe(true)
        expect(MergeMeta.ignoredFields("Bar")).toEqual(["num"])
        expect(MergeMeta.isAutoMerge("Baz")).toBe(false)
    })

    it("answers a type that declared nothing", () => {

        expect(MergeMeta.metaOf("Foo")).toEqual({})
        expect(MergeMeta.resolvesConflicts("Foo")).toBe(false)
        expect(MergeMeta.ignoredFields("Foo")).toEqual([])
        expect(MergeMeta.isLinkType("Foo")).toBe(false)

        // the one default that is not "off"
        expect(MergeMeta.isAutoMerge("Foo")).toBe(true)
    })

    it("answers a name that is no type", () => {

        expect(MergeMeta.metaOf("NoSuchType")).toEqual({})
        expect(MergeMeta.resolvesConflicts("NoSuchType")).toBe(false)
        expect(MergeMeta.isAutoMerge("NoSuchType")).toBe(true)
    })
})

describe("link types", () => {

    it("recognizes one by its shape", () => {

        // an id, a version and the two foreign keys with their object fields, and nothing else
        expect(MergeMeta.isLinkType("BarLink")).toBe(true)
    })

    it("takes the declaration for one carrying a field of its own", () => {

        // CorgeLink has a weight, so nothing about its shape says "link"
        expect(MergeMeta.isLinkType("CorgeLink")).toBe(true)
    })

    it("is not fooled by a type that merely has two relations", () => {

        // Foo points at an owner and a type, and has fields two users can disagree about
        expect(MergeMeta.isLinkType("Foo")).toBe(false)

        // and one relation is not two, whatever the rest looks like
        expect(MergeMeta.isLinkType("Bar")).toBe(false)
        expect(MergeMeta.isLinkType("NoSuchType")).toBe(false)
    })
})

describe("link relations", () => {

    it("resolves a link array into both sides of the link", () => {

        expect(MergeMeta.linkRelation("Bar", "bazLinks")).toEqual({
            field: "bazLinks",
            sourceType: "Bar",
            linkType: "BarLink",
            sourceField: "barId",
            targetType: "Baz",
            targetField: "bazId",
            targetObject: "baz"
        })
    })

    it("resolves the same link from the other side", () => {

        expect(MergeMeta.linkRelation("Baz", "barLinks")).toEqual({
            field: "barLinks",
            sourceType: "Baz",
            linkType: "BarLink",
            sourceField: "bazId",
            targetType: "Bar",
            targetField: "barId",
            targetObject: "bar"
        })
    })

    it("resolves a declared link type the same way", () => {

        expect(MergeMeta.linkRelation("Corge", "corgeLinks")).toEqual({
            field: "corgeLinks",
            sourceType: "Corge",
            linkType: "CorgeLink",
            sourceField: "corgeId",
            targetType: "Grault",
            targetField: "graultId",
            targetObject: "grault"
        })
    })

    it("leaves an ordinary back reference alone", () => {

        // AppUser.foos is a list of Foo the same way Bar.bazLinks is a list of BarLink, and editing it is
        // not the same thing at all
        expect(MergeMeta.linkRelations("AppUser")).toEqual([])
        expect(MergeMeta.linkRelation("AppUser", "foos")).toBeNull()
    })

    it("answers a field that is no link array, and a type that has none", () => {

        expect(MergeMeta.linkRelation("Bar", "name")).toBeNull()
        expect(MergeMeta.linkRelations("Qux")).toEqual([])
        expect(MergeMeta.linkRelations("NoSuchType")).toEqual([])
    })

    it("lists every link array of a type", () => {

        expect(MergeMeta.linkRelations("Bar").map(l => l.field)).toEqual(["bazLinks"])
        expect(MergeMeta.linkRelations("Grault").map(l => l.field)).toEqual(["corgeLinks"])
    })
})
