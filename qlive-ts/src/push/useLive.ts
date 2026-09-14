import {useEffect, useState, useSyncExternalStore} from "react";
import {GraphQLQuery} from "../GraphQLQuery";
import inject, {InjectParams} from "../inject";
import {WorkingSet} from "../merge/WorkingSet";
import {QueryDocument} from "../QueryDocument";
import {DocumentWatchSnapshot, watchDocument, watchWorkingSet} from "./entityVersion";

/**
 * Subscribes the calling component to other people's writes to the rows of an injected query document.
 *
 * The document itself is left alone -- no values travel with a change notification, so there is nothing to
 * apply. What this returns says that a row on screen has changed and which fields of it did; the view
 * decides what that is worth:
 *
 *     const bars = useInjection(Q_Bar)
 *     const live = useLiveRows(Q_Bar)
 *
 *     { live.stale && <button onClick={ () => bars.update({}) }>Reload</button> }
 *
 * For rows the user is editing this is the wrong half: a working set holds a draft for them and can say
 * what a change means, so an editing view calls useLiveWorkingSet() instead. Calling both for the same
 * rows asks to be told twice and refetches the document the drafts stand on.
 *
 * Rules of hooks apply: call it at the top level of a view, unconditionally. The query and the __id are
 * the ones the useInjection() beside it was given -- an injection is read by id, so this finds the same
 * document rather than a second one.
 *
 * @param query     GraphQLQuery the document was injected for
 * @param params    parameters, including __id where the same query is injected twice
 *
 * @returns what has changed under the document since it was last read
 */
export function useLiveRows<T>(query: GraphQLQuery<T>, params: InjectParams = {}): DocumentWatchSnapshot
{
    const value = inject(query, params)

    if (!(value instanceof QueryDocument))
    {
        throw new Error(
            `Injection "${params.__id || query.queryName}" is not a query document, and only rows can move.`
        )
    }

    // Constructed closed: opening registers a subscription, and StrictMode calls this initializer twice
    // while keeping one of the two watches, so a watch that opened here would leave a live one that
    // nothing holds and nothing can close.
    const [watch] = useState(() => watchDocument(value as QueryDocument<unknown>, {open: false}))

    // StrictMode also mounts, unmounts and mounts again, so opening has to survive having been closed:
    // without the open() the second mount would leave the view with a watch that hears nothing.
    useEffect(
        () =>
        {
            watch.open()
            return watch.close
        },
        [watch]
    )

    return useSyncExternalStore(watch.subscribe, watch.getSnapshot)
}


/**
 * Keeps a working set current with other people's writes to the rows it holds, for as long as the calling
 * component is mounted.
 *
 * A field somebody else changed takes the status it would have taken at save time, so the user sees it
 * while they are still editing -- which is the same marking the merge does, arriving early rather than late.
 * Nothing is returned: what changes is the working set, and the useWorkingSet() the view already has is
 * what re-renders it.
 *
 * Rules of hooks apply: call it at the top level of a view, unconditionally.
 *
 * @param workingSet    the working set to keep current
 */
export function useLiveWorkingSet(workingSet: WorkingSet): void
{
    useEffect(() => watchWorkingSet(workingSet), [workingSet])
}
