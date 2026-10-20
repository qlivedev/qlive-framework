/*
 * Widgets: what edits one field, how a field finds its widget, and the widgets QLive ships.
 */
import type {ComponentType, JSX, ReactNode} from "react";

import config from "../config";
import {formatValue, hasInput} from "../converter";
import i18n from "../i18n";
import {findType} from "../type-utils";
import type {FormContext} from "./FormContext";
import {RowPicker} from "./RowPicker";
import type {FieldState} from "./useField";

/**
 * The input of one field. It gets the whole field state and renders the input only: label, error and merge marks are
 * Field's. It only ever sees the modes `normal`, `disabled` and `readOnly`, since a field in `text` mode renders no
 * widget.
 */
export type Widget = ComponentType<WidgetProps>

/**
 * Props of a Widget.
 */
export interface WidgetProps
{
    field: FieldState
}

/**
 * A component given a field's control as `children`, rendering it with whatever it adds around it. What QLive's conflict buttons are
 * (ConflictResolution), and where an application puts UI about a field that no widget should have to know about.
 */
export type FieldDecorator = ComponentType<FieldDecoratorProps>

/**
 * Props of a FieldDecorator.
 */
export interface FieldDecoratorProps
{
    field: FieldState
    /** the field's control, widget and merge mark */
    children: ReactNode
}

/**
 * A widget or decorator for the fields an application doesn't name one for, listed in `config().widgets`. It matches
 * on where the field is and on what it is, each by one key at most:
 *
 * - where: the `layout` the field is in, by name, or the `domainType` of the row the form edits
 * - what: the field's `path`, or its `fieldType`
 *
 * A key left out matches anything, and an override giving both keys of one kind is an error.
 *
 *     config.widgets = [
 *         {fieldType: "Money", widget: MoneyInput},
 *         {fieldType: "AppUser", widget: UserPicker},
 *         {domainType: "Bar", path: "num", widget: Slider}
 *     ]
 *
 * Of the overrides that match, the most specific wins: what the field is counts first, a path over a field type over
 * neither, then where it is, a layout over a domain type over neither. Of two equally specific ones the later wins.
 * Widgets and decorators are matched separately, so a decorator override doesn't hide a widget override that is less
 * specific.
 */
export interface WidgetOverride
{
    /** the name of the layout the field is in */
    layout?: string
    /** the type of the row the form edits */
    domainType?: string
    /** the field's path from that type */
    path?: string
    /** the field's type: a scalar or enum type, or the related type for a relation */
    fieldType?: string
    widget?: Widget
    decorator?: FieldDecorator
}

const widgetRegistry = new Map<string, Widget>()

/**
 * Gives a widget the name a layout refers to it by. A layout written in TSX holds the component itself and needs no
 * name; a layout the server delivers can't hold one.
 *
 * @param name      widget name
 * @param widget    the widget
 */
export function registerWidget(name: string, widget: Widget): void
{
    widgetRegistry.set(name, widget)
}

/**
 * The widget a name stands for.
 *
 * @throws Error for a name no widget is registered under
 */
export function namedWidget(name: string): Widget
{
    const widget = widgetRegistry.get(name)
    if (!widget)
    {
        throw new Error("No widget is registered as " + JSON.stringify(name) + ". Register it with registerWidget().")
    }
    return widget
}

/**
 * Refuses an override that gives two keys of one kind, which leaves open which of them it means.
 */
function checkOverride(override: WidgetOverride): void
{
    if (override.layout !== undefined && override.domainType !== undefined ||
        override.path !== undefined && override.fieldType !== undefined)
    {
        throw new Error(
            "A widget override gives a layout or a domain type, and a path or a field type, not both: " +
            JSON.stringify({...override, widget: undefined, decorator: undefined})
        )
    }
}

/** how specific a matching override is, -1 for one that doesn't match */
function specificity(override: WidgetOverride, form: FormContext, field: FieldState): number
{
    checkOverride(override)
    if (override.layout !== undefined && override.layout !== form.layoutName ||
        override.domainType !== undefined && override.domainType !== form.domainType ||
        override.path !== undefined && override.path !== field.path ||
        override.fieldType !== undefined && override.fieldType !== field.fieldType)
    {
        return -1
    }
    const what = override.path !== undefined ? 2 : override.fieldType !== undefined ? 1 : 0
    const where = override.layout !== undefined ? 2 : override.domainType !== undefined ? 1 : 0
    return what * 3 + where
}

/**
 * The most specific override in `config().widgets` that gives the member asked for, `null` for none.
 *
 * @throws Error for an override giving two keys of one kind
 *
 * @internal
 */
export function findOverride<K extends "widget" | "decorator">(
    member: K,
    form: FormContext,
    field: FieldState
): NonNullable<WidgetOverride[K]> | null
{
    let best: NonNullable<WidgetOverride[K]> | null = null
    let bestScore = -1
    for (const override of config().widgets ?? [])
    {
        const candidate = override[member]
        const score = candidate ? specificity(override, form, field) : -1
        if (score >= 0 && score >= bestScore)
        {
            best = candidate as NonNullable<WidgetOverride[K]>
            bestScore = score
        }
    }
    return best
}

