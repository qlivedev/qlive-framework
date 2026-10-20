/*
 * RowSet: the widget of a many-to-many field, the rows a row is associated with.
 */
import {JSX} from "react";

import {catalogRows, isCatalog} from "../catalog";
import {field as fieldRef, FilterExpression, not, values} from "../FilterDSL";
import i18n from "../i18n";
import {objectFields, unwrapAll} from "../type-utils";
import {rowName, targetKey} from "./fieldText";
import {PickerInput, rowChoices} from "./PickerInput";
import type {WidgetProps} from "./widgets";

/**
 * Edits a many-to-many field: a picker adding one of the rows not yet associated, and after it the associated rows,
 * each named and with a button removing it. The picker comes first so it stays in place while rows come and go. It
 * searches as RowPicker searches a to-one relation's rows: through the related-rows endpoint, or in the browser for
 * a catalog type. The field is set to the new list of rows, and the merge writes the
 * difference as associations gained and lost.
 *
 * A row added comes from the picker, with its key and name fields only: other fields of it read as missing until
 * the row's query is read again.
 *
 * In `disabled` and `readOnly` the rows are listed without the buttons and without the picker.
 */
export function RowSet({field}: WidgetProps): JSX.Element
{
    const type = field.fieldType
    const key = targetKey(field)
    const rows = (field.value ?? []) as any[]
    const keys = rows.map(row => row[key])
    const editable = field.mode === "normal"

    const known = isCatalog(type)
        ? rowChoices(type, key, catalogRows(type).filter(row => !keys.includes(row[key])))
        : null

    // the endpoint offers the rows not associated yet
    const keyType = unwrapAll(objectFields(type).find(f => f.name === key)!.type).name!
    const narrowed = !known && keys.length
        ? not(fieldRef(key).in(values(keyType, ...keys as any[]))) as FilterExpression
        : null

    return (
        <span className="qlive-rowset" id={ editable ? undefined : field.id }>
            {
                editable && (
                    <PickerInput
                        id={ field.id }
                        labelId={ field.labelId }
                        mode={ field.mode }
                        required={ false }
                        shown=""
                        placeholder={ i18n("Add...") }
                        known={ known }
                        type={ type }
                        keyField={ key }
                        narrowed={ narrowed }
                        onChoose={ chosen => field.setValue([...rows, chosen.row]) }
                    />
                )
            }
            {
                rows.length > 0 && (
                    <ul className="qlive-rowset-rows">
                        {
                            rows.map(row => {
                                const name = rowName(type, row, row[key])
                                return (
                                    <li key={ String(row[key]) }>
                                        { name }
                                        {
                                            editable && (
                                                <button
                                                    type="button"
                                                    className="qlive-rowset-remove"
                                                    aria-label={ i18n("Remove {0}", name) }
                                                    onClick={ () => field.setValue(rows.filter(other => other[key] !== row[key])) }
                                                >
                                                    ×
                                                </button>
                                            )
                                        }
                                    </li>
                                )
                            })
                        }
                    </ul>
                )
            }
        </span>
    )
}
