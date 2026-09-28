import {describe, expect, test} from "vitest";
import {Temporal} from "temporal-polyfill";
import {
    and,
    component,
    conditionsEqual,
    field,
    isComposedComponentExpression,
    now,
    or,
    ownedPart,
    today,
    updateComponent,
    value,
    values,
    type Condition,
    type ConditionNode,
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


describe("component ownership", () => {

    const nameTerm = () => field("name").containsIgnoreCase(value("foo"));
    const ownerTerm = () => field("owner.login").eq(value("admin"));

    describe("updateComponent", () => {

        test("replaces the term of the component", () => {
            const cond = and(component("grid", nameTerm()), component("search", ownerTerm()))!;
            const updated = updateComponent(cond, "grid", field("name").eq(value("x")));
            expect(conditionsEqual(
                updated,
                and(component("grid", field("name").eq(value("x"))), component("search", ownerTerm()))
            )).toBe(true);
            expect((updated as ConditionNode).operands[1]).toBe((cond as ConditionNode).operands[1]);
        });

        test("returns the same object for an equal term", () => {
            const cond = and(component("grid", nameTerm()), component("search", null))!;
            expect(updateComponent(cond, "grid", nameTerm())).toBe(cond);
            expect(updateComponent(cond, "search", null)).toBe(cond);

            const lone = component("grid", nameTerm());
            expect(updateComponent(lone, "grid", nameTerm())).toBe(lone);
        });

        test("keeps an empty component", () => {
            const cond = and(component("grid", nameTerm()), component("search", ownerTerm()))!;
            expect(conditionsEqual(
                updateComponent(cond, "grid", null),
                and(component("grid", null), component("search", ownerTerm()))
            )).toBe(true);
        });

        test("adds a missing component to an and composition on the same level", () => {
            const cond = and(component("search", ownerTerm()), component("picker", null))!;
            expect(conditionsEqual(
                updateComponent(cond, "grid", nameTerm()),
                and(component("search", ownerTerm()), component("picker", null), component("grid", nameTerm()))
            )).toBe(true);
        });

        test("joins a lone component of someone else with and", () => {
            const updated = updateComponent(component("search", ownerTerm()), "grid", nameTerm());
            expect(conditionsEqual(
                updated,
                and(component("search", ownerTerm()), component("grid", nameTerm()))
            )).toBe(true);
            expect(isComposedComponentExpression(updated!)).toBe(true);
        });

        test("makes no condition the lone component", () => {
            expect(conditionsEqual(updateComponent(null, "grid", nameTerm()), component("grid", nameTerm()))).toBe(true);
            expect(updateComponent(null, "grid", null)).toBe(null);
        });

        test("lets two owners share a condition that started empty", () => {
            const first = updateComponent(null, "search", ownerTerm());
            expect(conditionsEqual(
                updateComponent(first, "grid", nameTerm()),
                and(component("search", ownerTerm()), component("grid", nameTerm()))
            )).toBe(true);
        });

        test("replaces inside an or composition but refuses to add to one", () => {
            const cond = or(component("grid", null), component("search", ownerTerm()))!;
            expect(conditionsEqual(
                updateComponent(cond, "grid", nameTerm()),
                or(component("grid", nameTerm()), component("search", ownerTerm()))
            )).toBe(true);

            expect(() => updateComponent(cond, "picker", nameTerm())).toThrow(/component\("picker", null\)/);
        });

        test("replaces any other condition whole with the plain term", () => {
            const cond = and(nameTerm(), ownerTerm())!;
            expect(updateComponent(cond, "grid", ownerTerm())).toMatchObject({type: "Condition", name: "eq"});
            expect(updateComponent(cond, "grid", and(nameTerm(), ownerTerm()))).toBe(cond);
            expect(updateComponent(cond, "grid", null)).toBe(null);
        });

        test("keeps the fluent methods on plain JSON input", () => {
            const cond = JSON.parse(JSON.stringify(and(component("grid", null), component("search", null))));
            const updated = updateComponent(cond, "grid", nameTerm()) as Condition;
            expect(typeof updated.and).toBe("function");
        });
    });

    describe("ownedPart", () => {

        test("reads the owner's component in a composition", () => {
            const cond = and(component("grid", nameTerm()), component("search", ownerTerm()))!;
            expect(conditionsEqual(ownedPart(cond, "grid"), nameTerm())).toBe(true);
            expect(ownedPart(cond, "picker")).toBe(null);
        });

        test("treats a lone component as a composition of one", () => {
            expect(conditionsEqual(ownedPart(component("grid", nameTerm()), "grid"), nameTerm())).toBe(true);
            expect(ownedPart(component("search", ownerTerm()), "grid")).toBe(null);
        });

        test("owns any other condition whole", () => {
            const cond = and(nameTerm(), ownerTerm())!;
            expect(ownedPart(cond, "grid")).toBe(cond);
            expect(ownedPart(null, "grid")).toBe(null);
        });

        test("reads an empty component as nothing", () => {
            expect(ownedPart(and(component("grid", null), component("search", ownerTerm())), "grid")).toBe(null);
        });
    });
});
