// @vitest-environment jsdom
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act, Component, ReactNode, useState} from "react";
import {createRoot, Root} from "react-dom/client";
import {and, component, conditionsEqual, field, FilterExpression, or, value} from "../../src/FilterDSL";
import {QueryConfig, QueryConfigDelta} from "../../src/QueryDocument";
import {ColumnFilter, FilterColumn, operatorFilter} from "../../src/grid/filters";
import {Filters, useFilters} from "../../src/grid/useFilters";

const contains = operatorFilter("containsIgnoreCase");
const num = operatorFilter("eq", "Int");

/** writes its input lowercased and reads the lowercased value back, as a normalizing filter does */
const lower: ColumnFilter<string[]> = {
    arity: 1,
    toCondition: (path, [text]) => contains.toCondition(path, [text.toLowerCase()]),
    fromCondition: (path, term) => contains.fromCondition!(path, term)
};

const COLUMNS: FilterColumn[] = [
    {field: "name", filter: contains},
    {field: "num", filter: num}
];

const nameTerm = (text: string) => field("name").containsIgnoreCase(value(text)) as FilterExpression;
const numTerm = (n: number) => field("num").eq(value(n)) as FilterExpression;

let filters: Filters;
let config: QueryConfig;
let setCondition: (condition: FilterExpression | null) => void;
let updates: QueryConfigDelta[];
/** updates held back until the test lets them through, oldest first */
let held: (() => void)[];
let holdUpdates: boolean;

function Harness({initial, columns}: { initial: FilterExpression | null, columns: FilterColumn[] })
{
    const [state, setState] = useState<QueryConfig>({condition: initial, offset: 40, pageSize: 20, sortFields: []});
    config = state;
    setCondition = condition => setState(c => ({...c, condition}));

    const doc = {
        config: state,
        update: (delta: QueryConfigDelta) => {
            updates.push(delta);
            const apply = () => setState(c => ({...c, ...delta}));
            if (holdUpdates)
            {
                return new Promise<void>(resolve => held.push(() => { apply(); resolve(); }));
            }
            apply();
            return Promise.resolve();
        }
    };

    filters = useFilters(doc, "grid", columns, {delay: 100});
    return null;
}

class Boundary extends Component<{ children: ReactNode }, { error: Error | null }>
{
    state = {error: null as Error | null};

    static getDerivedStateFromError(error: Error)
    {
        return {error};
    }

    render()
    {
        return this.state.error ? <p className="error">{ this.state.error.message }</p> : this.props.children;
    }
}

let container: HTMLElement;
let root: Root;

function render(initial: FilterExpression | null, columns: FilterColumn[] = COLUMNS)
{
    act(() => root.render(<Boundary><Harness initial={ initial } columns={ columns }/></Boundary>));
}

function type(column: number, ...values: (string | null)[])
{
    act(() => filters.columns[column].setValues(values));
}

function wait(ms: number = 100)
{
    act(() => { vi.advanceTimersByTime(ms); });
}

beforeEach(() => {
    vi.useFakeTimers();
    vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true);
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    updates = [];
    held = [];
    holdUpdates = false;
});

afterEach(() => {
    act(() => root.unmount());
    container.remove();
    vi.unstubAllGlobals();
    vi.useRealTimers();
});

