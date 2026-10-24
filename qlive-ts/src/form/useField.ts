/*
 * useField: one field of a form, resolved against the schema and bound to the draft.
 */
import {ReactNode, useLayoutEffect} from "react";

import {actionParameters, Refusal} from "../action";
import config, {DomainFieldMeta} from "../config";
import type {FilterExpression} from "../FilterDSL";
import {hasInput, inputText, parseInput} from "../converter";
import i18n from "../i18n";
import {MergeField, untouchedField} from "../merge/MergeAccessor";
import type {MergeRefusal} from "../merge/types";
import {manyToManyField} from "../merge/meta";
import {GraphQLTypeRef} from "../GraphQLSchema";
import {findType, isListType, isNonNull, objectFields, unwrapAll} from "../type-utils";
import {scalarEqual} from "../util/scalar";
import {defaultFieldMode, FieldMode} from "./fieldMode";
import {FormState, useFormState} from "./FormContext";
import type {FieldDecorator, Widget} from "./widgets";

/**
 * What a field is given where it is written, in place of what is derived.
 */
export interface FieldOptions
{
    /** the mode the field starts in, see FormContext.modes */
    mode?: FieldMode
    /** the widget, or the name it is registered under (registerWidget()) */
    widget?: Widget | string
    /** the component around the widget, `null` for none, see WidgetOverride */
    decorator?: FieldDecorator | null
    /** the label, in place of the i18n() of `Type.field` */
    label?: ReactNode
    /**
     * For a to-one relation, which related rows it offers: a FilterDSL condition written against the form's row, which
     * reaches the related row through the relation. A related row is offered where the condition holds for the form's
     * row with that row in the relation's place.
     *
     *     <Field path="owner" condition={ field("owner.login").ne(field("name")) }/>
     *
     * Since it reads the form's row, the rows on offer follow it as it is edited.
     */
    condition?: FilterExpression | null
}

/**
 * One field of a form, as of this render: what it is, the mode it is in, its value and its text.
 */
export interface FieldState
{
    /** the path the field was asked for by */
    path: string
    /** the form's type; for a field of an action's parameters, the action's name */
    domainType: string
    /** the field's name in it; for a field of an action's parameters, its path */
    name: string
    /** true for a field of an action's parameters rather than of the row */
    parameter: boolean
    /** the field's type: a scalar or enum type, or the related type for a relation */
    fieldType: string
    /**
     * - `scalar`: a scalar or enum field
     * - `toOne`: a to-one relation, named by its object field and edited through its foreign key
     * - `manyToMany`: a many-to-many field, holding the related rows
     */
    kind: "scalar" | "toOne" | "manyToMany"
    label: ReactNode
    /** the input's element id, for the label's htmlFor */
    id: string
    /** the label's element id, for a widget's aria-labelledby where the label can't name the input by htmlFor */
    labelId: string
    /**
     * The id of the text saying what the merge did to the field, which a screen reader reads in place of the mark's
     * color. A widget puts it in its input's `aria-describedby`, as it puts `id` on the input: one that doesn't
     * leaves a screen reader without the field's merge state, and nothing looks wrong. Field renders the text; it is
     * empty while nothing happened to the field.
     */
    descriptionId: string
    mode: FieldMode
    /** non-null in the schema */
    required: boolean
    /** the field's meta data, maxLength and the like */
    meta: DomainFieldMeta
    /** which related rows a to-one relation offers, see FieldOptions.condition; null for all */
    condition: FilterExpression | null

    /** the value, read through the draft: the foreign key for a to-one relation */
    value: unknown
    /** writes a value to the draft, and drops text the field held */
    setValue(value: unknown): void

    /** what a text input shows: the text being typed, else the input text of the value */
    text: string
    /** takes what the user typed: text that parses sets the value, text that doesn't stays, with an error */
    setText(text: string): void
    /** why the text didn't parse, a message for the user; null where it did */
    error: string | null

    /** the merge state of the field the value is in; for a field of an action's parameters, that of no row's */
    merge: MergeField

    /**
     * The refusals of the action's last issue that blame the field or one below it. For a field of the form's row, a
     * refusal blames it by the subject's name, `bar.description` for the field `description`. Then the invariants of
     * the row's type that blame the field and that the row breaks, as `merge.refusals` has them.
     */
    refusals: Refusal[]
}

/** what a path resolves to */
interface Resolved
{
    name: string
    kind: FieldState["kind"]
    fieldType: string
    /** the field the value is in */
    storage: string
    required: boolean
    /** for a field of an action's parameters: the parameter, and the path into its value */
    parameter?: { name: string, rest: string[] }
}

function fieldError(type: string, path: string, message: string)
{
    return new Error("Field " + JSON.stringify(path) + " of " + type + ": " + message)
}

