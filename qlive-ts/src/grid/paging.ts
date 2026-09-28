/*
 * Page math over a query config: offset, page size and row count in, pages out.
 *
 * A page size of 0 means "all rows", the same as in the config, and makes everything one page. Pages are counted
 * from 0, like offsets; a pager showing "page 1" adds the 1 itself.
 */

/**
 * Number of pages the rows take up. At least 1, so an empty result is one empty page and not none.
 *
 * @param rowCount  total number of rows, as in a document's `rowCount`
 * @param pageSize  rows per page, 0 for all
 */
export function pageCount(rowCount: number, pageSize: number): number
{
    if (pageSize <= 0 || rowCount <= 0)
    {
        return 1;
    }
    return Math.ceil(rowCount / pageSize);
}

/**
 * Index of the page that starts at or contains the given offset.
 *
 * @param offset    offset in rows, as in a query config
 * @param pageSize  rows per page, 0 for all
 */
export function pageIndex(offset: number, pageSize: number): number
{
    if (pageSize <= 0)
    {
        return 0;
    }
    return Math.floor(offset / pageSize);
}

/**
 * Offset of the page with the given index.
 *
 * @param page      page index, from 0
 * @param pageSize  rows per page, 0 for all
 */
export function pageOffset(page: number, pageSize: number): number
{
    if (pageSize <= 0)
    {
        return 0;
    }
    return Math.max(0, page) * pageSize;
}

/**
 * The page sizes a control can offer given the type's `maxPageSize` from `config().meta`.
 *
 * Without a limit the options come back as they are. With one, sizes above it are dropped and so is 0 ("all rows"),
 * which the server would cap too, and the limit itself is added last in their place. What is left keeps the order it
 * was given in.
 *
 * @param options       page sizes to offer, 0 for "all rows"
 * @param maxPageSize   the type's largest page, absent for no limit
 */
export function pageSizeOptions(options: readonly number[], maxPageSize?: number): number[]
{
    if (maxPageSize == null)
    {
        return options.slice();
    }

    const allowed = options.filter(size => size > 0 && size <= maxPageSize);
    if (allowed.length < options.length && !allowed.includes(maxPageSize))
    {
        allowed.push(maxPageSize);
    }
    return allowed;
}

/**
 * The page indexes a pager lists as numbers: the current page and up to `radius` pages on either side of it. Near
 * either end the window shifts so it still holds `2 * radius + 1` pages where there are that many.
 *
 * @param page       current page index, from 0
 * @param pageCount  number of pages, as from `pageCount()`
 * @param radius     pages to list on either side of the current one
 */
export function pageWindow(page: number, pageCount: number, radius: number = 2): number[]
{
    const size = Math.min(pageCount, 2 * radius + 1);
    const first = Math.max(0, Math.min(page - radius, pageCount - size));

    const pages: number[] = [];
    for (let i = 0; i < size; i++)
    {
        pages.push(first + i);
    }
    return pages;
}