describe("useFilters", () => {

    it("reads its component of an injected condition", () => {
        render(and(component("grid", and(nameTerm("foo"), numTerm(3))), component("search", nameTerm("x"))));

        expect(filters.columns.map(c => c.values)).toEqual([["foo"], ["3"]]);
        expect(filters.columns.map(c => c.active)).toEqual([true, true]);
        expect(filters.active).toBe(true);
    });

    it("sends typing after the delay, on the first page, leaving other components alone", () => {
        const search = component("search", nameTerm("x"));
        render(search);

        type(0, "fo");
        wait(50);
        type(0, "foo");
        wait(50);
        expect(updates).toEqual([]);

        wait(50);
        expect(updates).toHaveLength(1);
        expect(updates[0].offset).toBe(0);
        expect(conditionsEqual(config.condition, and(search, component("grid", nameTerm("foo"))))).toBe(true);
        expect(filters.columns[0].active).toBe(true);
    });

    it("sends nothing until every input of a filter is filled", () => {
        render(null, [{field: "num", filter: operatorFilter("between", "Int")}]);

        type(0, "1", null);
        wait();
        expect(updates).toEqual([]);

        type(0, "1", "5");
        wait();
        expect(conditionsEqual(config.condition, component("grid", field("num").between(value(1), value(5))))).toBe(true);
    });

    it("filters an expression and reads its term back", () => {
        const sum = field("num").add(field("other"));
        const sumTerm = sum.between(value(1), value(5)) as FilterExpression;
        render(component("grid", sumTerm), [{field: sum, filter: operatorFilter("between", "Int")}]);

        expect(filters.columns[0].values).toEqual(["1", "5"]);
        expect(filters.columns[0].label).toBe("add(num, other)");

        type(0, "2", "6");
        wait();
        expect(conditionsEqual(config.condition, component("grid", sum.between(value(2), value(6))))).toBe(true);
    });

    it("sends a partial filter once any input is filled", () => {
        const atLeast: ColumnFilter<(string | null)[]> = {
            arity: 2,
            partial: true,
            toCondition: (target, [from]) => from === null ? null : target.ge(value(Number(from))) as FilterExpression
        };
        render(null, [{field: "num", filter: atLeast}]);

        type(0, "1", null);
        wait();
        expect(conditionsEqual(config.condition, component("grid", field("num").ge(value(1))))).toBe(true);

        type(0, null, null);
        wait();
        expect(conditionsEqual(config.condition, component("grid", null))).toBe(true);
    });

    it("keeps what the user typed when its own term comes back", () => {
        render(null, [{field: "name", filter: lower}]);

        type(0, "FoO");
        wait();
        expect(conditionsEqual(config.condition, component("grid", nameTerm("foo")))).toBe(true);
        expect(filters.columns[0].values).toEqual(["FoO"]);
    });

    it("keeps the terms of a filter that can't read them back as its own", () => {
        const blind: ColumnFilter<string[]> = {arity: 1, toCondition: contains.toCondition};
        render(null, [{field: "name", filter: blind}]);

        type(0, "foo");
        wait();
        expect(conditionsEqual(config.condition, component("grid", nameTerm("foo")))).toBe(true);
        expect(filters.unclaimed).toEqual([]);

        type(0, null);
        wait();
        expect(conditionsEqual(config.condition, component("grid", null))).toBe(true);
    });

    it("keeps typing that overtook a slow update", () => {
        holdUpdates = true;
        render(null);

        type(0, "ab");
        wait();
        type(0, "abc");
        wait();
        expect(updates).toHaveLength(2);

        act(() => held.shift()!());
        expect(filters.columns[0].values).toEqual(["abc"]);

        act(() => held.shift()!());
        expect(filters.columns[0].values).toEqual(["abc"]);
        expect(conditionsEqual(config.condition, component("grid", nameTerm("abc")))).toBe(true);
    });

    it("shows a change made from outside", () => {
        render(component("grid", nameTerm("foo")));

        act(() => setCondition(component("grid", numTerm(7))));
        expect(filters.columns.map(c => c.values)).toEqual([[null], ["7"]]);
        expect(filters.columns.map(c => c.active)).toEqual([false, true]);
    });

    it("owns a plain condition whole", () => {
        render(nameTerm("foo"));
        expect(filters.columns[0].values).toEqual(["foo"]);

        type(0, "bar");
        wait();
        expect(conditionsEqual(config.condition, nameTerm("bar"))).toBe(true);
    });

    it("keeps unclaimed terms until reset", async () => {
        const other = field("description").isNull() as FilterExpression;
        const search = component("search", nameTerm("x"));
        render(and(search, component("grid", and(nameTerm("foo"), other))));
        expect(filters.unclaimed).toEqual([other]);

        type(1, "3");
        wait();
        expect(conditionsEqual(
            config.condition,
            and(search, component("grid", and(nameTerm("foo"), numTerm(3), other)))
        )).toBe(true);

        await act(() => filters.reset());
        expect(conditionsEqual(config.condition, and(search, component("grid", null)))).toBe(true);
        expect(filters.columns.map(c => c.values)).toEqual([[null], [null]]);
        expect(filters.unclaimed).toEqual([]);
        expect(filters.active).toBe(false);
    });

    it("reports an or composition without its slot", () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        render(or(component("a", nameTerm("x")), component("b", nameTerm("y"))));

        type(0, "foo");
        wait();
        expect(container.querySelector(".error")!.textContent).toMatch(/component\("grid", null\)/);
        expect(updates).toEqual([]);
    });
});
