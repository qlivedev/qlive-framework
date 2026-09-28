import type {JSX, ReactNode} from "react";

import {FieldExpression} from "../FilterDSL";
import {SortableDocument, useSort} from "./useSort";

export type SortHeaderProps = {
    /**
     * Query document snapshot to sort. Its query has to select `config`.
     */
    doc: SortableDocument

    /**
     * What the column sorts by: a field path or a FilterDSL expression node, without a direction.
     */
    sortKey: FieldExpression

    /**
     * The heading.
     */
    children?: ReactNode

    /**
     * Added to the header cell's own classes.
     */
    className?: string
}

const ARROWS = {asc: "▲", desc: "▼"}

/**
 * A table header cell that sorts the document by its key when clicked, and shows where the key stands in the
 * document's sort order: its direction, and its position where the order has more than one field.
 *
 * Built on useSort() and nothing else, so a header that has to look different, or isn't a table cell, is written
 * the same way.
 */
export default function SortHeader({doc, sortKey, children, className}: SortHeaderProps): JSX.Element
{
    const {direction, position, toggle} = useSort(doc, sortKey)

    const classes = "qlive-grid-sort-header"
        + (direction ? " qlive-grid-sorted qlive-grid-sorted-" + direction : "")
        + (className ? " " + className : "")

    // aria-sort belongs on one header at a time, the one the rows are ordered by first
    const ariaSort = position === 0 ? (direction === "asc" ? "ascending" : "descending") : undefined
    const number = position !== null && doc.config.sortFields.length > 1 ? position + 1 : null

    return (
        <th className={ classes } aria-sort={ ariaSort }>
            <button type="button" onClick={ toggle }>
                { children }
                <span className="qlive-grid-sort-indicator" aria-hidden="true">
                    { direction && ARROWS[direction] }
                    { number && <sup>{ number }</sup> }
                </span>
            </button>
        </th>
    )
}
