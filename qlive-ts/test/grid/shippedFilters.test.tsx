// @vitest-environment jsdom
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import {and, condition, conditionsEqual, field, FilterExpression, not, operation, or, value} from "../../src/FilterDSL";
import {dateRangeFilter} from "../../src/grid/dateRangeFilter";
import {ColumnFilter} from "../../src/grid/filters";
import FilterInput from "../../src/grid/FilterInput";
import {flagSetFilter} from "../../src/grid/flagSetFilter";
import {numberContainsFilter} from "../../src/grid/numberContainsFilter";
import {patternFilter} from "../../src/grid/patternFilter";
import {pick} from "../../src/grid/pick";
import {ColumnFilterState} from "../../src/grid/useFilters";
import {initGridConfig} from "../fixtures/gridConfig";

beforeAll(initGridConfig);

/** the term, and that the filter reads it back as the values it came from */
function roundTrip<V extends unknown[]>(filter: ColumnFilter<V>, path: string, values: V): FilterExpression | null
{
    const term = filter.toCondition(path, values);
    expect(term && filter.fromCondition!(path, term)).toEqual(term && values);
    return term;
}

function same(a: FilterExpression | null, b: FilterExpression | null)
{
    expect(conditionsEqual(a, b), JSON.stringify(a) + " vs. " + JSON.stringify(b)).toBe(true);
}

describe("dateRangeFilter", () => {

    const dates = dateRangeFilter("Date");
    const times = dateRangeFilter("Timestamp", {timeZone: "Europe/Berlin"});

    it("filters a Date column by the dates as they are, open at either end", () => {
        same(roundTrip(dates, "day", ["2026-09-01", "2026-09-30"]),
            field("day").between(value("2026-09-01", "Date"), value("2026-09-30", "Date")) as FilterExpression);
        same(roundTrip(dates, "day", ["2026-09-01", null]), field("day").ge(value("2026-09-01", "Date")) as FilterExpression);
        same(roundTrip(dates, "day", [null, "2026-09-30"]), field("day").le(value("2026-09-30", "Date")) as FilterExpression);
    });

    it("filters a Timestamp column by whole days in the time zone", () => {
        const term = roundTrip(times, "created", ["2026-09-01", "2026-09-30"]);
        same(term, and(
            field("created").ge(value("2026-08-31T22:00:00Z", "Timestamp")) as FilterExpression,
            field("created").lt(value("2026-09-30T22:00:00Z", "Timestamp")) as FilterExpression
        ));

        // the winter time day after the switch starts an hour later in UTC
        same(roundTrip(times, "created", [null, "2026-10-25"]),
            field("created").lt(value("2026-10-25T23:00:00Z", "Timestamp")) as FilterExpression);
        same(roundTrip(times, "created", ["2026-10-26", null]),
            field("created").ge(value("2026-10-25T23:00:00Z", "Timestamp")) as FilterExpression);
    });

    it("filters nothing for a date that isn't one", () => {
        expect(dates.toCondition("day", ["2026-02-30", null])).toBeNull();
        expect(times.toCondition("created", ["2026-09", "2026-09-30"])).toBeNull();
    });

    it("recognizes only its own terms", () => {
        expect(times.fromCondition!("created", field("created").ge(value("2026-09-01T10:00:00Z", "Timestamp")) as FilterExpression))
            .toBeNull();
        expect(times.fromCondition!("other", times.toCondition("created", ["2026-09-01", null])!)).toBeNull();
        expect(dates.fromCondition!("day", field("day").ge(value("2026-09-01", "String")) as FilterExpression)).toBeNull();
        expect(dates.fromCondition!("day", and(
            field("day").ge(value("2026-09-01", "Date")) as FilterExpression,
            field("day").le(value("2026-09-30", "Date")) as FilterExpression
        )!)).toBeNull();
    });

    it("takes Date and Timestamp only", () => {
        expect(() => dateRangeFilter("String" as any)).toThrow("dateRangeFilter() filters Date and Timestamp, not \"String\".");
    });
});

