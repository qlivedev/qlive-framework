import config from "../config";
import {QueryConfig, QueryConfigDelta} from "../QueryDocument";
import {pageCount, pageIndex, pageOffset, pageSizeOptions} from "./paging";

/**
 * What usePagination() needs of a query document. A snapshot from useInjection() or useQueryDocument() is one as
 * long as its query selects `type`, `config` and `rowCount`.
 */
export interface PageableDocument
{
    type: string;
    config: QueryConfig;
    /** optional in the type because a query need not select it, but paging needs it */
    rowCount?: number;
    update(delta: QueryConfigDelta): Promise<unknown>;
}

export interface PaginationOptions
{
    /**
     * Page sizes to offer, 0 for "all rows". Default `[10, 20, 50, 100, 0]`.
     */
    pageSizes?: readonly number[];
}

/**
 * The paging state of a query document, and the ways to change it.
 */
export interface Pagination
{
    /** index of the current page, from 0 */
    page: number;

    /** number of pages, at least 1 */
    pageCount: number;

    /** page size of the document's config, 0 for all rows. What the server applied, so possibly capped. */
    pageSize: number;

    /**
     * Page sizes to offer: the options, held to the type's `maxPageSize`, plus the current page size where it isn't
     * one of them, so a control always has the current size to show.
     */
    pageSizes: number[];

    /**
     * Goes to the page with the given index, held to the pages there are. Does nothing on the current page.
     */
    goTo(page: number): Promise<void>;

    /**
     * Changes the page size, keeping the first row shown on the page shown. Does nothing on the current size.
     */
    setPageSize(pageSize: number): Promise<void>;
}

const DEFAULT_PAGE_SIZES: readonly number[] = [10, 20, 50, 100, 0];

/**
 * The paging state of a query document, for a pager -- the grid's, or one an application builds.
 *
 *     const paging = usePagination(foos)
 *
 *     <button disabled={ paging.page === 0 } onClick={ () => paging.goTo(paging.page - 1) }>Previous</button>
 *
 * Reads offset, page size and row count from the document and writes through update(), and holds nothing of its
 * own. The page size is the one in the document's config: where the type's `maxPageSize` held a request to less,
 * that is the size shown.
 *
 * @param doc       query document snapshot; its query has to select `rowCount`
 * @param options   page sizes to offer
 */
export function usePagination(doc: PageableDocument, options: PaginationOptions = {}): Pagination
{
    const {rowCount} = doc;
    if (rowCount == null)
    {
        throw new Error(
            "usePagination() needs the document's rowCount to count pages. Add rowCount to the selection of the " +
            "query the " + doc.type + " document comes from."
        );
    }

    const {offset, pageSize} = doc.config;
    const page = pageIndex(offset, pageSize);
    const count = pageCount(rowCount, pageSize);

    const maxPageSize = config().meta.types[doc.type]?.meta?.maxPageSize;
    const pageSizes = pageSizeOptions(options.pageSizes ?? DEFAULT_PAGE_SIZES, maxPageSize);
    if (!pageSizes.includes(pageSize))
    {
        insertPageSize(pageSizes, pageSize);
    }

    const goTo = (target: number) => {
        const clamped = Math.max(0, Math.min(target, count - 1));
        if (clamped === page)
        {
            return Promise.resolve();
        }
        return doc.update({offset: pageOffset(clamped, pageSize)}).then(() => {});
    };

    const setPageSize = (size: number) => {
        if (size === pageSize)
        {
            return Promise.resolve();
        }
        return doc.update({pageSize: size, offset: pageOffset(pageIndex(offset, size), size)}).then(() => {});
    };

    return {page, pageCount: count, pageSize, pageSizes, goTo, setPageSize};
}

/**
 * Puts a page size into a list of options in ascending order, ahead of "all rows" (0), which counts as the largest.
 */
function insertPageSize(pageSizes: number[], pageSize: number)
{
    const larger = (size: number) => pageSize === 0 ? false : size === 0 || size > pageSize;
    const index = pageSizes.findIndex(larger);
    pageSizes.splice(index < 0 ? pageSizes.length : index, 0, pageSize);
}
