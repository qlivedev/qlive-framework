import {describe, expect, test} from "vitest";
import {Temporal} from "temporal-polyfill";
import {
    and,
    component,
    conditionsEqual,
    field,
    now,
    or,
    today,
    value,
    values,
    type FilterExpression,
    type LogicalOperand,
    type RawValue
} from "../src/FilterDSL";

/**
 * The logical composers are the functional alternative to the fluent style:
 *
 *   field("v").lessThan(value(12)).and(field("v").greaterThan(value(5)))
 *   and(field("v").lessThan(value(12)), field("v").greaterThan(value(5)))
 *
 * Dropping falsy operands is the point, not a convenience: it lets callers
 * compose from helpers that may contribute nothing and let the final logical
 * shape fall out of whatever survived.
 */
describe("logical composition", () => {

    const a = () => field("name").eq(value("Foo #1"));
    const b = () => field("owner.login").eq(value("admin"));

    test("collapses to null when nothing survives", () => {
        expect(and(null, null)).toBe(null);
        expect(or(undefined, false, null)).toBe(null);
        expect(and()).toBe(null);
    });

    test("passes a lone survivor through unwrapped", () => {
        const only = a();
        expect(and(null, only, undefined)).toBe(only);
        expect(or(false, only)).toBe(only);
    });

    test("builds a real condition once more than one survives", () => {
        const composed = and(a(), null, b());
        expect(composed).toMatchObject({type: "Condition", name: "and"});
        expect((composed as {operands: unknown[]}).operands).toHaveLength(2);
    });

    test("both styles produce the same graph", () => {
        expect(JSON.parse(JSON.stringify(and(a(), b()))))
            .toEqual(JSON.parse(JSON.stringify(a().and(b()))));
    });

    test("composes from helpers that may contribute nothing", () => {
        const maybeName = (n?: string) => n ? field("name").eq(value(n)) : null;
        const maybeOwner = (o?: string) => o ? field("owner.login").eq(value(o)) : null;

        expect(and(maybeName("x"), maybeOwner(undefined)))
            .toMatchObject({type: "Condition", name: "eq"});
        expect(and(maybeName(undefined), maybeOwner(undefined))).toBe(null);
    });

    test("accepts `flag && cond` for boolean flags", () => {
        const flag = false as boolean;
        expect(and(a(), flag && b())).toMatchObject({type: "Condition", name: "eq"});
    });

    // Type-level: these must compile. `flag && cond` only lands in LogicalOperand
    // when flag is a boolean - a truthy-narrowable value needs !!flag or a ternary.
    test("operand and result types accept both styles", () => {
        const flag = true as boolean;
        const operands: LogicalOperand[] = [a(), null, undefined, false, flag && b()];
        const composed: FilterExpression | null = and(...operands);
        const fluent: FilterExpression | null = a().and(b());
        expect(composed).not.toBe(undefined);
        expect(fluent).not.toBe(undefined);
    });
});


describe("conditionsEqual", () => {

    test("compares structure, names and values", () => {
        expect(conditionsEqual(
            field("name").eq(value("a")),
            field("name").eq(value("a"))
        )).toBe(true);
        expect(conditionsEqual(field("name").eq(value("a")), field("name").eq(value("b")))).toBe(false);
        expect(conditionsEqual(field("name").eq(value("a")), field("other").eq(value("a")))).toBe(false);
        expect(conditionsEqual(field("name").eq(value("a")), field("name").ne(value("a")))).toBe(false);
        expect(conditionsEqual(field("num").eq(value(1)), field("num").eq(value(1, "Long")))).toBe(false);
    });

    test("treats null and undefined as no condition", () => {
        expect(conditionsEqual(null, undefined)).toBe(true);
        expect(conditionsEqual(null, field("x").isNull())).toBe(false);
    });

    test("equals the plain JSON the server echoes", () => {
        const cond = and(component("grid", field("name").containsIgnoreCase(value("x"))), component("search", null))!;
        expect(conditionsEqual(cond, JSON.parse(JSON.stringify(cond)))).toBe(true);
    });

    test("compares values by their JSON form", () => {
        const instant = Temporal.Instant.from("2026-09-28T10:00:00Z");
        expect(conditionsEqual(
            field("created").gt(value(instant as unknown as RawValue, "Timestamp")),
            field("created").gt(value("2026-09-28T10:00:00Z", "Timestamp"))
        )).toBe(true);
        expect(conditionsEqual(
            field("num").in(values("Int", 1, 2)),
            field("num").in(values("Int", 1, 3))
        )).toBe(false);
        expect(conditionsEqual(now(), now())).toBe(true);
        expect(conditionsEqual(now(), today())).toBe(false);
    });
});
