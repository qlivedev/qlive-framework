import {describe, expect, test} from "vitest";
import {and, conditionsEqual, field, FilterExpression, value} from "../../src/FilterDSL";
import {claimTerms, ColumnFilter, FilterColumn, filled, operatorFilter} from "../../src/grid/filters";

const contains = operatorFilter("containsIgnoreCase");
const num = operatorFilter("eq", "Int");

/** a filter whose term is an `and` of two conditions, like a range written without between */
const ge = operatorFilter("ge", "Int");
const le = operatorFilter("le", "Int");
const range: ColumnFilter<string[]> = {
    arity: 2,
    toCondition: (path, [from, to]) => and(ge.toCondition(path, [from]), le.toCondition(path, [to])),
    fromCondition: (path, term) => {
        if (term.type !== "Condition" || term.name !== "and" || term.operands.length !== 2)
        {
            return null;
        }
        const from = ge.fromCondition!(path, term.operands[0] as FilterExpression);
        const to = le.fromCondition!(path, term.operands[1] as FilterExpression);
        return from && to ? [...from, ...to] : null;
    }
};

describe("operatorFilter", () => {

    test("takes one input per operand", () => {
        expect(contains.arity).toBe(1);
        expect(operatorFilter("between", "Int").arity).toBe(2);
    });

    test("refuses conditions without values and lists", () => {
        expect(() => operatorFilter("isNull")).toThrow(/takes none/);
        expect(() => operatorFilter("in")).toThrow(/filter function/);
        expect(() => operatorFilter("nope" as any)).toThrow(/no such condition/);
    });

    test("builds the condition from typed values", () => {
        expect(conditionsEqual(
            contains.toCondition("name", ["Foo"]),
            field("name").containsIgnoreCase(value("Foo"))
        )).toBe(true);
        expect(conditionsEqual(
            operatorFilter("between", "Int").toCondition("num", ["1", " 5 "]),
            field("num").between(value(1), value(5))
        )).toBe(true);
    });

    test("filters nothing for text that isn't a value of the type", () => {
        expect(num.toCondition("num", ["1x"])).toBeNull();
        expect(num.toCondition("num", ["1.5"])).toBeNull();
        expect(operatorFilter("eq", "Float").toCondition("num", ["1.5"])).not.toBeNull();
        expect(operatorFilter("eq", "Boolean").toCondition("flag", ["maybe"])).toBeNull();
    });

    test("reads back exactly what it writes", () => {
        const term = num.toCondition("num", ["42"])!;
        expect(num.fromCondition!("num", term)).toEqual(["42"]);
        expect(num.fromCondition!("other", term)).toBeNull();
        expect(operatorFilter("ne", "Int").fromCondition!("num", term)).toBeNull();
    });
});

describe("filled", () => {

    test("wants every input", () => {
        expect(filled(["a"])).toBe(true);
        expect(filled(["a", null])).toBe(false);
        expect(filled([""])).toBe(false);
        expect(filled([0])).toBe(true);
    });
});

describe("claimTerms", () => {

    const columns: FilterColumn[] = [
        {field: "name", filter: contains},
        {field: "num", filter: num},
        {field: "num", filter: range}
    ];

    const nameTerm = () => contains.toCondition("name", ["foo"])!;
    const numTerm = () => num.toCondition("num", ["3"])!;
    const rangeTerm = () => range.toCondition("num", ["1", "5"])!;

    test("has nothing to hand out for no part", () => {
        expect(claimTerms(null, columns)).toEqual({terms: [null, null, null], values: [null, null, null], unclaimed: []});
    });

    test("hands a single term to its column", () => {
        const claimed = claimTerms(numTerm(), columns);
        expect(claimed.values).toEqual([null, ["3"], null]);
        expect(claimed.unclaimed).toEqual([]);
    });

    test("hands the operands of an and to their columns", () => {
        const claimed = claimTerms(and(nameTerm(), numTerm()), columns);
        expect(claimed.values).toEqual([["foo"], ["3"], null]);
    });

    test("takes an and a column claims whole as that column's term", () => {
        const claimed = claimTerms(rangeTerm(), columns);
        expect(claimed.values).toEqual([null, null, ["1", "5"]]);
        expect(claimed.unclaimed).toEqual([]);
    });

    test("keeps a nested and together next to other terms", () => {
        const claimed = claimTerms(and(nameTerm(), rangeTerm()), columns);
        expect(claimed.values).toEqual([["foo"], null, ["1", "5"]]);
    });

    test("leaves what no column recognizes unclaimed", () => {
        const other = field("description").isNull() as FilterExpression;
        const claimed = claimTerms(and(nameTerm(), other), columns);
        expect(claimed.values).toEqual([["foo"], null, null]);
        expect(claimed.unclaimed).toEqual([other]);
    });

    test("leaves a term two columns claim unclaimed", () => {
        const twice: FilterColumn[] = [{field: "name", filter: contains}, {field: "name", filter: contains}];
        const claimed = claimTerms(nameTerm(), twice);
        expect(claimed.terms).toEqual([null, null]);
        expect(claimed.unclaimed).toHaveLength(1);
    });

    test("gives a column one term at most", () => {
        const second = contains.toCondition("name", ["bar"])!;
        const claimed = claimTerms(and(nameTerm(), second), columns);
        expect(claimed.values[0]).toEqual(["foo"]);
        expect(claimed.unclaimed).toEqual([second]);
    });
});
