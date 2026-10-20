/*
 * Form: the provider of a form's context, and the renderer of its layout.
 */
import {Fragment, JSX, ReactNode, useCallback, useId, useMemo, useState} from "react";

import i18n from "../i18n";
import {useMerge} from "../merge/useMerge";
import {Field} from "./Field";
import type {FieldMode} from "./fieldMode";
import {FieldEdit, FormState, FormStateContext} from "./FormContext";
import {deriveLayout, Layout, LayoutNode, namedComponent} from "./layout";

/**
 * Props of Form.
 */
export interface FormProps
{
    /** the draft the form edits, what `ws.edit(row)` or `ws.create(type)` returned */
    row: object
    /** what the form renders, derived from the row without it */
    layout?: Layout
    /** the application's own fields, in place of a layout */
    children?: ReactNode
}

/** the modes a layout's field nodes give */
function layoutModes(nodes: LayoutNode[], modes: Map<string, FieldMode> = new Map()): Map<string, FieldMode>
{
    for (const node of nodes)
    {
        if ("field" in node)
        {
            if (node.mode)
            {
                modes.set(node.field, node.mode)
            }
        }
        else if (node.nodes)
        {
            layoutModes(node.nodes, modes)
        }
    }
    return modes
}

function renderNodes(nodes: LayoutNode[]): ReactNode
{
    return nodes.map((node, index) => {
        if ("field" in node)
        {
            return (
                <Field
                    key={ node.field }
                    path={ node.field }
                    widget={ node.widget }
                    mode={ node.mode }
                    label={ node.label }
                    condition={ node.condition }
                />
            )
        }
        if ("section" in node)
        {
            return (
                <fieldset key={ index } className="qlive-form-section">
                    <legend>{ i18n(node.section) }</legend>
                    { renderNodes(node.nodes) }
                </fieldset>
            )
        }
        const Component = typeof node.component === "string" ? namedComponent(node.component) : node.component
        return <Component key={ index } { ...node.props }>{ node.nodes && renderNodes(node.nodes) }</Component>
    })
}

/**
 * A form editing one row: the context of its fields, and what renders them.
 *
 * ```tsx
 * <Form row={ ws.edit(bar) }/>                          // the layout derived from the row
 * <Form row={ ws.edit(bar) } layout={ barLayout }/>     // a layout
 * <Form row={ ws.edit(bar) }>                           // the application's own fields
 *     <Field path="name"/>
 *     <Field path="owner"/>
 * </Form>
 * ```
 *
 * One form edits one row: its type and merge state are the working set's for the draft. A screen editing several
 * rows has several forms.
 *
 * Without children, the form renders a layout, the one given or the one derived from the row (deriveLayout()), and
 * wraps every widget in the conflict buttons (ConflictResolution). With children, it only provides the context, and
 * the application's fields and components render inside it as they are; useFormContext() reads it.
 *
 * @throws Error for a form given both a layout and children, and for a layout of another type than the row's
 */
export function Form({row, layout, children}: FormProps): JSX.Element
{
    const merge = useMerge(row)
    const type = merge.type
    if (layout && children !== undefined)
    {
        throw new Error("A form renders a layout or its children, not both.")
    }
    if (layout && layout.domainType !== type)
    {
        throw new Error("A layout of " + layout.domainType + " can't render a " + type + ".")
    }

    const fromLayout = children === undefined
    const resolvedLayout = useMemo(
        () => fromLayout ? layout ?? deriveLayout(type, row) : null,
        [fromLayout, layout, type, row]
    )
    const [modes, setModes] = useState(() => resolvedLayout ? layoutModes(resolvedLayout.nodes) : new Map())
    const [edits, setEdits] = useState<ReadonlyMap<string, FieldEdit>>(() => new Map())
    const idPrefix = useId() + "-"

    const setMode = useCallback((path: string, mode: FieldMode) => {
        setModes(current => new Map(current).set(path, mode))
    }, [])
    const setEdit = useCallback((path: string, edit: FieldEdit | null) => {
        setEdits(current => {
            if (!edit && !current.has(path))
            {
                return current
            }
            const next = new Map(current)
            edit ? next.set(path, edit) : next.delete(path)
            return next
        })
    }, [])

    const state = useMemo((): FormState => {
        const invalid = new Map<string, string>()
        for (const [path, edit] of edits)
        {
            if (edit.error !== null)
            {
                invalid.set(path, edit.text)
            }
        }
        const layoutName = resolvedLayout?.name ?? null
        return {row, domainType: type, layoutName, merge, invalid, modes, setMode, edits, setEdit, idPrefix, fromLayout}
    }, [row, type, resolvedLayout, merge, modes, setMode, edits, setEdit, idPrefix, fromLayout])

    return (
        <FormStateContext.Provider value={ state }>
            {
                resolvedLayout
                    ? <div className="qlive-form">{ renderNodes(resolvedLayout.nodes) }</div>
                    : <Fragment>{ children }</Fragment>
            }
        </FormStateContext.Provider>
    )
}
