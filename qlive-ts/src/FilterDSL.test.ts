import {describe, expect, test} from "vitest";
import {and, field, or, value, type FilterExpression, type LogicalOperand} from "./FilterDSL";

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
