import {Temporal} from "temporal-polyfill";
import {describe, expect, it} from "vitest";
import {genericScalarEqual, scalarEqual} from "../../src/util/scalar";

describe("scalarEqual", () => {

    it("compares primitives by value", () => {

        expect(scalarEqual("a", "a")).toBe(true)
        expect(scalarEqual(1, 1)).toBe(true)
        expect(scalarEqual(null, null)).toBe(true)
        expect(scalarEqual(undefined, undefined)).toBe(true)

        expect(scalarEqual("a", "b")).toBe(false)
        expect(scalarEqual(1, "1")).toBe(false)
        expect(scalarEqual(null, undefined)).toBe(false)
    })


    it("asks a converted value what it thinks", () => {

        // two Temporal.Instants at the same instant are two objects, which is the case this exists for
        const instant = Temporal.Instant.from("2026-09-12T10:00:00Z")
        const same = Temporal.Instant.from("2026-09-12T10:00:00Z")
        const other = Temporal.Instant.from("2026-09-12T10:00:01Z")

        expect(instant === same).toBe(false)
        expect(scalarEqual(instant, same)).toBe(true)
        expect(scalarEqual(instant, other)).toBe(false)
    })


    it("says no to a value that can neither be identified nor asked", () => {

        // the safe direction: what the user typed stays a change rather than being dropped as one
        expect(scalarEqual({a: 1}, {a: 1})).toBe(false)
        expect(scalarEqual([1], [1])).toBe(false)
        expect(scalarEqual(new Date(0), new Date(0))).toBe(false)

        const object = {a: 1}
        expect(scalarEqual(object, object)).toBe(true)
    })


    it("does not ask an equals() of a value on one side only", () => {

        const instant = Temporal.Instant.from("2026-09-12T10:00:00Z")

        expect(scalarEqual(instant, "2026-09-12T10:00:00Z")).toBe(false)
        expect(scalarEqual("2026-09-12T10:00:00Z", instant)).toBe(false)
    })
})


describe("genericScalarEqual", () => {

    it("wants the same type and the same value", () => {

        expect(genericScalarEqual({type: "String", value: "a"}, {type: "String", value: "a"})).toBe(true)
        expect(genericScalarEqual({type: "String", value: "a"}, {type: "String", value: "b"})).toBe(false)

        // a domain distinguishes these, and the type name is what the server coerces along
        expect(genericScalarEqual({type: "Int", value: 1}, {type: "Float", value: 1})).toBe(false)
    })


    it("compares the values the way a scalar is compared", () => {

        const a = {type: "Timestamp", value: Temporal.Instant.from("2026-09-12T10:00:00Z")}
        const b = {type: "Timestamp", value: Temporal.Instant.from("2026-09-12T10:00:00Z")}

        expect(genericScalarEqual(a, b)).toBe(true)
    })


    it("takes two nulls as the same value and one as not", () => {

        expect(genericScalarEqual(null, null)).toBe(true)
        expect(genericScalarEqual(null, {type: "String", value: "a"})).toBe(false)
        expect(genericScalarEqual({type: "String", value: "a"}, null)).toBe(false)
    })
})
