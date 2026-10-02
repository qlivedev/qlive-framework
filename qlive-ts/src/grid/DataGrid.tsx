import type {CSSProperties, JSX} from "react";

import {matchSort, matchSortPart} from "../FilterDSL";
import i18n from "../i18n";
import {WorkingSet} from "../merge/WorkingSet";
import {GridColumn, ResolvedColumn, resolveColumn, rowKey} from "./columns";
import FilterInput from "./FilterInput";
import Pager from "./Pager";
import SortHeader from "./SortHeader";
import {unawaited} from "./unawaited";
import {FilterableDocument, useFilters} from "./useFilters";
import {GridRowStatus, useGridRows} from "./useGridRows";
import {PageableDocument} from "./usePagination";

/**
 * What DataGrid needs of a query document: its type, config, rows, row count and update(), and its error if it has
 * one. A snapshot from useInjection() or useQueryDocument() is one as long as its query selects `type`, `config`,
 * `rows` and `rowCount`.
 */
export interface GridDocument<R> extends PageableDocument, FilterableDocument
{
    rows: readonly R[];

    /**
     * Why the last update() failed, see QueryDocument#error. The grid shows it above the rows, which are still the
     * ones from before.
     */
    error?: Error | null;
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
     * Working set the rows are edited in. The grid then shows the drafts, the rows created in the working set on the
     * first page, and what has happened to each row and cell, see useGridRows().
     */
    workingSet?: WorkingSet | null

    /**
     * Whether other people's writes show as they happen, see useGridRows(). Without a working set, the grid offers
     * to reload once a row it shows changed.
     */
    watch?: boolean

    /**
     * Classes of a row's element.
     */
    rowClassName?: (row: R) => string | undefined

    /**
     * Tooltip of a column's header cell, e.g. a description of the sort field its arrow stands for. What that says
     * and how it's put is the application's; `matchSort()` and `matchSortPart()` find the sort field. A column with a
     * `title` of its own keeps that.
     */
    headerTitle?: (column: ResolvedColumn) => string | undefined

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

const STATUS_CLASSES: Record<GridRowStatus, string | null> = {
    gone: "qlive-grid-gone",
    deleted: "qlive-grid-deleted",
    conflict: "qlive-grid-conflict",
    new: "qlive-grid-new",
    changed: "qlive-grid-changed",
    remoteChanged: "qlive-grid-remote-changed",
    unchanged: null
};

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
 * Each column is derived from its field path and the schema, see resolveColumn(). Given a working set, the grid shows
 * its drafts and marks rows and cells by what happened to them, see useGridRows(). The grid owns a component of the
 * document's condition, see useFilters(), so a search form writing a component of its own filters the same rows.
 * When an update() fails, the grid's own or anyone else's, it says so above the rows, which stay the ones from before.
 *
 * Composed from resolveColumn(), useFilters(), useGridRows(), SortHeader, FilterInput and Pager and nothing else. Where it doesn't
 * fit, copying it into the application and changing it is a reasonable answer.
 */
export default function DataGrid<R>(props: DataGridProps<R>): JSX.Element
{
    const {doc, id = "grid", workingSet, watch, rowClassName, headerTitle, highlighted, pageSizes, className} = props;

    const columns = props.columns.map(column => resolveColumn<R>(doc.type, column));
    const filters = useFilters(doc, id, columns.flatMap(column => column.filter ? [column.filter] : []));
    const {rows, status, fieldClass, stale} = useGridRows(doc, {workingSet, watch});

    const unsorted = doc.config.sortFields.filter(
        sortField => !columns.some(column => column.sort !== null &&
            (matchSort([sortField], column.sort) !== null || matchSortPart([sortField], column.sort) !== null)
        )
    );

    const title = (column: ResolvedColumn) => column.title ?? headerTitle?.(column);

    let filterIndex = 0;
    return (
        <div className={ classes("qlive-grid", className) }>
            {
                doc.error && (
                    <p className="qlive-grid-error" role="alert">
                        {
                            doc.error.message
                                ? i18n("Rows not updated: {0}", doc.error.message)
                                : i18n("Rows not updated")
                        }
                    </p>
                )
            }
            <table className="qlive-grid-table">
                <thead>
                    <tr className="qlive-grid-headings">
                        {
                            columns.map((column, index) => column.sort !== null
                                ? (
                                    <SortHeader key={ index } doc={ doc } sortKey={ column.sort } title={ title(column) }>
                                        { column.heading }
                                    </SortHeader>
                                )
                                : <th key={ index } className="qlive-grid-heading" title={ title(column) }>{ column.heading }</th>
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
                        rows.map(row => {
                            const key = rowKey(doc.type, row);
                            return (
                                <tr key={ key } data-id={ key }
                                    className={ classes(
                                        "qlive-grid-row",
                                        STATUS_CLASSES[status(row)],
                                        key === highlighted && "qlive-grid-highlighted",
                                        rowClassName?.(row)
                                    ) }>
                                    {
                                        columns.map((column, index) => (
                                            <td key={ index } style={ cellStyle(column) }
                                                className={ classes(
                                                    column.nowrap && "qlive-grid-nowrap",
                                                    column.statusField && fieldClass(row, column.statusField),
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
                        rows.length === 0 && (
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
                    stale && (
                        <>
                            <span className="qlive-grid-note">{ i18n("Rows changed elsewhere") }</span>
                            <button type="button" className="qlive-grid-reload" onClick={ () => unawaited(doc.update({})) }>
                                { i18n("Reload") }
                            </button>
                        </>
                    )
                }
                {
                    filters.active && (
                        <button type="button" className="qlive-grid-reset" onClick={ () => unawaited(filters.reset()) }>
                            { i18n("Reset filters") }
                        </button>
                    )
                }
                <Pager doc={ doc } pageSizes={ pageSizes }/>
            </div>
        </div>
    );
}
