import {useSyncExternalStore} from "react";
import {MergeAccessor} from "./MergeAccessor";
import {workingSetOf} from "./WorkingSet";

/**
 * Subscribes the calling component to the merge state of one row and returns the accessor for it.
 *
 * One hook per entity, not per field. A hook cannot be called from a loop over a field list, so a
 * useMergeField(row, "name") would only work in a form whose every input is written out by hand -- which is
 * the one kind of form that needs the least help. What comes back is an accessor: field(name) is an
 * ordinary function a generic renderer calls as often as it likes, in a loop, in a callback, or in a child
 * component it handed the accessor to.
 *
 * ```tsx
 * const bar = ws.edit(row)
 * const merge = useMerge(bar)
 *
 * for (const name of fieldNames)
 * {
 *     const f = merge.field(name)
 *     // <input className={ f.className } value={ bar[name] } ... />
 * }
 * ```
 *
 * Rules of hooks apply: call it at the top level of a view, unconditionally.
 *
 * @param draft     a draft, i.e. what ws.edit() or ws.create() returned. A row that is not one is a
 *                  mistake rather than a silent no-op -- it is what would otherwise show up as a form that
 *                  never marks anything
 *
 * @returns the merge state of that row
 */
export function useMerge(draft: object): MergeAccessor
{
    const workingSet = workingSetOf(draft)

    return useSyncExternalStore(workingSet.subscribe, () => workingSet.accessor(draft))
}
