import {describe, expect, test} from "vitest";
import {pageCount, pageIndex, pageOffset, pageSizeOptions} from "../../src/grid/paging";

describe("page math", () => {

    test("counts pages, at least one", () => {
        expect(pageCount(0, 10)).toBe(1);
        expect(pageCount(10, 10)).toBe(1);
        expect(pageCount(11, 10)).toBe(2);
        expect(pageCount(95, 0)).toBe(1);
    });

    test("finds the page of an offset and the offset of a page", () => {
        expect(pageIndex(0, 10)).toBe(0);
        expect(pageIndex(25, 10)).toBe(2);
        expect(pageIndex(25, 0)).toBe(0);
        expect(pageOffset(2, 10)).toBe(20);
        expect(pageOffset(-1, 10)).toBe(0);
        expect(pageOffset(3, 0)).toBe(0);
    });

    test("offers page sizes within the type's limit", () => {
        const options = [10, 25, 50, 100, 0];
        expect(pageSizeOptions(options)).toEqual(options);
        expect(pageSizeOptions(options, 1000)).toEqual([10, 25, 50, 100, 1000]);
        expect(pageSizeOptions(options, 50)).toEqual([10, 25, 50]);
        expect(pageSizeOptions(options, 40)).toEqual([10, 25, 40]);
        expect(pageSizeOptions([10, 25], 100)).toEqual([10, 25]);
    });
});