/**
 * Whether the field's text input is disabled or read-only, as attributes.
 */
function modeProps(field: FieldState)
{
    return {disabled: field.mode === "disabled", readOnly: field.mode === "readOnly"}
}

/**
 * Edits a field as text: String, ID, and every type with an input text (see inputText()).
 */
export function TextInput({field, type = "text", inputMode}: {
    field: FieldState,
    type?: string,
    inputMode?: "decimal" | "numeric"
}): JSX.Element
{
    return (
        <input
            id={ field.id }
            type={ type }
            inputMode={ inputMode }
            value={ field.text }
            required={ field.required }
            maxLength={ field.meta.maxLength }
            aria-invalid={ field.error !== null || undefined }
            onChange={ e => field.setText(e.target.value) }
            { ...modeProps(field) }
        />
    )
}

/**
 * Edits a number as text. Not `type="number"`: a browser hands that over as empty while it can't parse it, which
 * would hide what the user typed, and it has no idea of a bigint.
 */
export function NumberInput({field}: WidgetProps): JSX.Element
{
    const whole = field.fieldType !== "Float" && field.fieldType !== "Currency" && field.fieldType !== "BigDecimal"
    return <TextInput field={ field } inputMode={ whole ? "numeric" : "decimal" }/>
}

/** a date picker */
export function DateInput({field}: WidgetProps): JSX.Element
{
    return <TextInput field={ field } type="date"/>
}

/** a date and time picker in the user's time zone */
export function DateTimeInput({field}: WidgetProps): JSX.Element
{
    return <TextInput field={ field } type="datetime-local"/>
}

/**
 * Edits a non-null Boolean. A checkbox has no read-only state, so `readOnly` disables it.
 */
export function Checkbox({field}: WidgetProps): JSX.Element
{
    return (
        <input
            id={ field.id }
            type="checkbox"
            checked={ field.value === true }
            disabled={ field.mode !== "normal" }
            onChange={ e => field.setValue(e.target.checked) }
        />
    )
}

/**
 * Picks one of a set of values. A select has no read-only state, so `readOnly` disables it.
 */
function Choice({field, options}: { field: FieldState, options: { key: string, value: unknown, label: string }[] }): JSX.Element
{
    const current = field.value === null || field.value === undefined ? "" : String(field.value)
    const known = options.some(option => option.key === current)
    return (
        <select
            id={ field.id }
            value={ current }
            required={ field.required }
            disabled={ field.mode !== "normal" }
            onChange={ e => field.setValue(options.find(option => option.key === e.target.value)?.value ?? null) }
        >
            { (!field.required || current === "") && <option value=""/> }
            { !known && current !== "" && <option value={ current }>{ current }</option> }
            { options.map(option => <option key={ option.key } value={ option.key }>{ option.label }</option>) }
        </select>
    )
}

/**
 * Picks a value of an enum type, labeled `Type.VALUE` through i18n(); a nullable field can be cleared.
 */
export function EnumSelect({field}: WidgetProps): JSX.Element
{
    const values = findType(field.fieldType).enumValues ?? []
    return (
        <Choice
            field={ field }
            options={ values.map(v => ({key: v.name, value: v.name, label: i18n(field.fieldType + "." + v.name)})) }
        />
    )
}

/**
 * Edits a nullable Boolean: yes, no, or nothing.
 */
export function BooleanSelect({field}: WidgetProps): JSX.Element
{
    return (
        <Choice
            field={ field }
            options={ [
                {key: "true", value: true, label: i18n("Yes")},
                {key: "false", value: false, label: i18n("No")}
            ] }
        />
    )
}

/**
 * What a field gets where QLive has no widget for it, which a form shows rather than failing: a many-to-many field
 * until its row set exists, and a scalar without an input text.
 */
export function MissingWidget({field}: WidgetProps): JSX.Element
{
    return (
        <span id={ field.id } className="qlive-field-missing">
            { formatValue(field.value, field.kind === "scalar" ? field.fieldType : "String") }
            { " " }
            <small>({ i18n("No widget for {0}", field.fieldType) })</small>
        </span>
    )
}

const NUMBER_TYPES = new Set(["Byte", "Short", "Int", "Number", "Long", "BigInteger", "Float", "Currency", "BigDecimal"])

/**
 * The widget QLive has for a field: by its scalar type, or its relation kind.
 *
 * @internal
 */
export function builtInWidget(field: FieldState): Widget
{
    if (field.kind === "toOne")
    {
        return RowPicker
    }
    if (field.kind !== "scalar")
    {
        return MissingWidget
    }
    const type = field.fieldType
    if (findType(type).kind === "ENUM")
    {
        return EnumSelect
    }
    if (type === "Boolean")
    {
        return field.required ? Checkbox : BooleanSelect
    }
    if (!hasInput(type))
    {
        return MissingWidget
    }
    if (type === "Date")
    {
        return DateInput
    }
    if (type === "Timestamp")
    {
        return DateTimeInput
    }
    return NUMBER_TYPES.has(type) ? NumberInput : TextInput
}
