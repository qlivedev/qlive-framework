/*
 * RowPicker: the widget of a to-one relation, choosing the related row by searching its name.
 */
import {JSX, KeyboardEvent, useEffect, useRef, useState} from "react";

import config from "../config";
import {condition, field as fieldRef, FilterExpression, value} from "../FilterDSL";
import i18n from "../i18n";
import {objectFields, unwrapAll} from "../type-utils";
import {fieldText, rowName, targetKey} from "./fieldText";
import {useFormState} from "./FormContext";
import {relatedRows} from "./relatedRows";
import type {WidgetProps} from "./widgets";

/** how many rows the list offers at a time */
const PAGE_SIZE = 20

/** how long typing pauses before the rows are asked for, in milliseconds */
const SEARCH_DELAY = 250

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
 * Picks the row a to-one relation points at: a text input searching the related rows by their name fields, and the
 * list of the rows found, the first page of them. Choosing one sets the foreign key; a nullable key is cleared by
 * emptying the input.
 *
 * The rows come from relatedRows(), the server's related-rows endpoint, sorted by the name fields. The input shows
 * the chosen row's name while it isn't being typed into, and what the user typed while it is; typed text that
 * chooses no row is dropped when the input loses focus.
 *
 * QLive's widget for every to-one relation, so a form edits a foreign key without being told how.
 */
export function RowPicker({field}: WidgetProps): JSX.Element
{
    const {row} = useFormState()
    const key = targetKey(field)
    const type = field.fieldType

    /** what the user typed, null while the input shows the chosen row's name */
    const [typed, setTyped] = useState<string | null>(null)
    const [open, setOpen] = useState(false)
    /** the rows found, null while they are asked for */
    const [rows, setRows] = useState<any[] | null>(null)
    const [rowCount, setRowCount] = useState(0)
    const [active, setActive] = useState(-1)
    const [failure, setFailure] = useState<string | null>(null)
    const request = useRef(0)

    const editable = field.mode === "normal"
    const listId = field.id + "-rows"

    function search(text: string)
    {
        const id = ++request.current
        relatedRows(type, {
            condition: searchCondition(type, text),
            sortFields: config().meta.types[type]?.meta?.nameFields ?? [key],
            offset: 0,
            pageSize: PAGE_SIZE
        })
            .then(found => {
                if (id === request.current)
                {
                    setRows(found.rows)
                    setRowCount(found.rowCount)
                    setActive(found.rows.length ? 0 : -1)
                    setFailure(null)
                }
            })
            .catch((e: Error) => {
                if (id === request.current)
                {
                    setRows([])
                    setRowCount(0)
                    setFailure(e.message)
                }
            })
    }

    // the rows are asked for once typing pauses, and while the list is open
    useEffect(() => {
        if (!open)
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
        setRows(null)
        setTyped(null)
        setActive(-1)
    }

    function choose(chosen: any)
    {
        field.setValue(chosen[key])
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
                    setOpen(true)
                }
                else
                {
                    setActive(index => Math.min(index + 1, (rows?.length ?? 0) - 1))
                }
                break
            case "ArrowUp":
                e.preventDefault()
                setActive(index => Math.max(index - 1, 0))
                break
            case "Enter":
                if (open && active >= 0 && rows?.[active])
                {
                    e.preventDefault()
                    choose(rows[active])
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

    const activeId = open && active >= 0 ? listId + "-" + active : undefined

    const note = failure ?? (
        rows === null ? i18n("Searching...")
            : rows.length === 0 ? i18n("Nothing found")
                : rowCount > rows.length ? i18n("{0} more, type to narrow", String(rowCount - rows.length))
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
                    setOpen(true)
                } }
                onClick={ () => editable && setOpen(true) }
                onKeyDown={ editable ? onKeyDown : undefined }
                onBlur={ leave }
            />
            {
                open && (
                    <ul id={ listId } className="qlive-picker-rows" role="listbox">
                        {
                            (rows ?? []).map((option, index) => (
                                <li
                                    key={ String(option[key]) }
                                    id={ listId + "-" + index }
                                    role="option"
                                    aria-selected={ index === active }
                                    className={ index === active ? "qlive-picker-active" : undefined }
                                    // before the input's blur, which would close the list under the click
                                    onMouseDown={ e => e.preventDefault() }
                                    onClick={ () => choose(option) }
                                >
                                    { rowName(type, option, option[key]) }
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
