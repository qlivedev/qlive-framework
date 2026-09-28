import {beforeAll, describe, expect, it} from "vitest";
import {Temporal} from "temporal-polyfill";
import {
    and,
    component,
    condition,
    field,
    FilterExpression,
    not,
    now,
    operation,
    or,
    value,
    values
} from "../src/FilterDSL";
import {conditionPredicate, evaluateQuery, sortComparator} from "../src/evaluate";
import {dateRangeFilter} from "../src/grid/dateRangeFilter";
import {numberContainsFilter} from "../src/grid/numberContainsFilter";
import {patternFilter} from "../src/grid/patternFilter";
import {compareNumbers, scalarCompare} from "../src/util/scalar";
import {initGridConfig} from "./fixtures/gridConfig";

beforeAll(initGridConfig);

type Tag = { id: string, name: string | null, rank: number | null };

type Row = {
    id: string
    name: string
    num: number
    ratio: number | null
    flag: boolean
    created: Temporal.Instant
    day: string | null
    ownerId: string | null
    owner: { id: string, login: string } | null
    tags: Tag[]
};

const ROWS: Row[] = [
    {
        id: "foo-1", name: "Alpha", num: 12, ratio: 0.5, flag: true,
        created: Temporal.Instant.from("2026-09-01T08:00:00Z"), day: "2026-09-01",
        ownerId: "user-1", owner: {id: "user-1", login: "admin"},
        tags: [{id: "t1", name: "red", rank: 1}, {id: "t2", name: "blue", rank: 2}]
    },
    {
        id: "foo-2", name: "beta", num: 222, ratio: null, flag: false,
        created: Temporal.Instant.from("2026-09-02T23:30:00Z"), day: null,
        ownerId: null, owner: null,
        tags: [{id: "t3", name: "red", rank: 3}]
    },
    {
        id: "foo-3", name: "Gamma", num: 7, ratio: 2.25, flag: true,
        created: Temporal.Instant.from("2026-09-03T12:00:00Z"), day: "2026-09-03",
        ownerId: "user-2", owner: {id: "user-2", login: "editor"},
        tags: []
    }
];

function ids(term: FilterExpression | null): string[]
{
    return ROWS.filter(conditionPredicate<Row>("Foo", term)).map(row => row.id);
}

