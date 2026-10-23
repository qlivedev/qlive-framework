/*
 * Form: the provider of a form's context, and the renderer of its layout.
 */
import {
    Fragment,
    JSX,
    ReactNode,
    useCallback,
    useEffect,
    useId,
    useMemo,
    useRef,
    useState,
    useSyncExternalStore
} from "react";

import {actionParameters} from "../action";
import i18n from "../i18n";
import {MergeAccessor, untouchedField} from "../merge/MergeAccessor";
import {workingSetOf} from "../merge/WorkingSet";
import type {ActionDraft} from "../useActionDraft";
import {primaryKeyFields} from "../type-utils";
import {isViteDev} from "../util/viteEnv";
import {Field} from "./Field";
import type {FieldMode} from "./fieldMode";
import {rowName} from "./fieldText";
import {Announcement, FieldEdit, FormState, FormStateContext} from "./FormContext";
import {FormStatus, StatusRegion} from "./FormStatus";
import {deriveLayout, Layout, LayoutNode, namedComponent, namedLayout} from "./layout";

/**
 * Props of Form.
 */
export interface FormProps
{
    /**
     * the draft the form edits, what `ws.edit(row)` or `ws.create(type)` returned; may be left out in a form with an
     * action, which then edits the action's parameters alone
     */
    row?: object
    /** the action draft, from useActionDraft(), whose arguments the form edits too */
    action?: ActionDraft<any>
    /**
     * the subject parameter of the action the row fills; may be left out where the action has exactly one subject,
     * which is not a list
     */
    subject?: string
    /**
     * what the form renders, or the name of one in `config().layouts` (namedLayout()); derived from the row without
     * either
     */
    layout?: Layout | string
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
        else if ("nodes" in node && node.nodes)
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
        if ("status" in node)
        {
            return <FormStatus key={ index }/>
        }
        if ("section" in node)
        {
            return (
                <fieldset key={ index } className="qlive-form-section">
                    <legend>{ node.section }</legend>
                    { renderNodes(node.nodes) }
                </fieldset>
            )
        }
        const Component = typeof node.component === "string" ? namedComponent(node.component) : node.component
        return <Component key={ index } { ...node.props }>{ node.nodes && renderNodes(node.nodes) }</Component>
    })
}

/** the merge state of a form without a row */
const NO_ROW: MergeAccessor = {
    type: "",
    id: "",
    gone: false,
    isNew: false,
    deleted: false,
    field: name => untouchedField(name, undefined),
    changedFields: () => [],
    conflictedFields: () => [],
    resolvedFields: () => [],
    remoteChangedFields: () => [],
    of: () => {
        throw new Error("A form without a row has no working set.")
    }
}

const noSubscription = () => () => {}

/**
 * useMerge() for a form that may have no row.
 */
function useFormMerge(row: object | null): MergeAccessor
{
    const workingSet = row ? workingSetOf(row) : null
    return useSyncExternalStore(
        workingSet ? workingSet.subscribe : noSubscription,
        () => workingSet ? workingSet.accessor(row!) : NO_ROW
    )
}

/**
 * The subject parameter a form's row fills: the one given, or the action's one subject.
 */
function subjectOf(action: ActionDraft<any> | null, row: object | null, given: string | undefined): string | null
{
    if (!action || !row)
    {
        if (given)
        {
            throw new Error("A form fills subject '" + given + "' with its row, and needs a row and an action.")
        }
        return null
    }

    const subjects = actionParameters(action.name).filter(parameter => parameter.subject)
    const subject = given !== undefined
        ? subjects.find(parameter => parameter.name === given)
        : subjects.length === 1 ? subjects[0] : undefined
    if (!subject)
    {
        throw new Error(
            "Action '" + action.name + "' " + (
                given !== undefined
                    ? "has no subject '" + given + "'."
                    : "has " + (subjects.length ? "the subjects " + subjects.map(p => p.name).join(", ") +
                        ", so the form names the one its row fills: subject=\"...\"." : "no subject for a row.")
            )
        )
    }
    if (subject.list)
    {
        throw new Error(
            "Subject '" + subject.name + "' of action '" + action.name + "' is a list, which the view sets; a form " +
            "fills one row."
        )
    }
    return subject.name
}

