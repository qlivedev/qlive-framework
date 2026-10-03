import {act, type ComponentType} from "react";
import {createRoot, type Root} from "react-dom/client";
import {afterAll, beforeAll, describe, expect, it} from "vitest";
import {FixtureScope, initFixture, type QLiveFixture} from "@qlivedev/qlive-ts";
import sortingFixture from "../../fixtures/grid/Sorting.json";

// tells React that this is a test, which makes act() wait for the updates it wraps
(globalThis as {IS_REACT_ACT_ENVIRONMENT?: boolean}).IS_REACT_ACT_ENVIRONMENT = true;

const fixture = sortingFixture as unknown as QLiveFixture;

let Sorting: ComponentType;
let container: HTMLElement;
let root: Root;

beforeAll(async () => {
    // QLive first, then the view: Sorting.tsx calls i18n() on import, which needs the config. A static import would
    // be hoisted above this.
    await initFixture(fixture);
    Sorting = (await import("../../../src/app/grid/Sorting")).default;

    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    await act(async () => root.render(
        <FixtureScope fixture={ fixture }>
            <Sorting/>
        </FixtureScope>
    ));
});

afterAll(() => {
    act(() => root.unmount());
    container.remove();
});

/** the names in the grid, top to bottom */
function names(): string[]
{
    return [...container.querySelectorAll(".qlive-grid-table tbody tr")].map(row => row.querySelector("td")!.textContent!);
}

/** clicks the given element and waits for the document update it starts */
async function click(element: Element | null): Promise<void>
{
    expect(element).not.toBeNull();
    await act(async () => (element as HTMLElement).click());
}

describe("grid/Sorting on a fixture", () => {

    // the rows as recorded, read from the JSON rather than the fixture, whose data is untyped
    const {rows} = sortingFixture.data["grid/sorting/Q_FooList"].data.queryFooDocument;

    it("opens on the first page of the recorded rows", () => {
        expect(names()).toEqual(rows.slice(0, 5).map(row => row.name));
    });

    it("sorts all the recorded rows, not just the page on screen", async () => {
        await click(container.querySelector(".qlive-grid-sort-header button"));

        // "Foo #10" before "Foo #2", as the database sorts text
        expect(names()).toEqual(rows.map(row => row.name).sort().slice(0, 5));
    });

    it("pages through them", async () => {
        const before = names();
        await click(container.querySelector(".qlive-grid-pager-next"));

        expect(names()).toHaveLength(5);
        expect(names()).not.toEqual(before);
    });
});
