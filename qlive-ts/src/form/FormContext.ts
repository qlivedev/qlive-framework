/*
 * The context of a form: what every field in it reads and writes through.
 */
import {createContext, useContext} from "react";

import type {MergeAccessor} from "../merge/MergeAccessor";
import type {FieldMode} from "./fieldMode";

/**
 * What a form holds for every field in it, read with useFormContext(). The fields themselves read it through
 * useField().
 */
export interface FormContext
{
    /** the draft the form edits */
    row: object
    /** its type */
    type: string
    /** its merge state */
    merge: MergeAccessor
    /**
     * The fields whose text didn't parse, by path, holding the text. That text never reaches the draft, which holds
     * values, and lasts as long as the form, so a field that unmounts and comes back still shows it.
     */
    invalid: ReadonlyMap<string, string>
    /**
     * The modes the layout gave fields and setMode() changed them to, by path. A field not in it has the mode its
     * FormField gives, or the default (defaultFieldMode()).
     */
    modes: ReadonlyMap<string, FieldMode>
    /** changes a field's mode */
    setMode(path: string, mode: FieldMode): void
}

/**
 * The text a field holds, and what became of it.
 *
 * @internal
 */
export interface FieldEdit
{
    text: string
    /** the value the text parsed to, undefined where it didn't */
    value: unknown
    /** the parse error, null where the text parsed */
    error: string | null
}

/**
 * The context with what only the fields use.
 *
 * @internal
 */
export interface FormState extends FormContext
{
    /** the text each field was last given, by path */
    edits: ReadonlyMap<string, FieldEdit>
    setEdit(path: string, edit: FieldEdit | null): void
    /** prefix of the fields' element ids */
    idPrefix: string
    /** true where the form renders a layout, rather than the application's own children */
    fromLayout: boolean
}

/** @internal */
export const FormStateContext = createContext<FormState | null>(null)

/**
 * The context of the form the calling component is in.
 *
 * Rules of hooks apply.
 *
 * @throws Error outside a Form
 *
 * @internal
 */
export function useFormState(): FormState
{
    const state = useContext(FormStateContext)
    if (!state)
    {
        throw new Error("A field is only ever in a form. Render it inside <Form/>.")
    }
    return state
}

/**
 * The context of the form the calling component is in: the row, its merge state, the text fields hold that didn't
 * parse, and the fields' modes. What an application's own component in a form reads it all through.
 *
 * Rules of hooks apply.
 *
 * @throws Error outside a Form
 */
export function useFormContext(): FormContext
{
    return useFormState()
}
