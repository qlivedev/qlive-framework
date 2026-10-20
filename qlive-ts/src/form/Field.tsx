/*
 * Field: a field's label, widget and error; and the conflict buttons QLive decorates a control with.
 */
import type {JSX} from "react";

import i18n from "../i18n";
import {fieldText} from "./fieldText";
import {FormContext, useFormState} from "./FormContext";
import {FieldOptions, FieldState, fieldState} from "./useField";
import {builtInWidget, FieldDecoratorProps, findOverride, namedWidget, Widget} from "./widgets";

/**
 * The buttons that resolve a field both writes changed, around its widget: one keeping the user's value, one taking
 * the saved one. Where the saved value isn't known yet -- a push message says which fields changed, not to what --
 * a note instead, until a save brings both.
 *
 * What a form rendering a layout decorates every control with, unless an override gives a decorator (WidgetOverride). A
 * hand-written form gives it where it wants it, `<Field path="name" decorator={ ConflictResolution }/>`.
 */
export function ConflictResolution({field, children}: FieldDecoratorProps): JSX.Element
{
    const {merge} = field
    const {row} = useFormState()
    return (
        <>
            { children }
            {
                merge.status === "conflict" && (
                    merge.storedKnown
                        ? (
                            <span className="qlive-field-resolve">
                                <button className="btn" type="button" onClick={ () => merge.resolve("mine") }>
                                    { i18n("Yours: {0}", fieldText(field, row, merge.mine)) }
                                </button>
                                <button className="btn" type="button" onClick={ () => merge.resolve("stored") }>
                                    { i18n("Saved: {0}", fieldText(field, row, merge.stored)) }
                                </button>
                            </span>
                        )
                        : <span className="qlive-field-resolve">{ i18n("Somebody else changed this too") }</span>
                )
            }
        </>
    )
}

/**
 * The widget of a field: the one given, by component or name, else an override, else QLive's own for the field's
 * type.
 */
function widgetOf(form: FormContext, field: FieldState, given: Widget | string | undefined): Widget
{
    if (typeof given === "string")
    {
        return namedWidget(given)
    }
    return given ?? findOverride("widget", form, field) ?? builtInWidget(field)
}

/**
 * One field of the form it is in: a label, the widget, the error, with the merge mark on the control.
 *
 * The widget is the one given, else the most specific override in `config().widgets` (WidgetOverride), else QLive's
 * own for the type: a text input, a number input, a date or date-time picker, a checkbox, a select for a nullable
 * Boolean, a RowPicker for an enum or a to-one relation. A field QLive has none for -- a many-to-many field, until its row set exists, or
 * a scalar without input text -- shows its value and says so.
 *
 * A field in `text` mode renders no widget at all, but the value as text in a `qlive-field-text` span.
 *
 * The control, widget or text, carries the merge mark; a decorator goes around the control, so what it adds sits
 * beside the marked box rather than in it.
 *
 * @param props     the field path, and the options of useField()
 */
export function Field(props: { path: string } & FieldOptions): JSX.Element
{
    const form = useFormState()
    const field = fieldState(form, props.path, props)
    const labelId = field.id + "-label"

    let content: JSX.Element
    if (field.mode === "text")
    {
        content = <FieldText field={ field } labelId={ labelId }/>
    }
    else
    {
        const FieldWidget = widgetOf(form, field, props.widget)
        content = <FieldWidget field={ field }/>
    }
    const control = <div className={ ("qlive-field-control " + field.merge.className).trim() }>{ content }</div>
    const Decorator = props.decorator !== undefined
        ? props.decorator
        : findOverride("decorator", form, field) ?? (form.fromLayout ? ConflictResolution : null)

    return (
        <div className={ "qlive-field qlive-field-mode-" + field.mode }>
            <label id={ labelId } htmlFor={ field.mode === "text" ? undefined : field.id }>{ field.label }</label>
            <div className="qlive-field-body">
                { Decorator ? <Decorator field={ field }>{ control }</Decorator> : control }
                { field.error !== null && <div className="qlive-field-error" role="alert">{ field.error }</div> }
            </div>
        </div>
    )
}

/**
 * A field in `text` mode: its value as text, labeled by the field's label.
 */
function FieldText({field, labelId}: { field: FieldState, labelId: string }): JSX.Element
{
    const {row} = useFormState()
    return <span id={ field.id } className="qlive-field-text" aria-labelledby={ labelId }>{ fieldText(field, row) }</span>
}