describe("conditionPredicate", () => {

    it("matches everything without a condition, or with an empty component", () => {
        expect(ids(null)).toEqual(["foo-1", "foo-2", "foo-3"]);
        expect(ids(and(component("grid", null), component("search", null)))).toEqual(["foo-1", "foo-2", "foo-3"]);
    });

    it("compares by the type of the field", () => {
        expect(ids(field("num").gt(value(10)))).toEqual(["foo-1", "foo-2"]);
        expect(ids(field("num").between(value(7), value(12)))).toEqual(["foo-1", "foo-3"]);
        expect(ids(field("flag").isTrue())).toEqual(["foo-1", "foo-3"]);
        expect(ids(field("name").eq(value("beta")))).toEqual(["foo-2"]);
        expect(ids(field("day").lt(value("2026-09-02", "Date")))).toEqual(["foo-1"]);
    });

    it("reads a value as the type of what it is compared to", () => {
        expect(ids(field("num").eq(value("12", "String")))).toEqual(["foo-1"]);
        expect(ids(field("name").eq(value(7)))).toEqual([]);
        expect(ids(field("created").ge(value("2026-09-02T00:00:00Z", "Timestamp"))))
            .toEqual(["foo-2", "foo-3"]);
        expect(ids(field("created").lt(now()))).toEqual(["foo-1", "foo-2", "foo-3"]);
    });

    it("is three-valued: a comparison with null is never true, negated or not", () => {
        expect(ids(field("ratio").lt(value(1.0, "Float")))).toEqual(["foo-1"]);
        expect(ids(not(field("ratio").lt(value(1.0, "Float"))) as FilterExpression)).toEqual(["foo-3"]);
        expect(ids(field("ratio").isNull())).toEqual(["foo-2"]);
        expect(ids(or(field("ratio").lt(value(1.0, "Float")), field("flag").isFalse()))).toEqual(["foo-1", "foo-2"]);
        expect(ids(field("ratio").isDistinctFrom(value(0.5, "Float")))).toEqual(["foo-2", "foo-3"]);
    });

    it("combines with and, or, not, andNot and orNot", () => {
        const big = field("num").gt(value(10));
        const flagged = field("flag").isTrue();
        expect(ids(and(big, flagged))).toEqual(["foo-1"]);
        expect(ids(or(big, flagged))).toEqual(["foo-1", "foo-2", "foo-3"]);
        expect(ids(condition("andNot", [big, flagged]) as FilterExpression)).toEqual(["foo-2"]);
        expect(ids(condition("orNot", [big, flagged]) as FilterExpression)).toEqual(["foo-1", "foo-2"]);
    });

    it("follows a to-one relation, and finds nothing past a null one", () => {
        expect(ids(field("owner.login").eq(value("admin")))).toEqual(["foo-1"]);
        expect(ids(not(field("owner.login").eq(value("admin"))) as FilterExpression)).toEqual(["foo-3"]);
    });

    it("asks of a to-many relation whether some element matches, one element per comparison", () => {
        expect(ids(field("tags.name").eq(value("red")))).toEqual(["foo-1", "foo-2"]);
        expect(ids(not(field("tags.name").eq(value("red"))) as FilterExpression)).toEqual(["foo-3"]);
        // two comparisons, each asking of its own: foo-2's red tag is ranked 3, but it has no blue one
        expect(ids(and(
            field("tags.rank").gt(value(1)),
            field("tags.name").eq(value("blue"))
        ))).toEqual(["foo-1"]);

        // two paths in one comparison read one element: foo-1 has red ranked 1 and blue ranked 2, no blue ranked 1
        const nameAndRank = (text: string) =>
            condition("eq", [operation("concat", [field("tags.name"), field("tags.rank")]), value(text)]) as FilterExpression;
        expect(ids(nameAndRank("red1"))).toEqual(["foo-1"]);
        expect(ids(nameAndRank("blue1"))).toEqual([]);
        expect(ids(condition("eq", [operation("add", [field("tags.rank"), value(1)]), field("tags.rank")]) as FilterExpression))
            .toEqual([]);
    });

    it("computes operations as the type of their first operand", () => {
        expect(ids(field("num").div(value(5)).eq(value(2)))).toEqual(["foo-1"]);
        expect(ids(field("ratio").mul(value(2, "Float")).eq(value(4.5, "Float")))).toEqual(["foo-3"]);
        expect(ids(field("num").bitAnd(value("8", "String")).eq(value(8)))).toEqual(["foo-1", "foo-2"]);
        expect(ids(field("name").lower().concat(value("!")).eq(value("alpha!")))).toEqual(["foo-1"]);
        expect(() => ids(field("num").div(value(0)).eq(value(1)))).toThrow(/Division by zero/);
    });

    it("matches text the way the database does", () => {
        expect(ids(field("name").containsIgnoreCase(value("A")))).toEqual(["foo-1", "foo-2", "foo-3"]);
        expect(ids(field("name").contains(value("a")))).toEqual(["foo-1", "foo-2", "foo-3"]);
        expect(ids(field("name").startsWith(value("G")))).toEqual(["foo-3"]);
        expect(ids(field("name").likeRegex(value("^[a-z]")))).toEqual(["foo-2"]);
        expect(ids(field("name").likeRegex(value("mm")))).toEqual(["foo-3"]);
        expect(ids(field("name").equalIgnoreCase(value("ALPHA")))).toEqual(["foo-1"]);
    });

    it("takes a list for in, and is unknown for a value it lacks when the list holds null", () => {
        expect(ids(field("num").in(values("Int", 7, 12)))).toEqual(["foo-1", "foo-3"]);
        expect(ids(not(field("num").in(values("Int", 7, null))) as FilterExpression)).toEqual([]);
    });

    it("agrees with what the shipped filters write", () => {
        const pattern = patternFilter();
        expect(ids(pattern.toCondition("name", ["a*a"]))).toEqual(["foo-1"]);
        expect(ids(pattern.toCondition("name", ["*a"]))).toEqual(["foo-1", "foo-2", "foo-3"]);
        expect(ids(pattern.toCondition("name", ["!beta"]))).toEqual(["foo-1", "foo-3"]);
        expect(ids(pattern.toCondition("name", ["alp|gam"]))).toEqual(["foo-1", "foo-3"]);
        expect(ids(patternFilter("Int").toCondition("num", ["2*2"]))).toEqual(["foo-2"]);

        expect(ids(numberContainsFilter().toCondition("num", ["22"]))).toEqual(["foo-2"]);

        const days = dateRangeFilter("Timestamp", {timeZone: "UTC"});
        expect(ids(days.toCondition("created", ["2026-09-02", "2026-09-02"]))).toEqual(["foo-2"]);
        expect(ids(days.toCondition("created", [null, "2026-09-02"]))).toEqual(["foo-1", "foo-2"]);
        const berlin = dateRangeFilter("Timestamp", {timeZone: "Europe/Berlin"});
        expect(ids(berlin.toCondition("created", ["2026-09-03", null]))).toEqual(["foo-2", "foo-3"]);
        expect(ids(dateRangeFilter("Date").toCondition("day", ["2026-09-02", null]))).toEqual(["foo-3"]);
    });

    it("refuses at once what it can't evaluate", () => {
        expect(() => conditionPredicate("Foo", field("nope").eq(value(1))))
            .toThrow('Condition on Foo: Foo has no field "nope".');
        expect(() => conditionPredicate("Foo", field("owner").isNull()))
            .toThrow(/"owner" is a AppUser, not a value/);
        expect(() => conditionPredicate("Foo", condition("frobnicate", [field("num")]) as FilterExpression))
            .toThrow(/invalid filter operator: frobnicate/);
        expect(() => conditionPredicate("Foo", condition("lower", [field("name")]) as FilterExpression))
            .toThrow(/produces a value, not a condition/);
        expect(() => conditionPredicate("Foo", condition("eq", [field("num")]) as FilterExpression))
            .toThrow(/'eq' takes 2 operand\(s\), not 1/);
        expect(() => conditionPredicate("Foo", field("created").eq(value("yesterday"))))
            .toThrow();
    });

    it("says which field a row lacks", () => {
        const test = conditionPredicate("Foo", field("ratio").isNull());
        expect(() => test({id: "foo-9", name: "No ratio"})).toThrow(
            'Condition on Foo: the rows have no "ratio". Select it in the query.'
        );
    });
});

