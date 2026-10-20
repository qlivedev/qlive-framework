/*
 * Field: a field's label, widget and error; and the conflict buttons QLive decorates a control with.
 */
import type {JSX} from "react";

import i18n from "../i18n";
import {fieldStatusText} from "../merge/MergeAccessor";
import {fieldText} from "./fieldText";
import {FormContext, useFormState} from "./FormContext";
import {FieldOptions, FieldState, useField} from "./useField";
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
 * Boolean, a RowPicker for an enum or a to-one relation, a RowSet for a many-to-many field. A field QLive has none
 * for, a scalar without input text, shows its value and says so.
 *
 * A field in `text` mode renders no widget at all, but the value as text in a `qlive-field-text` span.
 *
 * The control, widget or text, carries the merge mark; a decorator goes around the control, so what it adds sits
 * beside the marked box rather than in it. Before the control sits the mark's icon, so the states differ by shape
 * and not by color alone; after it a visually hidden text naming the state, which the widget's input is described
 * by (`field.descriptionId`, see Widget).
 *
 * @param props     the field path, and the options of useField()
 */
export function Field(props: { path: string } & FieldOptions): JSX.Element
{
    const form = useFormState()
    const field = useField(props.path, props)

    let content: JSX.Element
    if (field.mode === "text")
    {
        content = <FieldText field={ field }/>
    }
    else
    {
        const FieldWidget = widgetOf(form, field, props.widget)
        content = <FieldWidget field={ field }/>
    }
    const markClass = field.merge.className
    const control = <div className={ ("qlive-field-control " + markClass).trim() }>{ content }</div>
    const Decorator = props.decorator !== undefined
        ? props.decorator
        : findOverride("decorator", form, field) ?? (form.fromLayout ? ConflictResolution : null)

    return (
        <div className={ "qlive-field qlive-field-mode-" + field.mode }>
            <label id={ field.labelId } htmlFor={ field.mode === "text" ? undefined : field.id }>{ field.label }</label>
            <div className="qlive-field-body">
                <span className={ ("qlive-field-mark " + markClass).trim() } aria-hidden="true"/>
                { Decorator ? <Decorator field={ field }>{ control }</Decorator> : control }
                <span id={ field.descriptionId } className="qlive-visually-hidden">
                    { fieldStatusText(field.merge.status) }
                </span>
                { field.error !== null && <div className="qlive-field-error" role="alert">{ field.error }</div> }
            </div>
        </div>
    )
}

/**
 * A field in `text` mode: its value as text, read after the field's label. Plain text can't be named by the label,
 * aria-labelledby is not allowed on it.
 */
function FieldText({field}: { field: FieldState }): JSX.Element
{
    const {row} = useFormState()
    return <span id={ field.id } className="qlive-field-text">{ fieldText(field, row) }</span>
}
