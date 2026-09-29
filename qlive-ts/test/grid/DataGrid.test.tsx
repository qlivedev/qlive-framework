// @vitest-environment jsdom
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import {component, field, FilterExpression, value} from "../../src/FilterDSL";
import {QueryConfig} from "../../src/QueryDocument";
import DataGrid, {GridDocument} from "../../src/grid/DataGrid";
import {initGridConfig} from "../fixtures/gridConfig";

type Row = {
    id: string
    name: string
    num: number
    flag: boolean
    owner: { id: string, login: string } | null
};

const ROWS: Row[] = [
    {id: "foo-1", name: "Foo #1", num: 1, flag: true, owner: {id: "user-1", login: "admin"}},
    {id: "foo-2", name: "Foo #2", num: 2, flag: false, owner: null}
];

function doc(config: Partial<QueryConfig> = {}, rows: Row[] = ROWS): GridDocument<Row>
{
    return {
        type: "Foo",
        config: {condition: null, offset: 0, pageSize: 10, sortFields: [], ...config},
        rows,
        rowCount: rows.length,
        update: vi.fn(() => Promise.resolve({}))
    };
}

let container: HTMLElement;
let root: Root;

function render(element: React.ReactNode)
{
    act(() => root.render(element));
    return container.firstElementChild as HTMLElement;
}

function texts(elements: ArrayLike<Element>)
{
    return Array.from(elements, e => e.textContent);
}

beforeAll(initGridConfig);

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
    vi.useRealTimers();
});

