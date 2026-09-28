// @vitest-environment jsdom
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import {ColumnFilter, ColumnFilterInputProps, operatorFilter} from "../../src/grid/filters";
import FilterInput from "../../src/grid/FilterInput";
import {ColumnFilterState} from "../../src/grid/useFilters";

function column(filter: ColumnFilter<any>, values: unknown[], active: boolean = false): ColumnFilterState
{
    return {field: "name", filter, values, setValues: vi.fn(), active};
}

let container: HTMLElement;
let root: Root;

function render(element: React.ReactNode)
{
    act(() => root.render(element));
    return container.firstElementChild as HTMLElement;
}

/** sets an input's value the way React notices, through the native setter, and fires input */
function typeInto(input: HTMLInputElement, text: string)
{
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!;
    act(() => {
        setter.call(input, text);
        input.dispatchEvent(new Event("input", {bubbles: true}));
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

describe("FilterInput", () => {

    it("renders one text input per value", () => {
        const div = render(<FilterInput column={ column(operatorFilter("between", "Int"), ["1", null]) }/>);

        const inputs = Array.from(div.querySelectorAll("input"));
        expect(inputs.map(i => i.value)).toEqual(["1", ""]);
        expect(div.className).toBe("qlive-grid-filter");
    });

    it("marks an active filter", () => {
        const div = render(<FilterInput column={ column(operatorFilter("eq"), ["x"], true) } className="mine"/>);
        expect(div.className).toBe("qlive-grid-filter qlive-grid-filter-active mine");
    });

    it("sets the values, an emptied input as null", () => {
        const state = column(operatorFilter("between", "Int"), ["1", "5"]);
        const div = render(<FilterInput column={ state }/>);
        const [from, to] = Array.from(div.querySelectorAll("input"));

        typeInto(to, "7");
        expect(state.setValues).toHaveBeenLastCalledWith(["1", "7"]);

        typeInto(from, "");
        expect(state.setValues).toHaveBeenLastCalledWith([null, "5"]);
    });

    it("uses the filter's own input", () => {
        const Input = ({field, values}: ColumnFilterInputProps) => <span>{ field }={ String(values[0]) }</span>;
        const div = render(<FilterInput column={ column({...operatorFilter("eq"), Input}, ["x"]) }/>);
        expect(div.textContent).toBe("name=x");
    });
});
