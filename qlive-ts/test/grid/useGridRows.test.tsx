// @vitest-environment jsdom
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import {QueryConfig} from "../../src/QueryDocument";
import DataGrid, {GridDocument} from "../../src/grid/DataGrid";
import {resolveColumn} from "../../src/grid/columns";
import {WorkingSet} from "../../src/merge/WorkingSet";
import {initGridConfig} from "../fixtures/gridConfig";

type Row = {
    id: string
    name: string
    num: number
    ownerId: string | null
    owner: { id: string, login: string } | null
};

function rows(): Row[]
{
    return [
        {id: "foo-1", name: "Foo #1", num: 1, ownerId: "user-1", owner: {id: "user-1", login: "admin"}},
        {id: "foo-2", name: "Foo #2", num: 2, ownerId: null, owner: null}
    ];
}

function doc(config: Partial<QueryConfig> = {}, list: Row[] = rows()): GridDocument<Row>
{
    return {
        type: "Foo",
        config: {condition: null, offset: 0, pageSize: 10, sortFields: [], ...config},
        rows: list,
        rowCount: list.length,
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

function bodyRows(grid: HTMLElement)
{
    return Array.from(grid.querySelectorAll("tbody tr.qlive-grid-row")) as HTMLElement[];
}

function cellsOf(row: HTMLElement)
{
    return Array.from(row.querySelectorAll("td"));
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
});

const COLUMNS = ["name", "num", "owner", "owner.login"] as const;

describe("useGridRows() through DataGrid", () => {

    it("shows the document's rows as they are without a working set", () => {
        const grid = render(<DataGrid doc={ doc() } columns={ COLUMNS }/>);

        expect(bodyRows(grid).map(r => r.className)).toEqual(["qlive-grid-row", "qlive-grid-row"]);
    });

    it("registers the document and shows edits to its rows before they are saved", () => {
        const ws = new WorkingSet();
        const d = doc();
        const grid = render(<DataGrid doc={ d } workingSet={ ws } columns={ COLUMNS }/>);

        act(() => { ws.edit(d.rows[0]).name = "Renamed"; });

        const [first, second] = bodyRows(grid);
        expect(first.className).toBe("qlive-grid-row qlive-grid-changed");
        expect(second.className).toBe("qlive-grid-row");
        const [name, num] = cellsOf(first);
        expect(name.textContent).toBe("Renamed");
        expect(name.className).toBe("qlive-changed");
        expect(num.hasAttribute("class")).toBe(false);
    });

    it("marks a relation column by its foreign key, and a field of a related row not at all", () => {
        const ws = new WorkingSet();
        const d = doc();
        const grid = render(<DataGrid doc={ d } workingSet={ ws } columns={ COLUMNS }/>);

        act(() => { ws.edit(d.rows[0]).ownerId = "user-2"; });

        const [, , owner, login] = cellsOf(bodyRows(grid)[0]);
        expect(owner.className).toBe("qlive-changed");
        expect(login.hasAttribute("class")).toBe(false);
    });

    it("lists created rows first on the first page, and only there", () => {
        const ws = new WorkingSet();
        ws.create<Row>("Foo", {id: "foo-new", name: "New Foo"});

        const first = render(<DataGrid doc={ doc() } workingSet={ ws } columns={ COLUMNS }/>);
        const created = bodyRows(first)[0];
        expect(bodyRows(first).map(r => r.getAttribute("data-id"))).toEqual(["foo-new", "foo-1", "foo-2"]);
        expect(created.className).toBe("qlive-grid-row qlive-grid-new");
        expect(cellsOf(created).map(td => td.textContent)).toEqual(["New Foo", "", "", ""]);
        expect(cellsOf(created).some(td => td.hasAttribute("class"))).toBe(false);

        const second = render(<DataGrid doc={ doc({offset: 10}) } workingSet={ ws } columns={ COLUMNS }/>);
        expect(bodyRows(second).map(r => r.getAttribute("data-id"))).toEqual(["foo-1", "foo-2"]);
    });

    it("keeps a row marked for deletion until the merge, and drops a created one", () => {
        const ws = new WorkingSet();
        const d = doc();
        const grid = render(<DataGrid doc={ d } workingSet={ ws } columns={ COLUMNS }/>);

        act(() => {
            ws.delete(d.rows[1]);
            ws.delete(ws.create("Foo", {name: "Short-lived"}));
        });

        expect(bodyRows(grid).map(r => r.className)).toEqual([
            "qlive-grid-row", "qlive-grid-row qlive-grid-deleted"
        ]);

        act(() => ws.undo());
        expect(bodyRows(grid)[1].className).toBe("qlive-grid-row");
    });

    it("marks what other people's writes did", () => {
        const ws = new WorkingSet();
        const d = doc();
        const grid = render(<DataGrid doc={ d } workingSet={ ws } columns={ COLUMNS }/>);

        act(() => {
            ws.edit(d.rows[0]).name = "Mine";
            ws.storedState({type: "Foo", id: "foo-1", fields: {name: "Theirs"}});
            ws.storedState({type: "Foo", id: "foo-2", fields: {num: 3}});
        });

        const [first, second] = bodyRows(grid);
        expect(first.className).toBe("qlive-grid-row qlive-grid-conflict");
        expect(cellsOf(first)[0].className).toBe("qlive-conflict");
        expect(second.className).toBe("qlive-grid-row qlive-grid-remote-changed");
        expect(cellsOf(second)[1].className).toBe("qlive-remote-changed");
        expect(cellsOf(second)[1].textContent).toBe("3");

        act(() => ws.storedState({type: "Foo", id: "foo-2", deleted: true}));
        expect(bodyRows(grid)[1].className).toBe("qlive-grid-row qlive-grid-gone");
    });

    it("registers the rows of a new page and tells the working set's subscribers", () => {
        const ws = new WorkingSet();
        const d = doc();
        render(<DataGrid doc={ d } workingSet={ ws } columns={ COLUMNS }/>);

        const heard = vi.fn();
        ws.subscribe(heard);

        // a document updates in place, and the grid's drafts meet the new rows before anything registers them
        d.config = {...d.config, offset: 2};
        d.rows = [{id: "foo-3", name: "Foo #3", num: 3, ownerId: null, owner: null}];
        render(<DataGrid doc={ d } workingSet={ ws } columns={ COLUMNS }/>);

        expect(heard).toHaveBeenCalled();
        expect(ws.held().find(held => held.type === "Foo")!.ids).toContain("foo-3");

        // the same rows again are nothing new
        heard.mockClear();
        ws.register(d as any);
        expect(heard).not.toHaveBeenCalled();
    });

    it("finds a column's field on a created row that nobody set", () => {
        const ws = new WorkingSet();
        const draft = ws.create<Row>("Foo");

        expect("num" in draft).toBe(true);
        expect("nonsense" in draft).toBe(false);
        expect(resolveColumn<Row>("Foo", "owner").render(draft)).toBe("");
    });
});
