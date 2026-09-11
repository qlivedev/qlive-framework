import {beforeAll, describe, expect, it} from "vitest";
import {init} from "../../src/config";
import {heldRows, walkRows} from "../../src/util/rows";
import {barDocument, mergeConfig} from "../fixtures/mergeConfig";
import {testAuthentication} from "../fixtures/testConfig";

beforeAll(async () => {
    await init({
        config: mergeConfig,
        csrfToken: mergeConfig.csrfToken!,
        authentication: testAuthentication(),
        data: {}
    })
})


describe("walkRows", () => {

    it("reaches every row below the ones it was given", () => {

        const seen: string[] = []

        walkRows(barDocument().rows, "Bar", ({row, type}) => seen.push(type + " " + row.id))

        // a row is visited after the rows below it, which is what lets a caller register children first
        expect(seen).toEqual([
            "Baz baz-1",
            "BarLink link-1",
            "Bar bar-1",
            "Bar bar-2"
        ])
    })


    it("separates values from the rows below, by the schema", () => {

        const visits = new Map<string, any>()

        walkRows(barDocument().rows, "Bar", visited => visits.set(visited.row.id, visited))

        const bar = visits.get("bar-1")

        expect([...bar.values.keys()].sort()).toEqual(
            ["created", "description", "id", "name", "num", "version"]
        )
        expect(bar.relations.map((r: any) => [r.field, r.type, r.list])).toEqual([["bazLinks", "BarLink", true]])

        // the link's foreign keys are values and the rows they name are not, whatever they look like
        const link = visits.get("link-1")

        expect([...link.values.keys()].sort()).toEqual(["barId", "bazId", "id", "version"])
        expect(link.relations.map((r: any) => r.field)).toEqual(["baz"])
    })


    it("skips a field the query did not select", () => {

        const visits: any[] = []

        walkRows([{id: "bar-9", name: "only a name"}], "Bar", v => visits.push(v))

        expect([...visits[0].values.keys()]).toEqual(["id", "name"])
        expect(visits[0].relations).toEqual([])
    })


    it("visits a row without an id, and the rows below it", () => {

        // A query is free to select an object without selecting its id. Nothing can be said about that row,
        // but the rows under it are rows like any other.
        const seen: any[] = []

        walkRows([{name: "nameless", bazLinks: [{id: "link-9", barId: "x", bazId: "y"}]}], "Bar",
            v => seen.push(v.row.id))

        expect(seen).toEqual(["link-9", undefined])
    })


    it("walks past a null to-one rather than failing on it", () => {

        const seen: string[] = []

        walkRows([{id: "link-9", baz: null}], "BarLink", v => seen.push(v.row.id))

        expect(seen).toEqual(["link-9"])
    })
})


describe("heldRows", () => {

    it("collects the ids and the selected fields per type", () => {

        const held = heldRows(barDocument().rows, "Bar")

        expect(held.map(h => h.type).sort()).toEqual(["Bar", "BarLink", "Baz"])

        const bar = held.find(h => h.type === "Bar")!

        expect([...bar.ids].sort()).toEqual(["bar-1", "bar-2"])
        expect([...bar.fields].sort()).toEqual(["created", "description", "id", "name", "num", "version"])

        const baz = held.find(h => h.type === "Baz")!

        expect([...baz.ids]).toEqual(["baz-1"])
        expect([...baz.fields].sort()).toEqual(["id", "name", "version"])
    })


    it("unions the fields of a type selected differently in two places", () => {

        const rows = [
            {id: "link-1", barId: "b", baz: {id: "baz-1", name: "Baz #1"}},
            {id: "link-2", barId: "b", baz: {id: "baz-2", version: "zv2"}}
        ]

        const baz = heldRows(rows, "BarLink").find(h => h.type === "Baz")!

        expect([...baz.ids].sort()).toEqual(["baz-1", "baz-2"])
        expect([...baz.fields].sort()).toEqual(["id", "name", "version"])
    })


    it("leaves out a row with no id", () => {

        const held = heldRows([{name: "nameless"}], "Bar")

        expect(held).toEqual([])
    })
})
