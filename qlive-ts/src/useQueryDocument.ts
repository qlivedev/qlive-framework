import {QueryDocument, QueryDocumentSnapshot} from "./QueryDocument";
import {useSyncExternalStore} from "react";

/**
 * An injection that is not a query document has nothing that could change it, so its
 * store never fires. Module scope, not per call: useSyncExternalStore only resubscribes
 * when the subscribe function changes identity.
 *
 * @internal
 */
const noSubscribe = () => () => {}

/**
 * Subscribes the current component to the given QueryDocument and returns the current snapshot.
 *
 * @param document
 */
export default function useQueryDocument<T>(document: QueryDocument<T> | null) : T
{
    return useSyncExternalStore<unknown>(
        document ? document.subscribe : noSubscribe,
        // identity-stable: the injection is read once and cached, so a value that is not
        // a document is the same object on every render
        document ? document.getSnapshot : () => null
    ) as T
}
