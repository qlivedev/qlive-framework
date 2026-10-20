/*
 * PickerInput: the combobox under the pickers, searching a closed set by what its values are called.
 */
import {JSX, KeyboardEvent, useEffect, useRef, useState} from "react";

import config from "../config";
import {and, condition, field as fieldRef, FilterExpression, toJSON, value} from "../FilterDSL";
import i18n from "../i18n";
import {findType, objectFields, unwrapAll} from "../type-utils";
import type {FieldMode} from "./fieldMode";
import {enumLabel, rowName} from "./fieldText";
import {relatedRows} from "./relatedRows";

/** how many choices the list offers at a time */
const PAGE_SIZE = 20

/** how long typing pauses before the rows are asked for, in milliseconds */
const SEARCH_DELAY = 250

/**
 * One entry of a picker's list: the value choosing it sets, what it is called, and the row it stands for, if any.
 *
 * @internal
 */
export interface Choice
{
    value: unknown
    label: string
    row?: any
}

/** what a search found: the first page of choices, and how many there are in all */
interface Found
{
    choices: Choice[]
    count: number
}

/**
 * The condition finding the rows whose name fields contain the text, ignoring case: those of them that are strings.
 */
function searchCondition(type: string, text: string): FilterExpression | null
{
    const trimmed = text.trim()
    const fields = objectFields(type)
    const nameFields = (config().meta.types[type]?.meta?.nameFields ?? [])
        .filter(name => unwrapAll(fields.find(f => f.name === name)!.type).name === "String")
    if (trimmed === "" || !nameFields.length)
    {
        return null
    }
    const terms = nameFields.map(name => condition("containsIgnoreCase", [fieldRef(name), value(trimmed, "String")]))
    return (terms.length === 1 ? terms[0] : condition("or", terms)) as FilterExpression
}

/**
 * The values of an enum type, but the deprecated ones, as choices.
 *
 * @internal
 */
export function enumChoices(type: string): Choice[]
{
    return (findType(type).enumValues ?? [])
        .filter(v => !v.isDeprecated)
        .map(v => ({value: v.name, label: enumLabel(type, v.name)}))
}

/**
 * Rows of a type as choices of their key, called by their names.
 *
 * @internal
 */
export function rowChoices(type: string, key: string, rows: any[]): Choice[]
{
    return rows.map(row => ({value: row[key], label: rowName(type, row, row[key]), row}))
}

/**
 * The choices of a set known in the browser whose label contains the text, ignoring case.
 */
function findKnown(known: Choice[], text: string): Found
{
    const needle = text.trim().toLowerCase()
    const choices = needle === "" ? known : known.filter(choice => choice.label.toLowerCase().includes(needle))
    return {choices: choices.slice(0, PAGE_SIZE), count: choices.length}
}

/**
 * What a PickerInput searches and what it does with a choice.
 *
 * @internal
 */
export interface PickerInputProps
{
    /** the input's id, which the field's label names */
    id: string
    /** the id of the field's label, which names the list too */
    labelId: string
    /** the ids of what describes the input, besides its label */
    describedBy?: string
    mode: FieldMode
    required: boolean
    /** what the input shows while it isn't typed into */
    shown: string
    placeholder?: string
    /** the set, where the browser knows it; null asks the related-rows endpoint for rows of the type */
    known: Choice[] | null
    /** the type of the rows the endpoint is asked for */
    type: string
    /** the field of those rows a choice's value is */
    keyField: string | null
    /** what narrows the rows the endpoint is asked for, besides the text; they are asked for again when it changes */
    narrowed: FilterExpression | null
    onChoose(choice: Choice): void
    /** leaving the input emptied, where emptying means something */
    onEmpty?(): void
}

/**
 * A text input searching a closed set by what its values are called, and the list of the values found, the first
 * page of them. The input shows `shown` while it isn't being typed into, and what the user typed while it is; typed
 * text that chooses nothing is dropped when the input loses focus.
 *
 * A set the browser knows is searched there, by label; any other is asked of the related-rows endpoint once typing
 * pauses, found by the type's String name fields and sorted by its name fields.
 *
 * The list is a listbox named by the field's label. What the list says about itself, that nothing was found or how
 * many more there are, sits below it, outside the listbox, and is announced through a status region that is there
 * from the start.
 *
 * @internal
 */
