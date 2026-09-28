import type {CSSProperties, JSX} from "react";

import {matchSort} from "../FilterDSL";
import i18n from "../i18n";
import {GridColumn, ResolvedColumn, resolveColumn, rowKey} from "./columns";
import FilterInput from "./FilterInput";
import Pager from "./Pager";
import SortHeader from "./SortHeader";
import {FilterableDocument, useFilters} from "./useFilters";
import {PageableDocument} from "./usePagination";

/**
 * What DataGrid needs of a query document: its type, config, rows, row count and update(). A snapshot from
 * useInjection() or useQueryDocument() is one as long as its query selects `type`, `config`, `rows` and `rowCount`.
 */
export interface GridDocument<R> extends PageableDocument, FilterableDocument
{
    rows: readonly R[];
}

export type DataGridProps<R> = {
    /**
     * Query document snapshot to show.
     */
    doc: GridDocument<R>

    /**
     * The columns: field paths of the rows, or objects stating what differs from the defaults derived from one.
     */
    columns: readonly NoInfer<GridColumn<R>>[]

    /**
     * Component id of the grid's filters in the document's condition. Default "grid"; a view sharing the condition
     * with another owner gives each owner its own.
     */
    id?: string

    /**
     * Classes of a row's element.
     */
    rowClassName?: (row: R) => string | undefined

    /**
     * Key of the row to highlight, e.g. the one open in a detail pane. Rows are keyed as `rowKey()` keys them.
     */
    highlighted?: string | null

    /**
     * Page sizes the pager offers, see usePagination().
     */
    pageSizes?: readonly number[]

    /**
     * Added to the grid's own classes, e.g. `qlive-grid-striped`.
     */
    className?: string
}

function cellStyle(column: ResolvedColumn): CSSProperties | undefined
{
    const {minWidth, maxWidth} = column;
    return minWidth || maxWidth ? {minWidth, maxWidth} : undefined;
}

function classes(...names: (string | false | null | undefined)[]): string
{
    return names.filter(Boolean).join(" ");
}

/**
 * A table of a query document's rows, sortable by header, filtered from a filter row, and paged.
 *
 *     <DataGrid doc={ foos } columns={ ["name", "num", "owner", "created"] }/>
 *
 * Each column is derived from its field path and the schema, see resolveColumn(). The grid owns a component of the
 * document's condition, see useFilters(), so a search form writing a component of its own filters the same rows.
 *
 * Composed from resolveColumn(), useFilters(), SortHeader, FilterInput and Pager and nothing else. Where it doesn't
 * fit, copying it into the application and changing it is a reasonable answer.
 */
export default function DataGrid<R>(props: DataGridProps<R>): JSX.Element
{
    const {doc, id = "grid", rowClassName, highlighted, pageSizes, className} = props;

    const columns = props.columns.map(column => resolveColumn<R>(doc.type, column));
    const filters = useFilters(doc, id, columns.flatMap(column => column.filter ? [column.filter] : []));

    const unsorted = doc.config.sortFields.filter(
        sortField => !columns.some(column => column.sort !== null && matchSort([sortField], column.sort))
    );

    let filterIndex = 0;
    return (
        <div className={ classes("qlive-grid", className) }>
            <table className="qlive-grid-table">
                <thead>
                    <tr className="qlive-grid-headings">
                        {
                            columns.map((column, index) => column.sort !== null
                                ? <SortHeader key={ index } doc={ doc } sortKey={ column.sort }>{ column.heading }</SortHeader>
                                : <th key={ index } className="qlive-grid-heading">{ column.heading }</th>
                            )
                        }
                    </tr>
                    {
                        filters.columns.length > 0 && (
                            <tr className="qlive-grid-filters">
                                {
                                    columns.map((column, index) => (
                                        <td key={ index }>
                                            { column.filter && <FilterInput column={ filters.columns[filterIndex++] }/> }
                                        </td>
                                    ))
                                }
                            </tr>
                        )
                    }
                </thead>
                <tbody>
                    {
                        doc.rows.map(row => {
                            const key = rowKey(doc.type, row);
                            return (
                                <tr key={ key } data-id={ key }
                                    className={ classes(
                                        "qlive-grid-row",
                                        key === highlighted && "qlive-grid-highlighted",
                                        rowClassName?.(row)
                                    ) }>
                                    {
                                        columns.map((column, index) => (
                                            <td key={ index } style={ cellStyle(column) }
                                                className={ classes(
                                                    column.nowrap && "qlive-grid-nowrap",
                                                    column.className(row)
                                                ) || undefined }>
                                                { column.render(row) }
                                            </td>
                                        ))
                                    }
                                </tr>
                            );
                        })
                    }
                    {
                        doc.rows.length === 0 && (
                            <tr className="qlive-grid-empty">
                                <td colSpan={ columns.length }>{ i18n("No rows") }</td>
                            </tr>
                        )
                    }
                </tbody>
            </table>
            <div className="qlive-grid-footer">
                {
                    unsorted.length > 0 && (
                        <span className="qlive-grid-note">{ i18n("Also sorted by other fields") }</span>
                    )
                }
                {
                    filters.unclaimed.length > 0 && (
                        <span className="qlive-grid-note">{ i18n("Additional filter active") }</span>
                    )
                }
                {
                    filters.active && (
                        <button type="button" className="qlive-grid-reset" onClick={ () => filters.reset() }>
                            { i18n("Reset filters") }
                        </button>
                    )
                }
                <Pager doc={ doc } pageSizes={ pageSizes }/>
            </div>
        </div>
    );
}
