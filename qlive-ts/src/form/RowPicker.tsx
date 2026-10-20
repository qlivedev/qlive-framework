/*
 * RowPicker: the widget of a closed set, choosing a value by searching what the values are called.
 */
import {JSX} from "react";

import {catalogRows, isCatalog} from "../catalog";
import {candidatePredicate, endpointCondition} from "./fieldCondition";
import {fieldText, targetKey} from "./fieldText";
import {useFormState} from "./FormContext";
import {enumChoices, PickerInput, rowChoices} from "./PickerInput";
import type {WidgetProps} from "./widgets";

/**
 * Picks a value of a closed set: a text input searching the set by what its values are called, and the list of the
 * values found, the first page of them. Choosing one sets the field; a nullable field is cleared by emptying the
 * input. The input shows the chosen value's name while it isn't being typed into, and what the user typed while it
 * is; typed text that chooses nothing is dropped when the input loses focus.
 *
 * What the set is depends on the field:
 *
 * - a to-one relation picks a related row and sets the foreign key. The rows come from relatedRows(), the server's
 *   related-rows endpoint, found by their name fields and sorted by them.
 * - a to-one relation to a catalog type picks one of its rows, which the bootstrap brought (see catalogRows()). The
 *   set is known in the browser and searched there, by the rows' names, in the order the server read them.
 * - an enum field picks one of the type's values, but the deprecated ones, each called by its `Type.VALUE`
 *   translation. The set is known in the browser too.
 *
 * A to-one field's condition (FieldOptions.condition) narrows the rows: tested on each catalog row in the browser, sent
 * to the endpoint otherwise, where the rows are asked for again when a value of the form's row it reads changes.
 *
 * QLive's widget for all of them, so a form edits a closed set without being told how, and the same way however large the
 * set grows.
 */
export function RowPicker({field}: WidgetProps): JSX.Element
{
    const {row} = useFormState()
    const type = field.fieldType
    const key = field.kind === "scalar" ? null : targetKey(field)
    const catalog = key !== null && isCatalog(type)
    const known = key === null ? enumChoices(type)
        : catalog ? rowChoices(type, key, offeredRows(catalogRows(type)))
            : null

    /** the catalog rows the field's condition lets through */
    function offeredRows(rows: any[]): any[]
    {
        if (!field.condition)
        {
            return rows
        }
        const offered = candidatePredicate(field, row)
        return rows.filter(offered)
    }

    return (
        <PickerInput
            id={ field.id }
            mode={ field.mode }
            required={ field.required }
            shown={ fieldText(field, row) }
            known={ known }
            type={ type }
            keyField={ key }
            // the endpoint's part of the field's condition
            narrowed={ key !== null && !catalog ? endpointCondition(field, row) : null }
            onChoose={ chosen => field.setValue(chosen.value) }
            onEmpty={ field.required ? undefined : () => field.setValue(null) }
        />
    )
}
