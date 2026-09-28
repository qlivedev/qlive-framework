// @vitest-environment jsdom
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import {init} from "../../src/config";
import Pager from "../../src/grid/Pager";
import {testAuthentication, testConfig, testCsrfToken} from "../fixtures/testConfig";

function page(offset: number, pageSize: number, rowCount: number)
{
    return {
        type: "Foo",
        config: {condition: null, offset, pageSize, sortFields: []},
        rowCount,
        update: vi.fn(() => Promise.resolve({}))
    };
}

let container: HTMLElement;
let root: Root;

function render(element: React.ReactNode)
{
    act(() => root.render(element));
}

function button(className: string)
{
    return container.querySelector<HTMLButtonElement>("." + className)!;
}

beforeAll(async () => {
    await init({config: testConfig, csrfToken: testCsrfToken(), authentication: testAuthentication(), data: {}});
});

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

describe("Pager", () => {

    it("lists the pages around the current one and marks it", () => {
        render(<Pager doc={ page(100, 20, 500) }/>);

        const pages = Array.from(container.querySelectorAll(".qlive-grid-pager-page"));
        expect(pages.map(b => b.textContent)).toEqual(["4", "5", "6", "7", "8"]);
        expect(container.querySelector("[aria-current=page]")!.textContent).toBe("6");
    });

    it("disables what leads nowhere", () => {
        render(<Pager doc={ page(0, 20, 30) }/>);
        expect(button("qlive-grid-pager-first").disabled).toBe(true);
        expect(button("qlive-grid-pager-previous").disabled).toBe(true);
        expect(button("qlive-grid-pager-next").disabled).toBe(false);

        render(<Pager doc={ page(20, 20, 30) }/>);
        expect(button("qlive-grid-pager-next").disabled).toBe(true);
        expect(button("qlive-grid-pager-last").disabled).toBe(true);
    });

    it("moves through the document", () => {
        const doc = page(100, 20, 500);
        render(<Pager doc={ doc }/>);

        act(() => button("qlive-grid-pager-next").click());
        expect(doc.update).toHaveBeenLastCalledWith({offset: 120});

        act(() => button("qlive-grid-pager-last").click());
        expect(doc.update).toHaveBeenLastCalledWith({offset: 480});
    });

    it("changes the page size", () => {
        const doc = page(100, 20, 500);
        render(<Pager doc={ doc } pageSizes={ [20, 50] }/>);

        const select = container.querySelector("select")!;
        expect(select.value).toBe("20");

        act(() => {
            select.value = "50";
            select.dispatchEvent(new Event("change", {bubbles: true}));
        });
        expect(doc.update).toHaveBeenLastCalledWith({pageSize: 50, offset: 100});
    });

    it("aligns by class", () => {
        render(<Pager doc={ page(0, 20, 30) } align="end" className="mine"/>);
        expect(container.querySelector("nav")!.className).toBe("qlive-grid-pager qlive-grid-pager-end mine");
    });
});
