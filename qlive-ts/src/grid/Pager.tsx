import * as React from "react";

import i18n from "../i18n";
import {pageWindow} from "./paging";
import {PageableDocument, PaginationOptions, usePagination} from "./usePagination";

export type PagerProps = PaginationOptions & {
    /**
     * Query document snapshot to page. Its query has to select `type`, `config` and `rowCount`.
     */
    doc: PageableDocument

    /**
     * Page numbers to list on either side of the current one. Default 2.
     */
    radius?: number

    /**
     * Where the pager sits in its row. Default "start".
     */
    align?: "start" | "center" | "end"

    /**
     * Added to the pager's own classes.
     */
    className?: string
}

/**
 * First, previous, nearby page numbers, next and last page, and a page size select, for any query document.
 *
 * Built on usePagination() and nothing else, so a pager that has to look different is written the same way.
 */
export default function Pager({doc, pageSizes, radius = 2, align = "start", className}: PagerProps)
{
    const paging = usePagination(doc, {pageSizes})
    const {page, pageCount} = paging
    const last = pageCount - 1

    const classes = "qlive-grid-pager qlive-grid-pager-" + align + (className ? " " + className : "")

    return (
        <nav className={ classes } aria-label={ i18n("Pagination") }>
            <button type="button" className="qlive-grid-pager-first" disabled={ page <= 0 }
                    aria-label={ i18n("First page") } onClick={ () => paging.goTo(0) }>
                «
            </button>
            <button type="button" className="qlive-grid-pager-previous" disabled={ page <= 0 }
                    aria-label={ i18n("Previous page") } onClick={ () => paging.goTo(page - 1) }>
                ‹
            </button>
            {
                pageWindow(page, pageCount, radius).map(n => (
                    <button key={ n } type="button" className="qlive-grid-pager-page"
                            aria-label={ i18n("Page {0}", String(n + 1)) }
                            aria-current={ n === page ? "page" : undefined }
                            onClick={ () => paging.goTo(n) }>
                        { n + 1 }
                    </button>
                ))
            }
            <button type="button" className="qlive-grid-pager-next" disabled={ page >= last }
                    aria-label={ i18n("Next page") } onClick={ () => paging.goTo(page + 1) }>
                ›
            </button>
            <button type="button" className="qlive-grid-pager-last" disabled={ page >= last }
                    aria-label={ i18n("Last page") } onClick={ () => paging.goTo(last) }>
                »
            </button>
            <label className="qlive-grid-pager-size">
                { i18n("Rows per page") }
                <select value={ paging.pageSize } onChange={ ev => paging.setPageSize(Number(ev.target.value)) }>
                    {
                        paging.pageSizes.map(size => (
                            <option key={ size } value={ size }>
                                { size === 0 ? i18n("All") : size }
                            </option>
                        ))
                    }
                </select>
            </label>
        </nav>
    )
}
