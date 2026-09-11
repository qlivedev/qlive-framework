import {beforeAll, describe, expect, it} from "vitest";
import {init} from "../../src/config";
import {fieldOrder, maskedFields, maskOf} from "../../src/merge/fieldMask";
import {mergeConfig} from "../fixtures/mergeConfig";
import {testAuthentication} from "../fixtures/testConfig";

beforeAll(async () => {
    await init({
        config: mergeConfig,
        csrfToken: mergeConfig.csrfToken!,
        authentication: testAuthentication(),
        data: {}
    })
})


describe("fieldMask", () => {

    it("assigns a bit per field, alphabetically", () => {

        // The order FieldLayout.of(DomainQL, String) assigns on the Java side. Both ends derive it from
        // the same schema, and a mask read under another order names other fields without saying so.
        expect(fieldOrder("Bar")).toEqual(
            ["bazLinks", "created", "description", "id", "name", "num", "version"]
        )
    })


    it("makes a mask of the fields it is given", () => {

        expect(maskOf("Bar", ["bazLinks"])).toBe(1n)
        expect(maskOf("Bar", ["description"])).toBe(4n)
        expect(maskOf("Bar", ["name"])).toBe(16n)
        expect(maskOf("Bar", ["name", "num"])).toBe(48n)
        expect(maskOf("Bar", [])).toBe(0n)
    })


    it("leaves out a name the type does not have", () => {

        // The same as the Java side: a caller naming fields is not in a position to know what the layout
        // has, and neither is one reading a mask back.
        expect(maskOf("Bar", ["name", "nosuchfield"])).toBe(maskOf("Bar", ["name"]))
    })


    it("names the fields of a mask", () => {

        expect(maskedFields("Bar", 48n)).toEqual(["name", "num"])
        expect(maskedFields("Bar", 0n)).toEqual([])
    })


    it("ignores bits past the end of the field list", () => {

        // A field this client does not have is one it cannot name, and a stale bit is not worth guessing
        // at -- the Java side drops the same bits for the same reason.
        expect(maskedFields("Bar", 1n << 100n)).toEqual([])
        expect(maskedFields("Bar", (1n << 100n) | 16n)).toEqual(["name"])
    })


    it("round-trips every field of a type", () => {

        const all = fieldOrder("Bar")

        expect(maskedFields("Bar", maskOf("Bar", all))).toEqual(all)
    })


    it("reads a mask that arrived as a decimal string", () => {

        // How one travels: 128 bits has no exact JSON number to be, so the wire carries decimal digits and
        // this side reads them with BigInt. Parsing the same string as a number loses the low bits, which
        // is precisely the field names.
        const wire = ((1n << 100n) | 16n).toString()

        expect(maskedFields("Bar", BigInt(wire))).toEqual(["name"])
        expect(Number(wire) % 2 ** 53).toBe(0)
    })
})
