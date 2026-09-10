import {useSyncExternalStore} from "react";
import {WorkingSet, WorkingSetSnapshot} from "./WorkingSet";

/**
 * Subscribes the calling component to a working set and returns its current state.
 *
 * A working set is a store like a query document, so this is the same three lines useInjection() is: the
 * component re-renders whenever anything changes -- a field typed into a draft, a row created, a merge
 * coming back with conflicts.
 *
 * Rules of hooks apply: call it at the top level of a view, unconditionally. The working set itself is
 * made outside React and lives as long as the editing does.
 *
 * @param workingSet    the working set to read
 *
 * @returns a snapshot of it: whether it is dirty, what stood in the way of the last merge, and what moves
 *          it on
 */
export function useWorkingSet(workingSet: WorkingSet): WorkingSetSnapshot
{
    return useSyncExternalStore(workingSet.subscribe, workingSet.getSnapshot)
}
