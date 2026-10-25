/*
 * The context of a form: what every field in it reads and writes through.
 */
import {createContext, useContext} from "react";

import type {MergeAccessor} from "../merge/MergeAccessor";
import type {ActionDraft} from "../useActionDraft";
import type {FieldMode} from "./fieldMode";

/**
 * What a form holds for every field in it, read with useFormContext(). The fields themselves read it through
 * useField().
 */
export interface FormContext
{
    /** the draft the form edits, null for a form of an action's parameters alone */
    row: object | null
    /** its type, null without a row */
    domainType: string | null
    /** the action whose arguments the form edits too, null for none */
    action: ActionDraft<any> | null
    /** the subject parameter the row fills, null without an action or a row */
    subject: string | null
    /** the name of the layout the form renders, null for a derived layout and a form with children */
    layoutName: string | null
    /** its merge state; without a row, that of no row, where nothing ever happens */
    merge: MergeAccessor
    /**
     * The fields whose text didn't parse, by path, holding the text. That text never reaches the draft, which holds
     * values, and lasts as long as the form, so a field that unmounts and comes back still shows it.
     */
    invalid: ReadonlyMap<string, string>
    /**
     * The modes the layout gave fields and setMode() changed them to, by path. A field not in it has the mode its
     * Field gives, or the default (defaultFieldMode()).
     */
    modes: ReadonlyMap<string, FieldMode>
    /** changes a field's mode */
    setMode(path: string, mode: FieldMode): void
    /**
     * Whether the field holds the value one of the form's reactions set: until the user changes it, and while it is
     * among the row's changes, which the working set's undo() and a save end.
     */
    reacted(path: string): boolean
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
 * What the form's live region says, and how many times it has said something.
 *
 * @internal
 */
export interface Announcement
{
    text: string
    count: number
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
    /** what the live region says */
    announcement: Announcement
    /** tells the form a FormStatus places its live region; returns what tells it the FormStatus is gone */
    registerStatus(): () => void
    /**
     * Tells the form a field is on screen, by the merge's name for it, the id of its label and what it is called
     * where no element has that id; returns what tells it the field is gone. What the live region names.
     */
    registerField(name: string, labelId: string, label: string): () => void
    /** whether a field holds the value a reaction set, given its value and the merge's name for it, null for none */
    fieldReacted(path: string, value: unknown, mergeName: string | null): boolean
    /** tells the form's reactions the form changed a field's value */
    fieldChanged(path: string): void
    /** tells the form's reactions focus left a field */
    fieldBlurred(path: string): void
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