/**
 * What a path resolves to in a form with an action, where its first segment names a parameter; null where it names
 * none, which makes it a field of the row.
 */
function resolveParameter(form: FormState, path: string): Resolved | null
{
    const {action, subject, domainType} = form
    if (!action)
    {
        return null
    }

    const [first, ...rest] = path.split(".")
    const parameter = actionParameters(action.name).find(p => p.name === first && p.name !== subject)
    if (!parameter)
    {
        if (domainType === null)
        {
            throw fieldError(action.name, path, "a form without a row edits the action's parameters, and names none.")
        }
        return null
    }
    if (domainType !== null && objectFields(domainType).some(f => f.name === first))
    {
        throw fieldError(
            domainType, path, "both a field of the row and a parameter of action '" + action.name + "', which a " +
            "path can't tell apart. Rename the parameter."
        )
    }
    if (parameter.subject)
    {
        throw fieldError(
            action.name, path, "a subject, which is a row: the form's own, or one the view sets on the action."
        )
    }

    // along the input object types, to a scalar or an enum
    let type: GraphQLTypeRef = parameter.typeRef
    let required = parameter.required
    for (const segment of rest)
    {
        const named = findType(unwrapAll(type).name!)
        const field = isListType(type) ? undefined : named.inputFields?.find(f => f.name === segment)
        if (!field)
        {
            throw fieldError(action.name, path, "no field '" + segment + "' in " + named.name + ".")
        }
        type = field.type
        required = isNonNull(field.type)
    }
    const named = findType(unwrapAll(type).name!)
    if (isListType(type) || (named.kind !== "SCALAR" && named.kind !== "ENUM"))
    {
        throw fieldError(action.name, path, "a " + (isListType(type) ? "list" : named.name) + ", which no field edits.")
    }
    return {
        name: path, kind: "scalar", fieldType: named.name, storage: path, required, parameter: {name: first, rest}
    }
}

function resolve(type: string, path: string): Resolved
{
    if (path.includes("."))
    {
        throw fieldError(type, path, "a form edits its own row's fields; a related row's are a field group's.")
    }
    const fields = objectFields(type)
    const field = fields.find(f => f.name === path)
    if (!field)
    {
        throw fieldError(type, path, "no such field.")
    }
    const named = unwrapAll(field.type)
    if (isListType(field.type))
    {
        if (!manyToManyField(type, path))
        {
            throw fieldError(type, path, "a list, and not a many-to-many field.")
        }
        return {name: path, kind: "manyToMany", fieldType: named.name!, storage: path, required: false}
    }
    if (named.kind === "OBJECT")
    {
        const relation = config().meta.relations.find(r => r.sourceType === type && r.leftSideObjectName === path)
        if (!relation)
        {
            throw fieldError(type, path, "an object, and not a to-one relation.")
        }
        if (relation.sourceFields.length !== 1)
        {
            throw fieldError(type, path, "a relation over a key of several fields, which a form can't edit yet.")
        }
        const key = relation.sourceFields[0]
        const keyField = fields.find(f => f.name === key)
        return {
            name: path,
            kind: "toOne",
            fieldType: named.name!,
            storage: key,
            required: keyField ? isNonNull(keyField.type) : false
        }
    }
    return {name: path, kind: "scalar", fieldType: named.name!, storage: path, required: isNonNull(field.type)}
}

/** whether the field's value is edited as text */
function textual(resolved: Resolved): boolean
{
    return resolved.kind === "scalar" && hasInput(resolved.fieldType)
}

/**
 * The state of one field, built from the form's state. What useField() returns, without being a hook.
 *
 * @internal
 */