describe("numberContainsFilter", () => {

    const digits = numberContainsFilter();

    it("finds the digits in the number as text", () => {
        same(roundTrip(digits, "num", ["234"]),
            condition("contains", [operation("toString", [field("num")]), value("234")]) as FilterExpression);
        expect(digits.toCondition("num", ["  "])).toBeNull();
        expect(digits.fromCondition!("other", digits.toCondition("num", ["1"])!)).toBeNull();
    });
});

describe("patternFilter", () => {

    const pattern = patternFilter();

    const contains = (text: string) => field("name").containsIgnoreCase(value(text)) as FilterExpression;
    const regex = (re: string) =>
        condition("likeRegex", [operation("lower", [field("name")]), value(re)]) as FilterExpression;

    it("matches a plain word anywhere, ignoring case", () => {
        same(roundTrip(pattern, "name", ["Foo"]), contains("Foo"));
    });

    it("matches a word with wildcards against the whole value", () => {
        same(roundTrip(pattern, "name", ["foo*#1"]), regex("^foo.*#1$"));
        same(pattern.toCondition("name", ["F.o*"]), regex("^f\\.o.*$"));
        expect(pattern.fromCondition!("name", regex("^f\\.o.*$"))).toEqual(["f.o*"]);
    });

    it("combines words with & and |, & binding tighter, and negates with !", () => {
        same(roundTrip(pattern, "name", ["foo & !bar | baz*"]), or(
            and(contains("foo"), not(contains("bar")) as FilterExpression),
            regex("^baz.*$")
        ));
    });

    it("leaves out empty words, so a pattern being typed filters by what is there", () => {
        same(pattern.toCondition("name", ["foo & "]), contains("foo"));
        same(pattern.toCondition("name", ["foo | !"]), contains("foo"));
        expect(pattern.toCondition("name", ["&"])).toBeNull();
    });

    it("matches another type as text", () => {
        same(patternFilter("Int").toCondition("num", ["1*"]),
            condition("likeRegex", [operation("lower", [operation("toString", [field("num")])]), value("^1.*$")]) as FilterExpression);
        expect(patternFilter("Int").fromCondition!("num", contains("1"))).toBeNull();
    });

    it("recognizes only its own terms", () => {
        expect(pattern.fromCondition!("name", regex("foo"))).toBeNull();
        expect(pattern.fromCondition!("name", regex("^fo+$"))).toBeNull();
        expect(pattern.fromCondition!("name", field("name").eq(value("foo")) as FilterExpression)).toBeNull();
        expect(pattern.fromCondition!("name", and(contains("foo"), field("num").eq(value(1)) as FilterExpression)!)).toBeNull();
    });
});

describe("pick", () => {

    const owners = {
        type: "AppUser",
        rows: [{id: "user-1", login: "admin"}, {id: "user-2", login: "userA"}]
    };

    it("filters the foreign key for the chosen row's key", () => {
        const owner = pick(owners);
        expect(owner.key).toBe(true);
        same(roundTrip(owner, "ownerId", ["user-2"]), field("ownerId").eq(value("user-2", "String")) as FilterExpression);
        expect(owner.fromCondition!("other", owner.toCondition("ownerId", ["user-1"])!)).toBeNull();
    });

    it("needs a key of one field and a label", () => {
        expect(() => pick({type: "FooLink", rows: []}))
            .toThrow("pick(): the primary key of FooLink is several fields, so no single foreign key points at it.");
        expect(() => pick({type: "Tag", rows: []})).toThrow("pick(): Tag has no name fields to label its rows with.");
        expect(() => pick<{ id: string }>({type: "Tag", rows: []}, {label: row => row.id})).not.toThrow();
    });
});