describe("DataGrid", () => {

    it("renders headings, a filter row and a row per row", () => {
        const grid = render(
            <DataGrid doc={ doc() } columns={ ["name", "owner", {heading: "Actions", render: () => <button/>}] }/>
        );

        expect(grid.className).toBe("qlive-grid");
        const headings = grid.querySelectorAll(".qlive-grid-headings th");
        expect(texts(headings)).toEqual(["[Foo.name]", "[Foo.owner]", "Actions"]);
        expect(Array.from(headings, h => h.className)).toEqual([
            "qlive-grid-sort-header", "qlive-grid-sort-header", "qlive-grid-heading"
        ]);

        const filterCells = grid.querySelectorAll(".qlive-grid-filters td");
        expect(Array.from(filterCells, td => td.querySelectorAll("input").length)).toEqual([1, 1, 0]);

        const rows = grid.querySelectorAll("tbody tr");
        expect(Array.from(rows, r => r.getAttribute("data-id"))).toEqual(["foo-1", "foo-2"]);
        expect(texts(rows[0].querySelectorAll("td")).slice(0, 2)).toEqual(["Foo #1", "admin"]);
        expect(texts(rows[1].querySelectorAll("td")).slice(0, 2)).toEqual(["Foo #2", ""]);

        expect(grid.querySelector(".qlive-grid-footer .qlive-grid-pager")).not.toBeNull();
    });

    it("takes only the paths the rows have", () => {
        type Columns = Parameters<typeof DataGrid<Row>>[0]["columns"];
        const columns: Columns = ["name", "owner", "owner.login", {field: "num", render: row => row.num + 1}];
        // @ts-expect-error: a typo, or a field the query doesn't select
        const typo: Columns = ["nmae"];
        expect([columns, typo]).toHaveLength(2);
    });

    it("leaves out the filter row when no column filters", () => {
        const grid = render(<DataGrid doc={ doc() } columns={ [{field: "name", filter: false}] }/>);
        expect(grid.querySelector(".qlive-grid-filters")).toBeNull();
    });

    it("classes rows and cells", () => {
        const grid = render(
            <DataGrid doc={ doc() } highlighted="foo-2" className="qlive-grid-striped"
                      rowClassName={ row => row.flag ? "flagged" : undefined }
                      columns={ [{field: "name", nowrap: true, maxWidth: "10em", className: "name"}, "num"] }/>
        );

        expect(grid.className).toBe("qlive-grid qlive-grid-striped");
        const rows = grid.querySelectorAll("tbody tr");
        expect(Array.from(rows, r => r.className)).toEqual([
            "qlive-grid-row flagged", "qlive-grid-row qlive-grid-highlighted"
        ]);

        const [name, num] = Array.from(rows[0].querySelectorAll("td")) as HTMLElement[];
        expect(name.className).toBe("qlive-grid-nowrap name");
        expect(name.style.maxWidth).toBe("10em");
        expect(num.hasAttribute("class")).toBe(false);
    });

    it("says so when there are no rows", () => {
        const grid = render(<DataGrid doc={ doc({}, []) } columns={ ["name", "num"] }/>);

        const empty = grid.querySelector("tbody tr.qlive-grid-empty td") as HTMLTableCellElement;
        expect(empty.colSpan).toBe(2);
        expect(empty.textContent).toBe("[No rows]");
    });

    it("sorts by a column's key when its header is clicked", () => {
        const d = doc();
        const grid = render(<DataGrid doc={ d } columns={ ["name", "owner"] }/>);

        act(() => (grid.querySelectorAll(".qlive-grid-headings button")[1] as HTMLElement).click());
        expect(d.update).toHaveBeenCalledWith({sortFields: ["owner.login"], offset: 0});
    });

    it("writes its filters as its component of the condition", () => {
        vi.useFakeTimers();
        const d = doc();
        const grid = render(<DataGrid doc={ d } id="foo-grid" columns={ ["name", "num"] }/>);

        const input = grid.querySelectorAll(".qlive-grid-filters input")[1] as HTMLInputElement;
        const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!;
        act(() => {
            setter.call(input, "2");
            input.dispatchEvent(new Event("input", {bubbles: true}));
        });
        act(() => vi.advanceTimersByTime(300));

        expect(d.update).toHaveBeenCalledWith({
            condition: component("foo-grid", field("num").eq(value(2, "Int")) as FilterExpression),
            offset: 0
        });
    });

    it("notes what it can't show on a column, and offers a reset", async () => {
        const unclaimed = field("description").isNull() as FilterExpression;
        const d = doc({condition: component("grid", unclaimed), sortFields: ["name", "!created"]});
        const grid = render(<DataGrid doc={ d } columns={ ["name", "num"] }/>);

        expect(texts(grid.querySelectorAll(".qlive-grid-note")))
            .toEqual(["[Also sorted by other fields]", "[Additional filter active]"]);

        const reset = grid.querySelector(".qlive-grid-reset") as HTMLButtonElement;
        await act(async () => reset.click());
        expect(d.update).toHaveBeenCalledWith({condition: component("grid", null), offset: 0});
    });

    it("leaves a failed update to the document rather than rejecting unhandled", async () => {
        vi.useFakeTimers();
        const d = doc({condition: component("grid", field("name").isNull() as FilterExpression)});
        const failed = vi.fn(() => Promise.reject(new Error("Not authenticated")));
        d.update = failed;
        const grid = render(<DataGrid doc={ d } columns={ ["name", "num"] }/>);

        const input = grid.querySelectorAll(".qlive-grid-filters input")[1] as HTMLInputElement;
        const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!;
        await act(async () => {
            (grid.querySelector(".qlive-grid-headings button") as HTMLElement).click();
            const size = grid.querySelector(".qlive-grid-pager-size select") as HTMLSelectElement;
            size.value = "20";
            size.dispatchEvent(new Event("change", {bubbles: true}));
            (grid.querySelector(".qlive-grid-reset") as HTMLElement).click();
            setter.call(input, "2");
            input.dispatchEvent(new Event("input", {bubbles: true}));
            vi.advanceTimersByTime(300);
        });

        expect(failed).toHaveBeenCalledTimes(4);
    });

    it("shows the document's error above the rows", () => {
        const d = {...doc(), error: new Error("Not authenticated")};
        const grid = render(<DataGrid doc={ d } columns={ ["name", "num"] }/>);

        const error = grid.firstElementChild as HTMLElement;
        expect(error.className).toBe("qlive-grid-error");
        expect(error.getAttribute("role")).toBe("alert");
        expect(error.textContent).toBe("[Rows not updated:Not authenticated]");
        expect(grid.querySelectorAll("tbody tr")).toHaveLength(2);
    });

    it("shows an error without a message all the same", () => {
        const grid = render(<DataGrid doc={ {...doc(), error: new Error()} } columns={ ["name"] }/>);
        expect(grid.querySelector(".qlive-grid-error")!.textContent).toBe("[Rows not updated]");
    });

    it("shows no error line without an error", () => {
        const grid = render(<DataGrid doc={ {...doc(), error: null} } columns={ ["name"] }/>);
        expect(grid.querySelector(".qlive-grid-error")).toBeNull();
    });

    it("counts an expression over a column's field as shown on that column", () => {
        const grid = render(
            <DataGrid doc={ doc({sortFields: [field("num").mod(value(10))]}) } columns={ ["name", "num"] }/>
        );

        expect(grid.querySelectorAll(".qlive-grid-note")).toHaveLength(0);
        expect(grid.querySelector(".qlive-grid-sorted-partial")!.textContent).toBe("[Foo.num]▲");
    });

    it("notes nothing where every sort field and term has its column", () => {
        const term = field("name").containsIgnoreCase(value("x")) as FilterExpression;
        const grid = render(
            <DataGrid doc={ doc({condition: term, sortFields: ["!name"]}) } columns={ ["name", "num"] }/>
        );

        expect(grid.querySelectorAll(".qlive-grid-note")).toHaveLength(0);
        expect((grid.querySelector(".qlive-grid-filters input") as HTMLInputElement).value).toBe("x");
        expect(grid.querySelector(".qlive-grid-reset")).not.toBeNull();
    });
});