export function PickerInput(props: PickerInputProps): JSX.Element
{
    const {id, labelId, describedBy, mode, required, shown, placeholder, known, type, keyField, narrowed, onChoose, onEmpty} = props
    const narrowedKey = JSON.stringify(toJSON(narrowed))

    /** what the user typed, null while the input shows `shown` */
    const [typed, setTyped] = useState<string | null>(null)
    const [open, setOpen] = useState(false)
    /** the rows found, null while they are asked for */
    const [fetched, setFetched] = useState<Found | null>(null)
    const [active, setActive] = useState(-1)
    const [failure, setFailure] = useState<string | null>(null)
    const request = useRef(0)

    const editable = mode === "normal"
    const listId = id + "-rows"

    /** what the list shows, null while the rows are asked for */
    const found = known ? (open ? findKnown(known, typed ?? "") : null) : fetched
    const choices = found?.choices ?? []

    function search(text: string)
    {
        const current = ++request.current
        relatedRows(type, {
            condition: and(searchCondition(type, text), narrowed),
            sortFields: config().meta.types[type]?.meta?.nameFields ?? [keyField!],
            offset: 0,
            pageSize: PAGE_SIZE
        })
            .then(answer => {
                if (current === request.current)
                {
                    setFetched({choices: rowChoices(type, keyField!, answer.rows), count: answer.rowCount})
                    setActive(answer.rows.length ? 0 : -1)
                    setFailure(null)
                }
            })
            .catch((e: Error) => {
                if (current === request.current)
                {
                    setFetched({choices: [], count: 0})
                    setFailure(e.message)
                }
            })
    }

    // rows are asked for once typing pauses, and while the list is open
    useEffect(() => {
        if (!open || known)
        {
            return
        }
        const timer = setTimeout(() => search(typed ?? ""), typed === null ? 0 : SEARCH_DELAY)
        return () => clearTimeout(timer)
    }, [open, typed, narrowedKey])

    function close()
    {
        request.current++
        setOpen(false)
        setFetched(null)
        setTyped(null)
        setActive(-1)
    }

    /** opens the list, its first choice active; fetched rows set that again once they are there */
    function openList()
    {
        setOpen(true)
        setActive(0)
    }

    function choose(chosen: Choice)
    {
        onChoose(chosen)
        close()
    }

    /** what leaving the input does with the text typed: empty text is onEmpty's, any other is dropped */
    function leave()
    {
        if (typed !== null && typed.trim() === "")
        {
            onEmpty?.()
        }
        close()
    }

    function onKeyDown(e: KeyboardEvent<HTMLInputElement>)
    {
        switch (e.key)
        {
            case "ArrowDown":
                e.preventDefault()
                if (!open)
                {
                    openList()
                }
                else
                {
                    setActive(index => Math.min(index + 1, choices.length - 1))
                }
                break
            case "ArrowUp":
                e.preventDefault()
                setActive(index => Math.max(index - 1, 0))
                break
            case "Enter":
                if (open && choices[active])
                {
                    e.preventDefault()
                    choose(choices[active])
                }
                else if (typed !== null)
                {
                    e.preventDefault()
                    leave()
                }
                break
            case "Escape":
                if (open || typed !== null)
                {
                    e.preventDefault()
                    close()
                }
                break
        }
    }

    const activeId = open && choices[active] ? listId + "-" + active : undefined

    const note = failure ?? (
        found === null ? i18n("Searching...")
            : choices.length === 0 ? i18n("Nothing found")
                : found.count > choices.length ? i18n("{0} more, type to narrow", String(found.count - choices.length))
                    : null
    )

    return (
        <span className="qlive-picker">
            <input
                id={ id }
                role="combobox"
                autoComplete="off"
                aria-autocomplete="list"
                aria-expanded={ open }
                aria-controls={ open ? listId : undefined }
                aria-describedby={ describedBy }
                aria-activedescendant={ activeId }
                value={ typed ?? shown }
                placeholder={ placeholder }
                required={ required }
                disabled={ mode === "disabled" }
                readOnly={ mode === "readOnly" }
                onChange={ e => {
                    setTyped(e.target.value)
                    openList()
                } }
                onClick={ () => editable && !open && openList() }
                onKeyDown={ editable ? onKeyDown : undefined }
                onBlur={ leave }
            />
            {
                open && (
                    <span
                        className="qlive-picker-popup"
                        // before the input's blur, which would close the list under the click
                        onMouseDown={ e => e.preventDefault() }
                    >
                        <ul id={ listId } className="qlive-picker-rows" role="listbox" aria-labelledby={ labelId }>
                            {
                                choices.map((option, index) => (
                                    <li
                                        key={ String(option.value) }
                                        id={ listId + "-" + index }
                                        role="option"
                                        aria-selected={ index === active }
                                        className={ index === active ? "qlive-picker-active" : undefined }
                                        onClick={ () => choose(option) }
                                    >
                                        { option.label }
                                    </li>
                                ))
                            }
                        </ul>
                        { note !== null && <span className="qlive-picker-note" aria-hidden="true">{ note }</span> }
                    </span>
                )
            }
            <span className="qlive-visually-hidden" role="status">{ open ? note : null }</span>
        </span>
    )
}