describe("sortComparator", () => {

    const order = (...sortFields: Parameters<typeof sortComparator>[1]) =>
        [...ROWS].sort(sortComparator<Row>("Foo", sortFields)).map(row => row.id);

    it("sorts by fields in every form a config has them", () => {
        expect(order("num")).toEqual(["foo-3", "foo-1", "foo-2"]);
        expect(order("!num")).toEqual(["foo-2", "foo-1", "foo-3"]);
        expect(order(operation("desc", [field("created")]))).toEqual(["foo-3", "foo-2", "foo-1"]);
        expect(order(field("flag"), "!num")).toEqual(["foo-2", "foo-1", "foo-3"]);
        expect(order(operation("neg", [field("num")]))).toEqual(["foo-2", "foo-1", "foo-3"]);
    });

    it("orders text by the user's locale, whatever the case", () => {
        expect(order("name")).toEqual(["foo-1", "foo-2", "foo-3"]);
    });

    it("puts nulls last ascending and first descending", () => {
        expect(order("ratio")).toEqual(["foo-1", "foo-3", "foo-2"]);
        expect(order("!ratio")).toEqual(["foo-2", "foo-3", "foo-1"]);
        expect(order("owner.login")).toEqual(["foo-1", "foo-3", "foo-2"]);
    });

    it("refuses a path through a to-many relation", () => {
        expect(() => sortComparator("Foo", ["tags.name"])).toThrow(/cannot follow a to-many relation/);
    });
});

describe("evaluateQuery", () => {

    it("filters, sorts, pages and counts", () => {
        const result = evaluateQuery("Foo", ROWS, {
            condition: field("flag").isTrue(),
            sortFields: ["!num"],
            offset: 1,
            pageSize: 1
        });
        expect(result.rows.map(row => row.id)).toEqual(["foo-3"]);
        expect(result.rowCount).toBe(2);

        expect(evaluateQuery("Foo", ROWS, {condition: null, sortFields: [], offset: 1, pageSize: 0}).rows)
            .toHaveLength(2);
    });
});

describe("scalarCompare", () => {

    it("compares numbers by value across their forms", () => {
        expect(compareNumbers("1E+3", 1000)).toBe(0);
        expect(compareNumbers(12345678901234567890n, "12345678901234567889.5")).toBe(1);
        expect(compareNumbers("-0.5", 0)).toBe(-1);
        expect(compareNumbers("-2", "-10")).toBe(1);
        expect(compareNumbers("0.000", "-0")).toBe(0);
        expect(compareNumbers(1e-7, "0.0000001")).toBe(0);
        expect(scalarCompare("BigDecimal", "2.50", "2.5")).toBe(0);
    });

    it("orders instants through the Timestamp converter and refuses what has no order", () => {
        const earlier = Temporal.Instant.from("2026-09-01T00:00:00Z");
        const later = Temporal.Instant.from("2026-09-02T00:00:00Z");
        expect(scalarCompare("Timestamp", earlier, later)).toBeLessThan(0);
        expect(() => scalarCompare("JSONB", {a: 1}, {a: 2})).toThrow(/Cannot order values of JSONB/);
    });
});