export function fieldState(form: FormState, path: string, options: FieldOptions = {}): FieldState
{
    const {action, subject} = form
    const resolved = resolveParameter(form, path) ?? resolve(form.domainType!, path)
    const parameter = resolved.parameter
    const type = parameter ? action!.name : form.domainType!
    if (options.condition && resolved.kind !== "toOne")
    {
        throw fieldError(type, path, "a condition narrows the rows a to-one relation offers, and this is none.")
    }
    const {storage, fieldType} = resolved

    let value: unknown
    let write: (next: unknown) => void
    if (parameter)
    {
        const args = action!.args as Record<string, unknown>
        value = readPath(args[parameter.name], parameter.rest)
        const set = action!.set as (parameter: string, value: unknown) => void
        write = next => set(parameter.name, writePath(args[parameter.name], parameter.rest, next))
    }
    else
    {
        const draft = form.row as Record<string, unknown>
        value = draft[storage]
        write = next => { draft[storage] = next }
    }
    const edit = form.edits.get(path)

    let text: string
    if (edit && (edit.error !== null || scalarEqual(edit.value, value)))
    {
        text = edit.text
    }
    else if (textual(resolved))
    {
        text = inputText(value, fieldType)
    }
    else
    {
        text = value === null || value === undefined ? "" : String(value)
    }

    const merge = parameter ? untouchedField(path, value) : form.merge.field(storage)

    return {
        path,
        domainType: type,
        name: resolved.name,
        parameter: !!parameter,
        fieldType,
        kind: resolved.kind,
        label: options.label ?? i18n(type + "." + resolved.name),
        id: form.idPrefix + path,
        labelId: form.idPrefix + path + "-label",
        descriptionId: form.idPrefix + path + "-state",
        mode: form.modes.get(path) ?? options.mode ?? (parameter ? "normal" : defaultFieldMode(type, storage)),
        required: resolved.required,
        meta: parameter ? {} : config().meta.types[type]?.fields?.[storage] ?? {},
        condition: options.condition ?? null,

        value,
        setValue: next => {
            form.setEdit(path, null)
            write(next)
        },

        text,
        setText: next => {
            if (!textual(resolved) && !(resolved.kind === "scalar" && findType(fieldType).kind === "ENUM"))
            {
                throw fieldError(type, path, "a " + fieldType + " has no input text.")
            }
            let parsed: unknown
            try
            {
                parsed = textual(resolved) ? parseInput(next, fieldType) : next.trim() === "" ? null : next
            }
            catch (e)
            {
                form.setEdit(path, {text: next, value: undefined, error: (e as Error).message})
                return
            }
            form.setEdit(path, {text: next, value: parsed, error: null})
            write(parsed)
        },
        error: edit?.error ?? null,

        merge,
        refusals: [
            ...(action ? action.refusalsOf(refusalPath(path, !!parameter, subject)) : []),
            ...merge.refusals.map(invariantRefusal)
        ]
    }
}

/**
 * An invariant the row breaks, as a field shows it among an action's refusals.
 */
function invariantRefusal(refusal: MergeRefusal): Refusal
{
    return {code: "RULE", messageKey: refusal.messageKey, field: refusal.field, errorClass: refusal.errorClass}
}

/**
 * The path a refusal blames a field by: a parameter's as it is, a field of the row after the subject's name.
 */
function refusalPath(path: string, parameter: boolean, subject: string | null): string
{
    return parameter || subject === null ? path : subject + "." + path
}

/** the value at a path into a parameter's argument */
function readPath(value: unknown, rest: string[]): unknown
{
    return rest.reduce((current: any, segment) => current == null ? undefined : current[segment], value)
}

/** the argument with the value at a path into it replaced, as a new object along the path */
function writePath(value: unknown, rest: string[], next: unknown): unknown
{
    if (!rest.length)
    {
        return next
    }
    const [segment, ...deeper] = rest
    const current = (value ?? {}) as Record<string, unknown>
    return {...current, [segment]: writePath(current[segment], deeper, next)}
}

/**
 * One field of the form the calling component is in: what it is, the mode it is in, its value, and its text.
 *
 * A path is a field of the form's row. A to-one relation is named by its object field, `owner`, and edits the
 * foreign key behind it; a many-to-many field holds the related rows. A field of a related row, `owner.login`, is
 * not a form field.
 *
 * In a form with an action, a path whose first segment names one of the action's parameters is that parameter's
 * instead: `comment`, or `address.street` into an input object, as the action's rules name it. It ends at a scalar or
 * an enum, and reads and writes the action draft's argument. A name both a parameter and a field of the row is an
 * error, and so is a subject, which is a row. The field claims its refusals (ActionDraft.claim()).
 *
 * A widget editing text calls `setText()`: text that parses (parseInput()) sets the value, text that doesn't stays as
 * it was typed, with an `error`, and is listed in the form's `invalid`. A widget editing a value, a checkbox or a
 * picker, calls `setValue()` and never has an error.
 *
 * Rules of hooks apply.
 *
 * @param path      field path
 * @param options   what is given in place of what is derived
 *
 * @throws Error outside a Form, and for a path the form's type has no field for
 */
export function useField(path: string, options?: FieldOptions): FieldState
{
    const form = useFormState()
    const field = fieldState(form, path, options)

    // what the form's live region calls the field, if it ever has to
    const {registerField} = form
    const {labelId} = field

    // the refusals the field shows are not the action's unclaimed ones
    const claim = form.action?.claim
    const claimed = form.action ? refusalPath(path, field.parameter, form.subject) : null
    useLayoutEffect(() => claim && claimed !== null ? claim(claimed) : undefined, [claim, claimed])
    const name = field.merge.name
    const label = typeof field.label === "string" ? field.label : i18n(field.domainType + "." + field.name)
    useLayoutEffect(() => registerField(name, labelId, label), [registerField, name, labelId, label])

    return field
}
