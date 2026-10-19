import { WidgetProps } from "@qlivedev/qlive-ts";

/**
 * Widgets of the application's own: what a form field is edited with where QLive's built-ins won't do. A widget
 * gets the field's whole state and renders the input only -- label, error and merge mark are the form's.
 *
 * main.tsx puts them to use, one by name for layouts that refer to it as a string, one as an override that
 * applies wherever a form shows the field without naming a widget.
 */

/**
 * Edits text in a text area. Through setText(), like QLive's own text input: a String takes the text as it is.
 */
export function TextArea({ field }: WidgetProps)
{
    return (
        <textarea
            id={ field.id }
            rows={ 3 }
            value={ field.text }
            disabled={ field.mode === "disabled" }
            readOnly={ field.mode === "readOnly" }
            onChange={ e => field.setText(e.target.value) }
        />
    )
}

/**
 * A whole number with a step down and a step up. The input goes through setText(), so text that isn't a number
 * stays as typed with an error; the buttons set values through setValue(), which drops that text.
 */
export function Stepper({ field }: WidgetProps)
{
    const editable = field.mode === "normal"
    const value = typeof field.value === "number" ? field.value : 0

    return (
        <span className="stepper">
            <button type="button" disabled={ !editable } onClick={ () => field.setValue(value - 1) }>-</button>
            <input
                id={ field.id }
                inputMode="numeric"
                value={ field.text }
                disabled={ field.mode === "disabled" }
                readOnly={ field.mode === "readOnly" }
                aria-invalid={ field.error !== null || undefined }
                onChange={ e => field.setText(e.target.value) }
            />
            <button type="button" disabled={ !editable } onClick={ () => field.setValue(value + 1) }>+</button>
        </span>
    )
}
