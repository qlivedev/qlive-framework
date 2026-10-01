import type {GraphQLQuery} from "../GraphQLQuery";
import type {QLiveBoostrap} from "../config";
import type {InjectParams} from "../inject";

/**
 * How a view read one of its injections: the query and the parameters it gave useInjection().
 */
export type InjectionNote = {
    query: GraphQLQuery<any>
    params: InjectParams
}

/**
 * What the fixture recorder needs to know about the page and only finds out while the page runs. Kept in dev mode
 * only; see fixture/recorder.ts.
 */
let bootstrap: QLiveBoostrap | null = null

let notes = new Map<string, InjectionNote>()

/**
 * Keeps the bootstrap the page started from, as the server sent it. init() changes the config it is given, so the
 * caller hands in a copy.
 *
 * @param received  copy of the bootstrap as received
 */
export function keepBootstrap(received: QLiveBoostrap): void
{
    bootstrap = received
    notes = new Map()
}

/**
 * Notes the query and parameters a view read an injection with.
 *
 * @param injectionId   injection id
 * @param query         query of the injection
 * @param params        parameters as given to useInjection()
 */
export function noteInjection(injectionId: string, query: GraphQLQuery<any>, params: InjectParams): void
{
    notes.set(injectionId, {query, params})
}

/**
 * The bootstrap the page started from, or null where startup() kept none.
 */
export function keptBootstrap(): QLiveBoostrap | null
{
    return bootstrap
}

/**
 * How the view read the injection with the given id, or undefined where nothing read it.
 *
 * @param injectionId   injection id
 */
export function injectionNote(injectionId: string): InjectionNote | undefined
{
    return notes.get(injectionId)
}
