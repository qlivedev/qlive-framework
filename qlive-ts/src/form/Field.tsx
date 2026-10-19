/*
 * Field: a field's label, widget and error; and the conflict buttons QLive wraps a widget in.
 */
import type {JSX} from "react";

import config from "../config";
import {formatValue} from "../converter";
import i18n from "../i18n";
import {objectFields, primaryKeyFields, unwrapAll} from "../type-utils";
import {useFormState} from "./FormContext";
import {FieldOptions, FieldState, fieldState} from "./useField";
import {builtInWidget, FieldWrapperProps, findOverride, namedWidget, Widget} from "./widgets";

/**
 * A related row named by its type's name fields, the key where it has none or the row isn't selected.
 */
function rowName(type: string, row: any, key: unknown): string
{
    const nameFields = config().meta.types[type]?.meta?.nameFields ?? []
    if (!row || !nameFields.length || !nameFields.every(name => name in row))
    {
        return formatValue(key, "String")
    }
    const fields = objectFields(type)
    return nameFields
        .map(name => formatValue(row[name], unwrapAll(fields.find(f => f.name === name)!.type).name!))
        .filter(text => text !== "")
        .join(" ")
}

/**
 * The field of the related type a to-one relation's foreign key holds.
 */
function targetKey(field: FieldState): string
{
    const relation = config().meta.relations.find(r => r.sourceType === field.type && r.leftSideObjectName === field.name)
    return relation?.targetFields[0] ?? primaryKeyFields(field.scalarType)[0]
}

/**
 * The value of a field as text, what `text` mode shows: the formatted value, the related row's name for a relation.
 *
 * @param field     the field
 * @param row       the row it is a field of
 * @param value     the value to show, the field's own by default
 */
export function fieldText(field: FieldState, row: object, value: unknown = field.value): string
{
    switch (field.kind)
    {
        case "scalar":
            return formatValue(value, field.scalarType)
        case "toOne":
        {
            if (value === null || value === undefined)
            {
                return ""
            }
            // the relation object is what the row was read with, so it names the key only while that is unchanged
            const related = (row as any)[field.name]
            const key = targetKey(field)
            return rowName(field.scalarType, related && related[key] === value ? related : null, value)
        }
        case "manyToMany":
        {
            const key = primaryKeyFields(field.scalarType)[0]
            return ((value ?? []) as any[]).map(related => rowName(field.scalarType, related, related[key])).join(", ")
        }
    }
}

/**
 * The buttons that resolve a field both writes changed, around its widget: one keeping the user's value, one taking
 * the saved one. Where the saved value isn't known yet -- a push message says which fields changed, not to what --
 * a note instead, until a save brings both.
 *
 * What a form rendering a layout wraps every widget in, unless an override gives a wrapper (WidgetOverride). A
 * hand-written form gives it where it wants it, `<Field path="name" wrapper={ ConflictResolution }/>`.
 */
export function ConflictResolution({field, children}: FieldWrapperProps): JSX.Element
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
function widgetOf(field: FieldState, given: Widget | string | undefined): Widget
{
    if (typeof given === "string")
    {
        return namedWidget(given)
    }
    return given ?? findOverride("widget", field.type, field) ?? builtInWidget(field)
}

/**
 * One field of the form it is in: a label, the widget, the error, with the merge mark on the control.
 *
 * The widget is the one given, else the most specific override in `config().widgets` (WidgetOverride), else QLive's
 * own for the type: a text input, a number input, a date or date-time picker, a checkbox, a select for an enum. A
 * field QLive has none for -- a relation, until pickers exist, or a scalar without input text -- shows its value
 * and says so.
 *
 * A field in `text` mode renders no widget at all, but the value as text in a `qlive-field-text` span.
 *
 * The control, widget or text, carries the merge mark; a wrapper goes around the control, so what it adds sits
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
        const FieldWidget = widgetOf(field, props.widget)
        content = <FieldWidget field={ field }/>
    }
    const control = <div className={ ("qlive-field-control " + field.merge.className).trim() }>{ content }</div>
    const Wrapper = props.wrapper !== undefined
        ? props.wrapper
        : findOverride("wrapper", field.type, field) ?? (form.fromLayout ? ConflictResolution : null)

    return (
        <div className={ "qlive-field qlive-field-mode-" + field.mode }>
            <label id={ labelId } htmlFor={ field.mode === "text" ? undefined : field.id }>{ field.label }</label>
            <div className="qlive-field-body">
                { Wrapper ? <Wrapper field={ field }>{ control }</Wrapper> : control }
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
