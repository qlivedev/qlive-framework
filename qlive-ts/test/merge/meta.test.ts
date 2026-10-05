import {beforeAll, describe, expect, it} from "vitest";
import {init} from "../../src/config";
import * as MergeMeta from "../../src/merge/meta";
import {mergeConfig} from "../fixtures/mergeConfig";
import {testAuthentication} from "../fixtures/testConfig";

/**
 * The client's half of what a type says about merging it. Everything here is derived from the schema and the
 * type meta data the page already carries -- there is no merge-specific payload, and the answers have to be
 * the ones MergeMeta reaches on the Java side.
 */

beforeAll(async () => {
    await init({
        config: mergeConfig,
        csrfToken: mergeConfig.csrfToken!,
        authentication: testAuthentication(),
        data: {}
    })
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

        // the one default that is not "off"
        expect(MergeMeta.isAutoMerge("Foo")).toBe(true)
    })

    it("answers a name that is no type", () => {

        expect(MergeMeta.metaOf("NoSuchType")).toEqual({})
        expect(MergeMeta.resolvesConflicts("NoSuchType")).toBe(false)
        expect(MergeMeta.isAutoMerge("NoSuchType")).toBe(true)
    })
})

describe("many-to-many fields", () => {

    it("reads a field from the declaration", () => {

        expect(MergeMeta.manyToManyField("Bar", "bazes")).toEqual({
            field: "bazes",
            sourceType: "Bar",
            targetType: "Baz",
            linkType: "BarLink",
            writable: true
        })
    })

    it("reads the same declaration from the other end", () => {

        expect(MergeMeta.manyToManyField("Baz", "bars")).toEqual({
            field: "bars",
            sourceType: "Baz",
            targetType: "Bar",
            linkType: "BarLink",
            writable: true
        })
    })

    it("says where a field cannot be written", () => {

        // a CorgeLink needs a weight, which nothing but a CorgeLink row can give it
        expect(MergeMeta.manyToManyField("Corge", "graults")?.writable).toBe(false)
    })

    it("leaves the link arrays of ordinary relations alone", () => {

        // Bar.bazLinks lists the same link rows, and is a one-to-many relation like AppUser.foos: its rows are
        // entities of their own, whatever their shape
        expect(MergeMeta.manyToManyField("Bar", "bazLinks")).toBeNull()
        expect(MergeMeta.manyToManyField("AppUser", "foos")).toBeNull()
    })

    it("answers a field that is none, and a type that has none", () => {

        expect(MergeMeta.manyToManyField("Bar", "name")).toBeNull()
        expect(MergeMeta.manyToManyFields("Qux")).toEqual([])
        expect(MergeMeta.manyToManyFields("NoSuchType")).toEqual([])
    })

    it("lists every many-to-many field of a type", () => {

        expect(MergeMeta.manyToManyFields("Bar").map(f => f.field)).toEqual(["bazes"])
        expect(MergeMeta.manyToManyFields("Grault").map(f => f.field)).toEqual(["corges"])
    })
})
