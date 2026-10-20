/*
 * RowPicker: the widget of a closed set, choosing a value by searching what the values are called.
 */
import {JSX, KeyboardEvent, useEffect, useRef, useState} from "react";

import config from "../config";
import {condition, field as fieldRef, FilterExpression, value} from "../FilterDSL";
import i18n from "../i18n";
import {findType, objectFields, unwrapAll} from "../type-utils";
import {enumLabel, fieldText, rowName, targetKey} from "./fieldText";
import {useFormState} from "./FormContext";
import {relatedRows} from "./relatedRows";
import type {WidgetProps} from "./widgets";

/** how many choices the list offers at a time */
const PAGE_SIZE = 20

/** how long typing pauses before the rows are asked for, in milliseconds */
const SEARCH_DELAY = 250

/** one entry of the list: the value choosing it sets, and what it is called */
interface Choice
{
    value: unknown
    label: string
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
 */
function enumChoices(type: string): Choice[]
{
    return (findType(type).enumValues ?? [])
        .filter(v => !v.isDeprecated)
        .map(v => ({value: v.name, label: enumLabel(type, v.name)}))
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
 * Picks a value of a closed set: a text input searching the set by what its values are called, and the list of the
 * values found, the first page of them. Choosing one sets the field; a nullable field is cleared by emptying the
 * input. The input shows the chosen value's name while it isn't being typed into, and what the user typed while it
 * is; typed text that chooses nothing is dropped when the input loses focus.
 *
 * What the set is depends on the field:
 *
 * - a to-one relation picks a related row and sets the foreign key. The rows come from relatedRows(), the server's
 *   related-rows endpoint, found by their name fields and sorted by them.
 * - an enum field picks one of the type's values, but the deprecated ones, each called by its `Type.VALUE`
 *   translation. The set is known in the browser and searched there.
 *
 * QLive's widget for both, so a form edits a closed set without being told how, and the same way however large the
 * set grows.
 */
export function RowPicker({field}: WidgetProps): JSX.Element
{
    const {row} = useFormState()
    const type = field.fieldType
    const known = field.kind === "scalar" ? enumChoices(type) : null
    const key = known ? null : targetKey(field)

    /** what the user typed, null while the input shows the chosen row's name */
    const [typed, setTyped] = useState<string | null>(null)
    const [open, setOpen] = useState(false)
    /** the rows found, null while they are asked for */
    const [fetched, setFetched] = useState<Found | null>(null)
    const [active, setActive] = useState(-1)
    const [failure, setFailure] = useState<string | null>(null)
    const request = useRef(0)

    const editable = field.mode === "normal"
    const listId = field.id + "-rows"

    /** what the list shows, null while the rows are asked for */
    const found = known ? (open ? findKnown(known, typed ?? "") : null) : fetched
    const choices = found?.choices ?? []

    function search(text: string)
    {
        const id = ++request.current
        relatedRows(type, {
            condition: searchCondition(type, text),
            sortFields: config().meta.types[type]?.meta?.nameFields ?? [key!],
            offset: 0,
            pageSize: PAGE_SIZE
        })
            .then(answer => {
                if (id === request.current)
                {
                    setFetched({
                        choices: answer.rows.map(row => ({value: row[key!], label: rowName(type, row, row[key!])})),
                        count: answer.rowCount
                    })
                    setActive(answer.rows.length ? 0 : -1)
                    setFailure(null)
                }
            })
            .catch((e: Error) => {
                if (id === request.current)
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
    }, [open, typed])

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
        field.setValue(chosen.value)
        close()
    }

    /** what leaving the input does with the text typed: empty text clears a nullable key, any other is dropped */
    function leave()
    {
        if (typed !== null && typed.trim() === "" && !field.required)
        {
            field.setValue(null)
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
                id={ field.id }
                role="combobox"
                autoComplete="off"
                aria-autocomplete="list"
                aria-expanded={ open }
                aria-controls={ listId }
                aria-activedescendant={ activeId }
                value={ typed ?? fieldText(field, row) }
                required={ field.required }
                disabled={ field.mode === "disabled" }
                readOnly={ field.mode === "readOnly" }
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
                    <ul id={ listId } className="qlive-picker-rows" role="listbox">
                        {
                            choices.map((option, index) => (
                                <li
                                    key={ String(option.value) }
                                    id={ listId + "-" + index }
                                    role="option"
                                    aria-selected={ index === active }
                                    className={ index === active ? "qlive-picker-active" : undefined }
                                    // before the input's blur, which would close the list under the click
                                    onMouseDown={ e => e.preventDefault() }
                                    onClick={ () => choose(option) }
                                >
                                    { option.label }
                                </li>
                            ))
                        }
                        { note !== null && <li className="qlive-picker-note" role="presentation">{ note }</li> }
                    </ul>
                )
            }
        </span>
    )
}
