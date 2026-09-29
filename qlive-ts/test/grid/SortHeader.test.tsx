// @vitest-environment jsdom
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import {field, FieldExpression, value} from "../../src/FilterDSL";
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

    it("marks a key that is part of an expression sorted by", () => {
        const th = render(
            <SortHeader doc={ sortedBy(field("num").mod(value(10)).desc()) } sortKey="num">Num</SortHeader>
        );

        expect(th.className).toBe(
            "qlive-grid-sort-header qlive-grid-sorted qlive-grid-sorted-desc qlive-grid-sorted-partial"
        );
        expect(th.hasAttribute("aria-sort")).toBe(false);
        expect(th.textContent).toBe("Num▼");
    });

    it("gives the keys of one expression the same number", () => {
        const doc = sortedBy("name", field("num").add(field("flag")));

        expect(render(<SortHeader doc={ doc } sortKey="num">Num</SortHeader>).textContent).toBe("Num▲2");
        expect(render(<SortHeader doc={ doc } sortKey="flag">Flag</SortHeader>).textContent).toBe("Flag▲2");
        expect(render(<SortHeader doc={ doc } sortKey="name">Name</SortHeader>).className)
            .not.toContain("qlive-grid-sorted-partial");
    });

    it("prefers the key itself to an expression containing it", () => {
        const th = render(
            <SortHeader doc={ sortedBy(field("num").mod(value(10)), "!num") } sortKey="num">Num</SortHeader>
        );

        expect(th.className).not.toContain("qlive-grid-sorted-partial");
        expect(th.textContent).toBe("Num▼2");
    });

    it("puts its title on the header cell", () => {
        const th = render(<SortHeader doc={ sortedBy("name") } sortKey="name" title="name, ascending">Name</SortHeader>);
        expect(th.title).toBe("name, ascending");
        expect(render(<SortHeader doc={ sortedBy("name") } sortKey="name">Name</SortHeader>).hasAttribute("title"))
            .toBe(false);
    });

    it("toggles on click", () => {
        const doc = sortedBy("name");
        const th = render(<SortHeader doc={ doc } sortKey="name">Name</SortHeader>);

        act(() => th.querySelector("button")!.click());
        expect(doc.update).toHaveBeenLastCalledWith({sortFields: ["!name"], offset: 0});
    });
});
