import {useEffect, useRef, useSyncExternalStore} from "react";

import {conditionPredicate} from "../evaluate";
import {conditionsEqual, FilterExpression} from "../FilterDSL";
import {fieldClassName, MergeAccessor} from "../merge/MergeAccessor";
import {WorkingSet} from "../merge/WorkingSet";
import {watchWorkingSet} from "../push/entityVersion";
import {useWatch} from "../push/useDocumentWatch";
import {DocumentOrSnapshot, documentOf} from "../QueryDocument";
import {PageableDocument} from "./usePagination";

/**
 * What has happened to a row, the most pressing first:
 *
 * - `gone` -- somebody else deleted it
 * - `deleted` -- marked for deletion, deleted by the next merge
 * - `conflict` -- a field of it both writes changed, and nobody decided which value wins
 * - `new` -- created in the working set and not saved yet
 * - `changed` -- changed in the working set
 * - `remoteChanged` -- somebody else changed it, and the working set's user didn't
 * - `unchanged` -- none of that
 */
export type GridRowStatus = "gone" | "deleted" | "conflict" | "new" | "changed" | "remoteChanged" | "unchanged";

/**
 * Options for useGridRows,
 */
export interface GridRowsOptions
{
    /**
     * The working set the rows are edited in. The rows are then its drafts, so unsaved edits show in the list, and
     * the rows created in it show on the first page, those the document's condition matches.
     */
    workingSet?: WorkingSet | null;

    /**
     * Whether other people's writes to the rows show as they happen: through the working set if there is one, see
     * `useWorkingSet()`, through `useDocumentWatch()` on the document otherwise. Default false.
     */
    watch?: boolean;
}

/**
 * The rows of a list and what has happened to them.
 *
 * @typeParam R     row type
 */
export interface GridRows<R>
{
    /**
     * The rows to show. Without a working set, the document's rows. With one, the drafts of them, preceded on the
     * first page by the rows created in it that the document's condition matched when it was set, and the rows
     * created since.
     */
    rows: readonly R[];

    /** what has happened to a row */
    status(row: R): GridRowStatus;

    /**
     * What has happened to a field of a row, as the class a cell or input carries (see `MergeField.className`),
     * empty for an unchanged field. A new row's fields carry none: the row says it all.
     */
    fieldClass(row: R, field: string): string;

    /**
     * true once somebody else changed a row shown, when watching the document rather than a working set. The
     * document's `update({})` reads the rows again.
     */
    stale: boolean;
}

const unsubscribed = () => () => {};

const noWorkingSet = () => null;

/**
 * Status of a row from its merge state.
 */
function rowStatus(merge: MergeAccessor): GridRowStatus
{
    if (merge.gone)
    {
        return "gone";
    }
    if (merge.deleted)
    {
        return "deleted";
    }
    if (merge.conflictedFields().length > 0)
    {
        return "conflict";
    }
    if (merge.isNew)
    {
        return "new";
    }
    if (merge.changedFields().length > 0)
    {
        return "changed";
    }
    return merge.remoteChangedFields().length > 0 ? "remoteChanged" : "unchanged";
}

/**
 * The rows a list shows, and what has happened to each of them and their fields.
 *
 *     const {rows, status, fieldClass} = useGridRows(foos, {workingSet: ws, watch: true})
 *
 * Without a working set the rows are the document's own. With one they are its drafts, so a row being edited
 * elsewhere, in a detail pane or a dialog, shows the edits before they are saved; the rows created in the working
 * set are listed first on the first page. Which of them is decided the way a query decides which rows it returns:
 * when the condition changes, the created rows it matches then show, evaluated in the browser as
 * conditionPredicate() has it, and so does every row created after. A row created under a filter shows although it
 * is still empty, and neither it nor a row of the query leaves the list because an edit made it stop matching.
 * The document is registered with the working set here, so its rows can be edited without doing that first.
 *
 * The calling component re-renders with every change to the working set. DataGrid is this plus markup; a table of
 * its own gets the same through this hook.
 *
 * Rules of hooks apply: call it at the top level of a view, unconditionally.
 *
 * @param doc       query document snapshot
 * @param options   working set and watching, see GridRowsOptions
 */
export function useGridRows<R>(
    doc: PageableDocument & { rows: readonly R[] },
    options: GridRowsOptions = {}
): GridRows<R>
{
    const {workingSet = null, watch = false} = options;

    const live = documentOf(doc);

    // Registered in render the first time, since the drafts below need it; the working set finds the rows of a
    // later page by itself as they are edited. Registered again from an effect once the rows have changed, which
    // tells the working set's subscribers -- a watch among them -- about rows it hasn't held before.
    const registered = useRef<{ workingSet: WorkingSet, document: object } | null>(null);
    if (workingSet && (registered.current?.workingSet !== workingSet || registered.current.document !== (live ?? doc)))
    {
        workingSet.register(doc as unknown as DocumentOrSnapshot);
        registered.current = {workingSet, document: live ?? doc};
    }

    useEffect(
        () => workingSet?.register(doc as unknown as DocumentOrSnapshot),
        [workingSet, doc.rows]
    );

    useSyncExternalStore(workingSet?.subscribe ?? unsubscribed, workingSet?.getSnapshot ?? noWorkingSet);

    useEffect(
        () => workingSet && watch ? watchWorkingSet(workingSet) : undefined,
        [workingSet, watch]
    );

    const watched = useWatch(!workingSet && watch ? live : null);

    const excluded = useRef<{ workingSet: WorkingSet, condition: FilterExpression | null, rows: Set<object> } | null>(null);

    if (!workingSet)
    {
        const changed = new Map<string, string[]>();
        for (const row of watched?.remoteChanged ?? [])
        {
            if (row.type === doc.type)
            {
                changed.set(row.id, row.fields);
            }
        }
        const fieldsOf = (row: R) => changed.get((row as any).id);

        return {
            rows: doc.rows,
            status: row => fieldsOf(row) ? "remoteChanged" : "unchanged",
            fieldClass: (row, field) => fieldsOf(row)?.includes(field) ? fieldClassName("remoteChanged") : "",
            stale: watched?.stale ?? false
        };
    }

    // the created rows the condition didn't match when it was set, per working set and condition
    if (!excluded.current || excluded.current.workingSet !== workingSet ||
        !conditionsEqual(excluded.current.condition, doc.config.condition))
    {
        const matches = conditionPredicate(doc.type, doc.config.condition);
        excluded.current = {
            workingSet,
            condition: doc.config.condition,
            rows: new Set(workingSet.created<R & object>(doc.type).filter(row => !matches(row)))
        };
    }
    const hidden = excluded.current.rows;

    const created: R[] = doc.config.offset === 0
        ? workingSet.created<R & object>(doc.type).filter(row => !hidden.has(row))
        : [];

    return {
        rows: [...created, ...doc.rows.map(row => workingSet.edit(row as R & object))],
        status: row => rowStatus(workingSet.accessor(row as object)),
        fieldClass: (row, field) => {
            const merge = workingSet.accessor(row as object);
            return merge.isNew ? "" : merge.field(field).className;
        },
        stale: false
    };
}
