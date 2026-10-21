import {beforeAll, describe, expect, it} from "vitest";
import {Temporal} from "temporal-polyfill";
import {init} from "../src/config";
import {inputText, parseInput, registerConverter} from "../src/converter";
import {testAuthentication, testConfig, testCsrfToken} from "./fixtures/testConfig";

/**
 * Between a live value and the text an input edits. The messages for the user are i18n tags, which render in
 * brackets without a translation; what is checked is that the text is refused, and with which tag.
 */

beforeAll(async () => {
    await init({
        config: testConfig,
        csrfToken: testCsrfToken(),
        authentication: testAuthentication(),
        data: {}
    })
})

describe("inputText()", () => {

    it("is empty for no value", () => {

        expect(inputText(null, "Int")).toBe("")
        expect(inputText(undefined, "Date")).toBe("")
    })

    it("writes numbers and integers as they are", () => {

        expect(inputText(42, "Int")).toBe("42")
        expect(inputText(1.5, "Float")).toBe("1.5")
        expect(inputText(9223372036854775807n, "Long")).toBe("9223372036854775807")
    })

    it("writes a Currency amount in whole units with at least two decimals", () => {

        expect(inputText(11500, "Currency")).toBe("1.15")
        expect(inputText(10000, "Currency")).toBe("1.00")
        expect(inputText(12345, "Currency")).toBe("1.2345")
        expect(inputText(-500, "Currency")).toBe("-0.05")
    })

    it("writes a Date the way a date input takes it", () => {

        expect(inputText(Temporal.PlainDate.from("2026-10-19"), "Date")).toBe("2026-10-19")
    })

    it("refuses a type without an input", () => {

        expect(() => inputText("1.50", "BigDecimal")).toThrow(/No input for BigDecimal/)
    })
})

describe("parseInput()", () => {

    it("keeps text as it is, blank included", () => {

        expect(parseInput("  padded ", "String")).toBe("  padded ")
        expect(parseInput("", "String")).toBe("")
    })

    it("reads blank text as null for every other type", () => {

        expect(parseInput("", "Int")).toBe(null)
        expect(parseInput("   ", "Date")).toBe(null)
    })

    it("reads whole numbers within the type's range", () => {

        expect(parseInput(" 42 ", "Int")).toBe(42)
        expect(parseInput("127", "Byte")).toBe(127)
        expect(() => parseInput("128", "Byte")).toThrow("Not between {0} and {1}")
        expect(() => parseInput("1.5", "Int")).toThrow("Not a whole number")
        expect(() => parseInput("abc", "Int")).toThrow("Not a whole number")
    })

    it("keeps a Number within what a JavaScript number holds exactly", () => {

        expect(parseInput("9007199254740991", "Number")).toBe(9007199254740991)
        expect(() => parseInput("9007199254740992", "Number")).toThrow("Not between {0} and {1}")
    })

    it("reads a Long as a bigint, to the end of its range", () => {

        expect(parseInput("9223372036854775807", "Long")).toBe(9223372036854775807n)
        expect(() => parseInput("9223372036854775808", "Long")).toThrow("Not between {0} and {1}")
        expect(parseInput("123456789012345678901234567890", "BigInteger")).toBe(123456789012345678901234567890n)
    })

    it("reads a Currency amount into 1/10000th units", () => {

        expect(parseInput("1.15", "Currency")).toBe(11500)
        expect(parseInput("-.5", "Currency")).toBe(-5000)
        expect(parseInput("3", "Currency")).toBe(30000)
        expect(() => parseInput("1.23456", "Currency")).toThrow("Max {0} decimals")
    })

    it("reads a Float and refuses what is no number", () => {

        expect(parseInput("1e3", "Float")).toBe(1000)
        expect(() => parseInput("x", "Float")).toThrow("Not a number")
    })

    it("reads a Boolean from its text", () => {

        expect(parseInput("true", "Boolean")).toBe(true)
        expect(() => parseInput("yes", "Boolean")).toThrow("Not a boolean")
    })

    it("reads a Date and refuses one that doesn't exist", () => {

        expect(parseInput("2026-10-19", "Date")).toEqual(Temporal.PlainDate.from("2026-10-19"))
        expect(() => parseInput("2026-02-30", "Date")).toThrow("Not a date")
    })

    it("reads a Timestamp in the user's time zone and writes it back the same", () => {

        const instant = parseInput("2026-10-19T14:30", "Timestamp") as Temporal.Instant

        expect(instant).toBeInstanceOf(Temporal.Instant)
        expect(inputText(instant, "Timestamp")).toBe("2026-10-19T14:30:00")
    })

    it("uses a converter's own conversion over the built-in one", () => {

        registerConverter<string, string>("Shout", {
            fromServer: value => value,
            toInput: value => value.toLowerCase(),
            fromInput: text => text.toUpperCase()
        })

        expect(parseInput("hey", "Shout")).toBe("HEY")
        expect(inputText("HEY", "Shout")).toBe("hey")
    })
})
