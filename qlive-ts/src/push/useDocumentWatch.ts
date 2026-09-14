import {useEffect, useRef, useSyncExternalStore} from "react";
import {DocumentOrSnapshot, documentOf, QueryDocument} from "../QueryDocument";
import {DocumentWatch, DocumentWatchSnapshot, watchDocument} from "./entityVersion";

/**
 * Subscribes the calling component to other people's writes to the rows of a query document.
 *
 * Takes the document, which for a view is the value beside it: useInjection() returns a snapshot and a
 * snapshot knows the document it was taken of, so the query and its parameters are named in one place and
 * this is not it:
 *
 *     const bars = useInjection(Q_Bar)
 *     const live = useDocumentWatch(bars)
 *
 *     { live.stale && <button onClick={ () => bars.update({}) }>Reload</button> }
 *
 * The document is left alone -- no values travel with a change notification, so there is nothing to
 * apply. What this returns says that a row on screen has changed and which fields of it did; the view
 * decides what that is worth.
 *
 * For rows the user is editing this is the wrong half: a working set holds a draft for them and can say
 * what a change means, so an editing view passes {watch: true} to useWorkingSet() instead. Doing both for
 * the same rows asks to be told twice and refetches the document the drafts stand on.
 *
 * Rules of hooks apply: call it at the top level of a view, unconditionally.
 *
 * @param document  query document to watch, or the snapshot a view holds of one
 *
 * @returns what has changed under the document since it was last read
 */
export function useDocumentWatch(document: DocumentOrSnapshot): DocumentWatchSnapshot
{
    const live = documentOf(document)

    if (!live)
    {
        throw new Error(
            "useDocumentWatch() was given something that is neither a query document nor a snapshot of "
            + "one, and only rows can move."
        )
    }

    // Constructed closed and constructed here rather than in an effect: opening registers a subscription
    // and so belongs in an effect, but the watch itself has to exist before the first render returns,
    // because useSyncExternalStore() reads it. Closed is what makes that safe -- StrictMode renders twice
    // and keeps the second, and a render can be thrown away entirely, and a watch that never opened holds
    // nothing that would have to be closed by the render that no longer exists.
    const held = useRef<{document: QueryDocument<any>, watch: DocumentWatch} | null>(null)

    if (held.current?.document !== live)
    {
        held.current = {document: live, watch: watchDocument(live, {open: false})}
    }

    const {watch} = held.current

    // StrictMode mounts, unmounts and mounts again, so opening has to survive having been closed: without
    // the open() the second mount would leave the view with a watch that hears nothing. A document that
    // was swapped out arrives as a new watch, and the cleanup closes the one it replaced.
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

