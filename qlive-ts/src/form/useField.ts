/*
 * useField: one field of a form, resolved against the schema and bound to the draft.
 */
import type {ReactNode} from "react";

import config, {DomainFieldMeta} from "../config";
import type {FilterExpression} from "../FilterDSL";
import {hasInput, inputText, parseInput} from "../converter";
import i18n from "../i18n";
import type {MergeField} from "../merge/MergeAccessor";
import {manyToManyField} from "../merge/meta";
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
    /** the form's type */
    domainType: string
    /** the field's name in it */
    name: string
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

    /** the merge state of the field the value is in */
    merge: MergeField
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
}

function fieldError(type: string, path: string, message: string)
{
    return new Error("Field " + JSON.stringify(path) + " of " + type + ": " + message)
}

function resolve(type: string, path: string): Resolved
{
    if (path.startsWith("input."))
    {
        throw fieldError(type, path, "action input isn't supported yet.")
    }
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
    const {row, domainType: type} = form
    const resolved = resolve(type, path)
    if (options.condition && resolved.kind !== "toOne")
    {
        throw fieldError(type, path, "a condition narrows the rows a to-one relation offers, and this is none.")
    }
    const {storage, fieldType} = resolved
    const draft = row as Record<string, unknown>
    const value = draft[storage]
    const edit = form.edits.get(path)

    const write = (next: unknown) => { draft[storage] = next }

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

    return {
        path,
        domainType: type,
        name: resolved.name,
        fieldType,
        kind: resolved.kind,
        label: options.label ?? i18n(type + "." + resolved.name),
        id: form.idPrefix + path,
        labelId: form.idPrefix + path + "-label",
        mode: form.modes.get(path) ?? options.mode ?? defaultFieldMode(type, storage),
        required: resolved.required,
        meta: config().meta.types[type]?.fields?.[storage] ?? {},
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

        merge: form.merge.field(storage)
    }
}

/**
 * One field of the form the calling component is in: what it is, the mode it is in, its value, and its text.
 *
 * A path is a field of the form's row. A to-one relation is named by its object field, `owner`, and edits the
 * foreign key behind it; a many-to-many field holds the related rows. A field of a related row, `owner.login`, is
 * not a form field.
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
    return fieldState(useFormState(), path, options)
}
