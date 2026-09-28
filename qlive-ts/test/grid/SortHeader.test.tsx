// @vitest-environment jsdom
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import {FieldExpression} from "../../src/FilterDSL";
import SortHeader from "../../src/grid/SortHeader";

function sortedBy(...sortFields: FieldExpression[])
{
    return {
        config: {condition: null, offset: 40, pageSize: 20, sortFields},
        update: vi.fn(() => Promise.resolve({}))
    };
}

let container: HTMLElement;
let root: Root;

function render(element: React.ReactNode)
{
    act(() => root.render(<table><thead><tr>{ element }</tr></thead></table>));
    return container.querySelector("th")!;
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

describe("SortHeader", () => {

    it("shows an unsorted key plainly", () => {
        const th = render(<SortHeader doc={ sortedBy("num") } sortKey="name">Name</SortHeader>);

        expect(th.className).toBe("qlive-grid-sort-header");
        expect(th.hasAttribute("aria-sort")).toBe(false);
        expect(th.textContent).toBe("Name");
    });

    it("shows the direction of the sole sort field", () => {
        const th = render(<SortHeader doc={ sortedBy("!name") } sortKey="name">Name</SortHeader>);

        expect(th.className).toBe("qlive-grid-sort-header qlive-grid-sorted qlive-grid-sorted-desc");
        expect(th.getAttribute("aria-sort")).toBe("descending");
        expect(th.textContent).toBe("Name▼");
    });

    it("numbers the fields of a longer order, aria-sort only on the first", () => {
        const doc = sortedBy("num", "name");

        const first = render(<SortHeader doc={ doc } sortKey="num">Num</SortHeader>);
        expect(first.textContent).toBe("Num▲1");
        expect(first.getAttribute("aria-sort")).toBe("ascending");

        const second = render(<SortHeader doc={ doc } sortKey="name">Name</SortHeader>);
        expect(second.textContent).toBe("Name▲2");
        expect(second.hasAttribute("aria-sort")).toBe(false);
    });

    it("toggles on click", () => {
        const doc = sortedBy("name");
        const th = render(<SortHeader doc={ doc } sortKey="name">Name</SortHeader>);

        act(() => th.querySelector("button")!.click());
        expect(doc.update).toHaveBeenLastCalledWith({sortFields: ["!name"], offset: 0});
    });
});
