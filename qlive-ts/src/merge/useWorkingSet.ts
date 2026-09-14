import {useEffect, useSyncExternalStore} from "react";
import {watchWorkingSet} from "../push/entityVersion";
import {WorkingSet, WorkingSetSnapshot} from "./WorkingSet";

/**
 * How a view reads its working set.
 */
export type UseWorkingSetOptions = {

    /**
     * Whether other people's writes to the rows this working set holds land in it as they happen.
     *
     * A field somebody else changed then takes the status it would have taken at save time, so the user
     * sees it while they are still editing -- the same marking the merge does, arriving early rather than
     * late. Nothing else changes: the merge is still what detects a conflict and still the authority on
     * the version, so a working set read without this is a working set that works, and finds out at save
     * time. Defaults to false, watching costing a subscription and being a choice a form makes.
     *
     * A view that sets this must not also call useDocumentWatch() on the document these rows came from.
     * That is the same news twice, and acting on the document half means update(), which swaps out the row
     * objects the drafts are standing on.
     */
    watch?: boolean
}

/**
 * Subscribes the calling component to a working set and returns its current state.
 *
 * A working set is a store like a query document, so this is the same three lines useInjection() is: the
 * component re-renders whenever anything changes -- a field typed into a draft, a row created, a merge
 * coming back with conflicts.
 *
 *     const { dirty, conflicts, merge } = useWorkingSet(ws, {watch: true})
 *
 * Rules of hooks apply: call it at the top level of a view, unconditionally. The working set itself is
 * made outside React and lives as long as the editing does. The flag may be turned on and off freely; the
 * watch follows it.
 *
 * @param workingSet    the working set to read
 * @param options       see UseWorkingSetOptions
 *
 * @returns a snapshot of it: whether it is dirty, what stood in the way of the last merge, and what moves
 *          it on
 */
export function useWorkingSet(workingSet: WorkingSet, options: UseWorkingSetOptions = {}): WorkingSetSnapshot
{
    const watch = options.watch === true

    // Nothing is returned by watching: what changes is the working set, and the subscription below is what
    // re-renders for it. Watched by however many components ask -- they share one subscription.
    useEffect(
        () => watch ? watchWorkingSet(workingSet) : undefined,
        [workingSet, watch]
    )

    return useSyncExternalStore(workingSet.subscribe, workingSet.getSnapshot)
}
