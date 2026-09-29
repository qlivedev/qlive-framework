import {describe, expect, test} from "vitest";
import {and, conditionsEqual, field, FilterExpression, value} from "../../src/FilterDSL";
import {claimTerms, ColumnFilter, FilterColumn, filled, filterLabel, filterTarget, operatorFilter} from "../../src/grid/filters";

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
            contains.toCondition(field("name"), ["Foo"]),
            field("name").containsIgnoreCase(value("Foo"))
        )).toBe(true);
        expect(conditionsEqual(
            operatorFilter("between", "Int").toCondition(field("num"), ["1", " 5 "]),
            field("num").between(value(1), value(5))
        )).toBe(true);
    });

    test("filters nothing for text that isn't a value of the type", () => {
        expect(num.toCondition(field("num"), ["1x"])).toBeNull();
        expect(num.toCondition(field("num"), ["1.5"])).toBeNull();
        expect(operatorFilter("eq", "Float").toCondition(field("num"), ["1.5"])).not.toBeNull();
        expect(operatorFilter("eq", "Boolean").toCondition(field("flag"), ["maybe"])).toBeNull();
    });

    test("reads back exactly what it writes", () => {
        const term = num.toCondition(field("num"), ["42"])!;
        expect(num.fromCondition!(field("num"), term)).toEqual(["42"]);
        expect(num.fromCondition!(field("other"), term)).toBeNull();
        expect(operatorFilter("ne", "Int").fromCondition!(field("num"), term)).toBeNull();
    });

    test("filters an expression like a field", () => {
        const between = operatorFilter("between", "Int");
        const sum = field("numA").add(field("numB"));
        const term = between.toCondition(sum, ["200", "300"])!;
        expect(conditionsEqual(term, sum.between(value(200), value(300)))).toBe(true);

        expect(between.fromCondition!(field("numA").add(field("numB")), term)).toEqual(["200", "300"]);
        expect(between.fromCondition!(field("numA").sub(field("numB")), term)).toBeNull();
        expect(between.fromCondition!(field("numA"), term)).toBeNull();
    });
});

describe("filterTarget", () => {

    test("gives a path, an expression and a plain node the builder methods", () => {
        expect(conditionsEqual(filterTarget("num").eq(value(1)), field("num").eq(value(1)))).toBe(true);

        const sum = field("numA").add(field("numB"));
        expect(filterTarget(sum)).toBe(sum);

        const plain = JSON.parse(JSON.stringify(sum));
        const target = filterTarget(plain);
        expect(target).not.toBe(plain);
        expect(conditionsEqual(target, sum)).toBe(true);
        expect(conditionsEqual(target.between(value(1), value(2)), sum.between(value(1), value(2)))).toBe(true);
    });
});

describe("filterLabel", () => {

    test("names a column by its label, its path, or its expression", () => {
        const filter = operatorFilter("eq", "Int");
        expect(filterLabel({field: "num", filter, label: "Number"})).toBe("Number");
        expect(filterLabel({field: "owner.login", filter})).toBe("owner.login");
        expect(filterLabel({field: field("numA").add(field("numB")), filter})).toBe("add(numA, numB)");
        expect(filterLabel({field: field("num").mul(value(2)), filter})).toBe("mul(num, 2)");
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

    const nameTerm = () => contains.toCondition(field("name"), ["foo"])!;
    const numTerm = () => num.toCondition(field("num"), ["3"])!;
    const rangeTerm = () => range.toCondition(field("num"), ["1", "5"])!;

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
        const second = contains.toCondition(field("name"), ["bar"])!;
        const claimed = claimTerms(and(nameTerm(), second), columns);
        expect(claimed.values[0]).toEqual(["foo"]);
        expect(claimed.unclaimed).toEqual([second]);
    });

    test("hands a column the terms it wrote without asking its filter", () => {
        const blind: FilterColumn = {field: "name", filter: {arity: 1, toCondition: contains.toCondition}};
        const twice: FilterColumn[] = [blind, {field: "name", filter: contains}];

        const claimed = claimTerms(nameTerm(), twice, [[null, nameTerm()], [null]]);
        expect(claimed.terms).toEqual([nameTerm(), null]);
        expect(claimed.values).toEqual([null, null]);
        expect(claimed.unclaimed).toEqual([]);
    });
});
