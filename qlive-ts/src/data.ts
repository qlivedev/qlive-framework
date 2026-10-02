import {Injection, InjectionSource} from "./config";

/**
 * The injections as the server shipped them, keyed by injection id. Kept as received:
 * converting them needs the selections of the query the injection was produced from,
 * and that query is only known once a view actually asks for the injection with it.
 */
let injectionSources: { [key: string]: InjectionSource }

/**
 * The injections inject() has converted, keyed by injection id. An id present here has
 * been claimed by a view with its query, an id only in injectionSources has not -- which
 * is what keeps "not converted yet" from being a guess.
 */
let injections: { [key: string]: Injection }

export function initData(data : { [key: string]: InjectionSource })
{
    // a copy: addData() adds to it, and the object is the caller's -- a bootstrap, or a fixture still in use
    injectionSources = {...data}
    // A new page brings new injections. What the last one converted belongs to the data
    // it was converted from, so it goes with it instead of shadowing an id that repeats.
    injections = {}
}

/**
 * Adds the given injections to the ones the page holds, keeping those already there. What
 * addFixture() loads a fixture with, next to the fixtures loaded before it.
 *
 * Injection ids carry the route of their view, so injections of different views don't
 * meet. An id that is already there has to come with the same data: two fixtures for one
 * route, recorded at different times, would otherwise give one view the other's data.
 *
 * @param data      injections by injection id, as a bootstrap or fixture carries them
 */
export function addData(data: { [key: string]: InjectionSource })
{
    if (!injectionSources)
    {
        initData({})
    }

    for (const [injectionId, source] of Object.entries(data ?? {}))
    {
        const present = injectionSources[injectionId]
        if (present && !sameJSON(present, source))
        {
            throw new Error(
                "The page already holds the injection '" + injectionId + "' with other data. Two fixtures " +
                "for the same route can't share a page: each view reads its injections by its route."
            )
        }
        injectionSources[injectionId] = source
    }
}

/**
 * Whether two values parsed from JSON are equal, key order aside.
 */
function sameJSON(a: unknown, b: unknown): boolean
{
    if (a === b)
    {
        return true
    }
    if (typeof a !== "object" || typeof b !== "object" || a === null || b === null)
    {
        return false
    }
    if (Array.isArray(a) !== Array.isArray(b))
    {
        return false
    }

    const aKeys = Object.keys(a)
    const bKeys = Object.keys(b)
    return aKeys.length === bKeys.length && aKeys.every(
        key => Object.hasOwn(b, key) && sameJSON((a as Record<string, unknown>)[key], (b as Record<string, unknown>)[key])
    )
}

function requireInit()
{
    if (!injectionSources)
    {
        throw new Error("Injected data not initialized")
    }
}

/**
 * Returns the injection the server shipped under the given id, as received, or undefined
 * if the page came with no injection for that id.
 *
 * This is the raw GraphQL result: scalars are still in their wire format and query
 * documents are still plain objects. inject() is what turns one of these into an
 * Injection.
 *
 * @param injectionId   injection id with its route, e.g. "grid/sorting/Q_FooList"
 */
export function injectionSource(injectionId : string): InjectionSource | undefined
{
    requireInit()

    return injectionSources[injectionId]
}

/**
 * Stores the injection converted for the given id, so that every later read of that id
 * gets the same value. Called by inject(), which is the only place a conversion happens.
 *
 * @param injectionId   injection id with its route, e.g. "grid/sorting/Q_FooList"
 * @param injection     the converted injection
 */
export function storeInjection(injectionId : string, injection : Injection)
{
    requireInit()

    injections[injectionId] = injection
}

/**
 * Returns the converted injection for the given id, with the type and meta information it
 * came with, or undefined while no view has read that id yet.
 *
 * useInjection() is the normal way to read injected data; this is for the cases that need
 * the type or the meta alongside the value. An id the page shipped but no view has claimed
 * is undefined here and readable with injectionSource().
 *
 * @param injectionId   injection id with its route, e.g. "grid/sorting/Q_FooList"
 */
export default function data(injectionId : string): Injection | undefined
{
    requireInit()

    return injections[injectionId]
}
