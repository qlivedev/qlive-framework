import {useSyncExternalStore} from "react";
import {GraphQLQuery} from "./GraphQLQuery";
import inject, {InjectParams} from "./inject";
import {QueryDocument} from "./QueryDocument";
import useQueryDocument from "./useQueryDocument";


/**
 * Reads the data the server injected for the given query and subscribes the calling
 * component to it.
 *
 * Each query document is its own store: what this returns is a snapshot of it, and every
 * component that got the same injection re-renders with a new snapshot when anything --
 * an update() of that document, most of all -- changes it.
 *
 * The injection itself has already been prepared on the server side by the time this runs.
 * We use static analysis to find the useInjection() calls in the code base and ship the
 * data each view needs with the view.
 *
 * Rules of hooks apply: call it at the top level of a view, unconditionally.
 *
 * @param query     GraphQLQuery the data was injected for
 * @param params    parameters, including __id where the same query is injected twice
 *
 * @returns the injected value; a snapshot of it where it is a query document
 */
export function useInjection<T>(query: GraphQLQuery<T>, params: InjectParams = {}): T
{
    const value = inject(query, params)
    const document = value instanceof QueryDocument ? value as QueryDocument<unknown> : null

    return useQueryDocument(document) as T
}
