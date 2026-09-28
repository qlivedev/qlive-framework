// @vitest-environment jsdom
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {act, useState} from "react";
import {createRoot, Root} from "react-dom/client";
import {field, value} from "../src/FilterDSL";
import DataGrid from "../src/grid/DataGrid";
import {localDocument, useLocalDocument} from "../src/localDocument";
import {initGridConfig} from "./fixtures/gridConfig";

type Row = { id: string, name: string, num: number };

const ROWS: Row[] = [
    {id: "foo-1", name: "Charlie", num: 3},
    {id: "foo-2", name: "alpha", num: 1},
    {id: "foo-3", name: "Bravo", num: 2}
];

beforeAll(initGridConfig);

describe("localDocument", () => {

    it("answers update() from its rows", async () => {
        const doc = localDocument("Foo", ROWS, {pageSize: 2});
        expect(doc.rows.map(row => row.id)).toEqual(["foo-1", "foo-2"]);
        expect(doc.rowCount).toBe(3);

        const snapshot = await doc.update({sortFields: ["name"], offset: 2});
        expect(snapshot.rows.map(row => row.id)).toEqual(["foo-1"]);
        expect(snapshot.config).toEqual({condition: null, offset: 2, pageSize: 2, sortFields: ["name"]});

        await doc.update({condition: field("num").ge(value(2)), offset: 0});
        expect(doc.rows.map(row => row.id)).toEqual(["foo-3", "foo-1"]);
        expect(doc.rowCount).toBe(2);
    });

    it("keeps its rows and config when a condition can't be evaluated, and says why", async () => {
        const doc = localDocument("Foo", ROWS);

        await expect(doc.update({condition: field("nope").eq(value(1))})).rejects.toThrow(/Foo has no field "nope"/);
        expect(doc.error!.message).toMatch(/Foo has no field "nope"/);
        expect(doc.rows).toHaveLength(3);
        expect(doc.config.condition).toBeNull();

        await doc.update({});
        expect(doc.error).toBeNull();
    });
});

describe("useLocalDocument", () => {

    let container: HTMLElement;
    let root: Root;

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

    let setRows: (rows: Row[]) => void;

    function View()
    {
        const [rows, set] = useState(ROWS);
        setRows = set;
        const doc = useLocalDocument("Foo", rows, {pageSize: 10});
        return <DataGrid doc={ doc } columns={ ["name", "num"] }/>;
    }

    const names = () => Array.from(container.querySelectorAll("tbody tr td:first-child"), td => td.textContent);

    it("sorts, filters and takes new rows in a DataGrid", async () => {
        act(() => root.render(<View/>));
        expect(names()).toEqual(["Charlie", "alpha", "Bravo"]);

        await act(async () => (container.querySelector(".qlive-grid-headings button") as HTMLElement).click());
        expect(names()).toEqual(["alpha", "Bravo", "Charlie"]);

        act(() => setRows([...ROWS, {id: "foo-4", name: "Delta", num: 4}]));
        expect(names()).toEqual(["alpha", "Bravo", "Charlie", "Delta"]);

        vi.useFakeTimers();
        try
        {
            const input = container.querySelectorAll(".qlive-grid-filters input")[1] as HTMLInputElement;
            const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!;
            act(() => {
                setter.call(input, "4");
                input.dispatchEvent(new Event("input", {bubbles: true}));
            });
            await act(async () => { vi.advanceTimersByTime(300); });
        }
        finally
        {
            vi.useRealTimers();
        }
        expect(names()).toEqual(["Delta"]);
    });
});
