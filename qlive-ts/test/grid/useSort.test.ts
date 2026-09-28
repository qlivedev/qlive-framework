import {describe, expect, test, vi} from "vitest";
import {field, FieldExpression} from "../../src/FilterDSL";
import {useSort} from "../../src/grid/useSort";

function sortedBy(...sortFields: FieldExpression[])
{
    return {
        config: {condition: null, offset: 40, pageSize: 20, sortFields},
        update: vi.fn(() => Promise.resolve({}))
    };
}

describe("useSort", () => {

    test("shows a key the order doesn't name as unsorted", () => {
        const sort = useSort(sortedBy("num"), "name");

        expect(sort.direction).toBeNull();
        expect(sort.position).toBeNull();
    });

    test("shows direction and position of a key in the order", () => {
        const doc = sortedBy("num", "!name");

        expect(useSort(doc, "num")).toMatchObject({direction: "asc", position: 0});
        expect(useSort(doc, "name")).toMatchObject({direction: "desc", position: 1});
    });

    test("toggles to ascending, then descending, on the first page", async () => {
        const doc = sortedBy("num", "name");

        await useSort(doc, "name").toggle();
        expect(doc.update).toHaveBeenLastCalledWith({sortFields: ["name"], offset: 0});

        const sorted = sortedBy("name");
        await useSort(sorted, "name").toggle();
        expect(sorted.update).toHaveBeenLastCalledWith({sortFields: ["!name"], offset: 0});
    });

    test("sorts by an expression key", async () => {
        const key = field("numa").plus(field("numb"));
        const doc = sortedBy(field("numa").plus(field("numb")).desc());

        expect(useSort(doc, key).direction).toBe("desc");

        await useSort(doc, key).toggle();
        expect(doc.update).toHaveBeenLastCalledWith({sortFields: [key], offset: 0});
    });
});
