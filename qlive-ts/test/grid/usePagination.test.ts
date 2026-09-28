import {beforeAll, describe, expect, test, vi} from "vitest";
import {init} from "../../src/config";
import {usePagination} from "../../src/grid/usePagination";
import {testAuthentication, testConfig, testCsrfToken} from "../fixtures/testConfig";

function page(offset: number, pageSize: number, rowCount: number = 95, type: string = "Foo")
{
    return {
        type,
        config: {condition: null, offset, pageSize, sortFields: []},
        rowCount,
        update: vi.fn(() => Promise.resolve({}))
    };
}

beforeAll(async () => {
    await init({
        config: {
            ...testConfig,
            meta: {...testConfig.meta, types: {Capped: {meta: {maxPageSize: 50}}}}
        },
        csrfToken: testCsrfToken(),
        authentication: testAuthentication(),
        data: {}
    });
});

describe("usePagination", () => {

    test("reads page, page count and page size from the document", () => {
        const paging = usePagination(page(40, 20));

        expect(paging).toMatchObject({page: 2, pageCount: 5, pageSize: 20});
        expect(paging.pageSizes).toEqual([10, 20, 50, 100, 0]);
    });

    test("holds the offered sizes to the type's maxPageSize", () => {
        expect(usePagination(page(0, 20, 95, "Capped")).pageSizes).toEqual([10, 20, 50]);
        expect(usePagination(page(0, 10, 95, "Capped"), {pageSizes: [10, 25, 0]}).pageSizes).toEqual([10, 25, 50]);
    });

    test("offers the current page size where the options don't have it", () => {
        expect(usePagination(page(0, 25)).pageSizes).toEqual([10, 20, 25, 50, 100, 0]);
        expect(usePagination(page(0, 500)).pageSizes).toEqual([10, 20, 50, 100, 500, 0]);
        expect(usePagination(page(0, 0), {pageSizes: [10, 20]}).pageSizes).toEqual([10, 20, 0]);
    });

    test("goes to a page, held to the pages there are", async () => {
        const doc = page(40, 20);
        const paging = usePagination(doc);

        await paging.goTo(4);
        expect(doc.update).toHaveBeenLastCalledWith({offset: 80});

        await paging.goTo(12);
        expect(doc.update).toHaveBeenLastCalledWith({offset: 80});

        await paging.goTo(-1);
        expect(doc.update).toHaveBeenLastCalledWith({offset: 0});
    });

    test("does nothing for the current page and size", async () => {
        const doc = page(40, 20);
        const paging = usePagination(doc);

        await paging.goTo(2);
        await paging.setPageSize(20);
        expect(doc.update).not.toHaveBeenCalled();
    });

    test("keeps the first row shown when the page size changes", async () => {
        const doc = page(40, 20);
        const paging = usePagination(doc);

        await paging.setPageSize(50);
        expect(doc.update).toHaveBeenLastCalledWith({pageSize: 50, offset: 0});

        await paging.setPageSize(10);
        expect(doc.update).toHaveBeenLastCalledWith({pageSize: 10, offset: 40});

        await paging.setPageSize(0);
        expect(doc.update).toHaveBeenLastCalledWith({pageSize: 0, offset: 0});
    });

    test("names the missing rowCount", () => {
        expect(() => usePagination({...page(0, 20), rowCount: undefined})).toThrow(/rowCount.*Foo/);
    });
});
