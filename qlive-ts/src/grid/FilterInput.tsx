import type {JSX} from "react";

import i18n from "../i18n";
import {ColumnFilterState} from "./useFilters";

export type FilterInputProps = {
    /**
     * The column's filter state, one of `useFilters().columns`.
     */
    column: ColumnFilterState

    /**
     * Added to the wrapper's own classes.
     */
    className?: string
}

/**
 * The inputs of one column's filter: the filter's own `Input` component where it has one, otherwise one text input
 * per value.
 *
 * Built on useFilters() and nothing else, so an input that has to look different is written the same way.
 */
export default function FilterInput({column, className}: FilterInputProps): JSX.Element
{
    const {field, filter, values, setValues, active} = column
    const {Input, arity} = filter

    const classes = "qlive-grid-filter" + (active ? " qlive-grid-filter-active" : "") + (className ? " " + className : "")

    return (
        <div className={ classes }>
            {
                Input
                    ? <Input field={ field } arity={ arity } values={ values } setValues={ setValues }/>
                    : values.map((value, index) => (
                        <input key={ index } type="text" className="qlive-grid-filter-input"
                               aria-label={ i18n("Filter {0}", field) + (arity > 1 ? " " + (index + 1) : "") }
                               value={ value == null ? "" : String(value) }
                               onChange={ ev => {
                                   const next = values.slice()
                                   next[index] = ev.target.value === "" ? null : ev.target.value
                                   setValues(next)
                               } }/>
                    ))
            }
        </div>
    )
}