/** the fields of the row somebody else changed, and those both changed, as of one render */
interface Marked
{
    row: object
    remote: Set<string>
    conflict: Set<string>
}

/** the fields on screen, by the merge's name: what each is called where its label isn't found, by label id */
type ShownFields = Map<string, Map<string, string>>

/**
 * What a field on screen is called: the text of its label, else the label it was given or its translation.
 */
function shownLabel(labels: Map<string, string>): string
{
    const [labelId, label] = labels.entries().next().value!
    return document.getElementById(labelId)?.textContent || label
}

/**
 * What the form's live region says about the fields on screen somebody else changed since the last render, and those
 * a save left in conflict, each called by its label, and in which row, by its name fields as the form shows them: a
 * page of several forms has as many regions, and only the name tells them apart. null for nothing new. A row's fields
 * marked when the form first shows it are no news.
 */
function newlyMarked(before: Marked | null, now: Marked, shown: ShownFields, type: string): string | null
{
    if (!before || before.row !== now.row)
    {
        return null
    }
    const named = (names: string[]) => names.map(name => shownLabel(shown.get(name)!)).join(", ")
    const conflict = [...now.conflict].filter(name => !before.conflict.has(name) && shown.has(name))
    const remote = [...now.remote].filter(name => !before.remote.has(name) && shown.has(name))
    if (!conflict.length && !remote.length)
    {
        return null
    }
    const row = now.row as any
    const name = rowName(type, row, primaryKeyFields(type).map(key => row[key]).join(" "))
    const parts = []
    if (conflict.length)
    {
        parts.push(i18n("Conflict in {0}: {1}", name, named(conflict)))
    }
    if (remote.length)
    {
        parts.push(i18n("Changed by other in {0}: {1}", name, named(remote)))
    }
    return parts.join(" ")
}

function markedOf(row: object, merge: MergeAccessor): Marked
{
    return {row, remote: new Set(merge.remoteChangedFields()), conflict: new Set(merge.conflictedFields())}
}

/**
 * A form editing one row: the context of its fields, and what renders them.
 *
 * ```tsx
 * <Form row={ ws.edit(bar) }/>                          // the layout derived from the row
 * <Form row={ ws.edit(bar) } layout={ barLayout }/>     // a layout
 * <Form row={ ws.edit(bar) } layout="barEdit"/>         // a layout by name, from config().layouts
 * <Form row={ ws.edit(bar) }>                           // the application's own fields
 *     <Field path="name"/>
 *     <Field path="owner"/>
 * </Form>
 * ```
 *
 * One form edits one row: its type and merge state are the working set's for the draft. A screen editing several
 * rows has several forms.
 *
 * Given an action draft (useActionDraft()), the form edits the action's arguments too, and its row is one of them,
 * the subject it names or the action's one subject:
 *
 * ```tsx
 * const approve = useActionDraft("approveBar")
 *
 * <Form row={ ws.edit(bar) } action={ approve }/>      // the row's fields and the action's input
 * <Form action={ approve }>                            // the action's parameters alone
 *     <Field path="comment"/>
 * </Form>
 * ```
 *
 * A path whose first segment names a parameter is that parameter's, `comment` or `address.street`; any other is a
 * field of the row (see useField()). Each field shows the refusals of the last issue that blame it; ActionRefusals
 * shows those no field takes.
 *
 * Without children, the form renders a layout, the one given or the one derived from the row (deriveLayout()), and
 * wraps every widget in the conflict buttons (ConflictResolution). With children, it only provides the context, and
 * the application's fields and components render inside it as they are; useFormContext() reads it.
 *
 * Either way the form has a live region telling a screen reader what somebody else changed (FormStatus): at its top,
 * unless a FormStatus inside it places it. That region is the one markup a form with children adds of its own.
 *
 * @throws Error for a form given both a layout and children, for a layout name config().layouts doesn't have, and for
 *         a layout of another type than the row's
 */
