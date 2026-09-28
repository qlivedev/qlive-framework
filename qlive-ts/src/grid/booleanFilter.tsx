import type {JSX} from "react";

import i18n from "../i18n";
import {ColumnFilter, ColumnFilterInputProps, operatorFilter} from "./filters";

/**
 * Three-way select for a Boolean column: any, true, false.
 */
function BooleanSelect({field, values, setValues}: ColumnFilterInputProps<string[]>): JSX.Element
{
    return (
        <select className="qlive-grid-filter-input" aria-label={ i18n("Filter {0}", field) }
                value={ values[0] ?? "" }
                onChange={ ev => setValues([ev.target.value === "" ? null : ev.target.value]) }>
            <option value="">{ i18n("Any") }</option>
            <option value="true">{ i18n("Yes") }</option>
            <option value="false">{ i18n("No") }</option>
        </select>
    )
}

/**
 * The filter of a Boolean column: `eq` on the field, chosen from a select of any, yes and no.
 */
export function booleanFilter(): ColumnFilter<string[]>
{
    return {...operatorFilter("eq", "Boolean"), Input: BooleanSelect}
}