describe("flagSetFilter", () => {

    const open = field("closed").isNull() as FilterExpression;
    const flags = flagSetFilter([
        {name: "open", label: "Open", term: () => open},
        {name: "big", label: "Big", term: path => field(path).gt(value(100)) as FilterExpression}
    ]);

    it("combines the terms of the checked flags", () => {
        same(roundTrip(flags, "num", [["open"]]), open);
        same(roundTrip(flags, "num", [["open", "big"]]), and(open, field("num").gt(value(100)) as FilterExpression));
        same(flags.toCondition("num", [["big", "open"]]), flags.toCondition("num", [["open", "big"]]));
        expect(flags.toCondition("num", [[]])).toBeNull();
    });

    it("recognizes only combinations of its flags", () => {
        expect(flags.fromCondition!("num", and(open, open)!)).toBeNull();
        expect(flags.fromCondition!("num", field("num").gt(value(5)) as FilterExpression)).toBeNull();
    });

    it("wants distinct names", () => {
        expect(() => flagSetFilter([
            {name: "a", label: "A", term: () => open},
            {name: "a", label: "B", term: () => open}
        ])).toThrow("flagSetFilter(): two flags have the same name.");
    });
});

describe("the inputs of the shipped filters", () => {

    let container: HTMLElement;
    let root: Root;

    function render(filter: ColumnFilter<any>, values: unknown[])
    {
        const state: ColumnFilterState = {field: "f", filter, values, setValues: vi.fn(), active: false};
        act(() => root.render(<FilterInput column={ state }/>));
        return {div: container.firstElementChild as HTMLElement, setValues: state.setValues as ReturnType<typeof vi.fn>};
    }

    function change(element: HTMLInputElement | HTMLSelectElement, text: string)
    {
        const proto = element instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
        act(() => {
            Object.getOwnPropertyDescriptor(proto, "value")!.set!.call(element, text);
            element.dispatchEvent(new Event(element instanceof HTMLSelectElement ? "change" : "input", {bubbles: true}));
        });
    }

    beforeEach(() => {
        vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true);
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        vi.unstubAllGlobals();
    });

    it("gives a date range two date inputs", () => {
        const {div, setValues} = render(dateRangeFilter("Date"), ["2026-09-01", null]);
        const inputs = Array.from(div.querySelectorAll("input"));
        expect(inputs.map(i => [i.type, i.value])).toEqual([["date", "2026-09-01"], ["date", ""]]);

        change(inputs[1], "2026-09-30");
        expect(setValues).toHaveBeenCalledWith(["2026-09-01", "2026-09-30"]);
    });

    it("gives pick() a select of the catalog, and shows a key it doesn't hold as itself", () => {
        const owners = {type: "AppUser", rows: [{id: "user-1", login: "admin"}]};
        const {div, setValues} = render(pick(owners), ["user-9"]);
        const select = div.querySelector("select")!;
        expect(Array.from(select.options).map(o => [o.value, o.textContent])).toEqual([
            ["", "[Any]"], ["user-9", "user-9"], ["user-1", "admin"]
        ]);
        expect(select.value).toBe("user-9");

        change(select, "user-1");
        expect(setValues).toHaveBeenCalledWith(["user-1"]);
        change(select, "");
        expect(setValues).toHaveBeenLastCalledWith([null]);
    });

    it("gives a flag set a checkbox per flag", () => {
        const flags = flagSetFilter([
            {name: "a", label: "A", term: () => field("a").isTrue() as FilterExpression},
            {name: "b", label: "B", term: () => field("b").isTrue() as FilterExpression}
        ]);
        const {div, setValues} = render(flags, [["b"]]);
        const boxes = Array.from(div.querySelectorAll("input"));
        expect(boxes.map(b => [b.parentElement!.textContent, b.checked])).toEqual([["A", false], ["B", true]]);

        act(() => boxes[0].click());
        expect(setValues).toHaveBeenCalledWith([["b", "a"]]);
        act(() => boxes[1].click());
        expect(setValues).toHaveBeenLastCalledWith([null]);
    });
});