export function Form({row: givenRow, action: givenAction, subject: givenSubject, layout, children}: FormProps): JSX.Element
{
    const row = givenRow ?? null
    const action = givenAction ?? null
    if (!row && !action)
    {
        throw new Error("A form edits a row, an action's parameters, or both, and was given neither.")
    }
    const merge = useFormMerge(row)
    const type = row ? merge.type : null
    if (layout && children !== undefined)
    {
        throw new Error("A form renders a layout or its children, not both.")
    }
    const subject = subjectOf(action, row, givenSubject)

    // the row is the argument of the subject it fills, for as long as the form shows it
    const set = action?.set as ((parameter: string, value: unknown) => void) | undefined
    useEffect(() => {
        if (set && subject)
        {
            set(subject, row)
            return () => set(subject, null)
        }
    }, [set, subject, row])

    const fromLayout = children === undefined
    const actionName = action?.name
    const resolvedLayout = useMemo(
        () => fromLayout
            ? (typeof layout === "string" ? namedLayout(layout) : layout) ?? deriveLayout(type, row, actionName)
            : null,
        [fromLayout, layout, type, row, actionName]
    )
    if (resolvedLayout && resolvedLayout.domainType !== type)
    {
        throw new Error("A layout of " + resolvedLayout.domainType + " can't render a " + type + ".")
    }
    const [modes, setModes] = useState(() => resolvedLayout ? layoutModes(resolvedLayout.nodes) : new Map())
    const [edits, setEdits] = useState<ReadonlyMap<string, FieldEdit>>(() => new Map())
    const idPrefix = useId() + "-"

    const [announcement, setAnnouncement] = useState<Announcement>({text: "", count: 0})
    const marked = useRef<Marked | null>(null)
    const shown = useRef<ShownFields>(new Map())
    const registerField = useCallback((name: string, labelId: string, label: string) => {
        const labels = shown.current.get(name) ?? new Map<string, string>()
        shown.current.set(name, labels.set(labelId, label))
        return () => {
            labels.delete(labelId)
            if (labels.size === 0 && shown.current.get(name) === labels)
            {
                shown.current.delete(name)
            }
        }
    }, [])
    useEffect(() => {
        if (!row || !type)
        {
            return
        }
        const now = markedOf(row, merge)
        const text = newlyMarked(marked.current, now, shown.current, type)
        marked.current = now
        if (text !== null)
        {
            setAnnouncement(current => ({text, count: current.count + 1}))
        }
    }, [row, merge, type])

    // how many FormStatus place the region; while none does, the form renders it at its top
    const placed = useRef(0)
    const [statusPlaced, setStatusPlaced] = useState(false)
    const registerStatus = useCallback(() => {
        placed.current++
        if (placed.current > 1 && isViteDev())
        {
            console.warn("A form has more than one FormStatus, and announces everything as often.")
        }
        setStatusPlaced(true)
        return () => {
            placed.current--
            if (placed.current === 0)
            {
                setStatusPlaced(false)
            }
        }
    }, [])

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
        return {
            row, domainType: type, action, subject, layoutName, merge, invalid, modes, setMode, edits, setEdit,
            idPrefix, fromLayout, announcement, registerStatus, registerField
        }
    }, [
        row, type, action, subject, resolvedLayout, merge, modes, setMode, edits, setEdit, idPrefix, fromLayout,
        announcement, registerStatus, registerField
    ])

    const status = !statusPlaced && <StatusRegion announcement={ announcement }/>
    return (
        <FormStateContext.Provider value={ state }>
            {
                resolvedLayout
                    ? <div className="qlive-form">{ status }{ renderNodes(resolvedLayout.nodes) }</div>
                    : <Fragment>{ status }{ children }</Fragment>
            }
        </FormStateContext.Provider>
    )
}
