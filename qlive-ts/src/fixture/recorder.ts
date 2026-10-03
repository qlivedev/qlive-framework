import config, {QLiveFixture} from "../config";
import {convertVariablesToServer} from "../converter";
import graphql, {firstValue} from "../util/graphql";
import {injectionNote, keptBootstrap} from "./notes";
import {routeOf} from "../router";

/*
 * The fixture recorder: dev tooling that saves the data of the view on screen as a fixture, see initFixture().
 *
 * startup() loads this module in dev mode only, with a dynamic import, so a production build never fetches it.
 */

/**
 * Records the view on screen as a fixture: the bootstrap it started from, with every query document injection queried
 * again for all its rows.
 *
 * The query and parameters come from the view's own useInjection() calls, so the view has to have rendered. A
 * document comes back with all rows unless its type sets a maxPageSize the result is larger than. Then the fixture
 * holds the rows that came back, first in the injected sort order, as if they were all there are: the view opens on
 * the same first page, and pages, filters and sorts within those. The description says where that happened, and so
 * does a warning on the console.
 *
 * The fixture carries the authentication of whoever records it: record as a demo login, not a personal one.
 *
 * The schema's descriptions are dropped: a quarter of the schema that only DomainTables shows.
 *
 * The fixture's description says when, where and as whom it was recorded, and how many rows each document holds --
 * "2 of 3 Foo rows" where maxPageSize cut it short.
 *
 * @returns the fixture
 */
export async function recordFixture(): Promise<QLiveFixture>
{
    const bootstrap = keptBootstrap()
    if (!bootstrap)
    {
        throw new Error("No bootstrap was kept to record a fixture from: the page was not started by startup() in dev mode")
    }

    const route = routeOf(location.pathname)
    const fixture: QLiveFixture = {description: "", ...structuredClone(bootstrap), route}
    // a token for a session that will be long gone by the time the fixture is used
    fixture.csrfToken = {...fixture.csrfToken, value: ""}
    if (fixture.config)
    {
        stripDescriptions(fixture.config.schema)
    }

    const queryDocumentTypes = config().queryDocumentTypes!
    const documents: string[] = []
    for (const [injectionId, source] of Object.entries(fixture.data))
    {
        if (!queryDocumentTypes.has(source.type))
        {
            continue
        }

        const note = injectionNote(injectionId)
        if (!note)
        {
            throw new Error(
                "No view read the injection '" + injectionId + "', so there is no query to fetch all its rows with"
            )
        }

        const {query, params: {__id, ...params}} = note

        // the variable taking the config, under whatever name the query gives it
        const variables = query.conversionMap.variables ?? {}
        const configVariable = Object.keys(variables).find(name => variables[name] === "QueryConfig")
        if (!configVariable)
        {
            throw new Error("Query " + query.queryName + " of the injection '" + injectionId + "' takes no QueryConfig")
        }

        const key = Object.keys(source.data)[0]
        const document = source.data[key]

        const result = await graphql<any>(query, {
            ...convertVariablesToServer(params, query.conversionMap),
            // The config the server answered with, which has the type's defaults in it, not the delta the view gave --
            // unless the query selects none, which leaves the delta.
            [configVariable]: {...(document.config ?? params[configVariable]), offset: 0, pageSize: 0}
        })
        const all = firstValue(result)

        const total = cutFrom(all)
        const rows = all.rows.length + (total != null ? " of " + total : "") + " " +
                     (document.type ? document.type + " " : "") + "rows"
        if (total != null)
        {
            console.warn(
                "Recording the fixture: the injection '" + injectionId + "' got " + rows + ", held to the type's " +
                "maxPageSize. The fixture holds these as all there are."
            )
        }

        // the rows there are, as far as the fixture knows, under the config the view injected with
        source.data = {
            [key]: {...document, rows: all.rows, rowCount: all.rows.length}
        }
        documents.push(injectionId.substring(route.length + 1) + " with " + rows)
    }

    fixture.description = "Recorded " + new Date().toISOString().substring(0, 16).replace("T", " ") + " UTC at " +
                          location.pathname + " as " + fixture.authentication.login +
                          (documents.length ? ": " + documents.join(", ") : "")

    return fixture
}

/**
 * How many rows the given result of a query for all rows was cut short of by the row type's maxPageSize: its rowCount
 * where it holds fewer rows than that, "possibly more" where the query selects no rowCount and the rows fill the
 * maxPageSize, or can't be told from a maxPageSize because the query selects no type.
 *
 * @param all   query document result, as received
 *
 * @returns how many rows there were, or null where the result holds them all
 */
function cutFrom(all: {rows: unknown[], rowCount?: number, type?: string}): number | "possibly more" | null
{
    if (all.rowCount != null)
    {
        return all.rows.length < all.rowCount ? all.rowCount : null
    }

    if (!all.type)
    {
        return "possibly more"
    }
    const maxPageSize = config().meta.types[all.type]?.meta?.maxPageSize
    return maxPageSize != null && all.rows.length >= maxPageSize ? "possibly more" : null
}

/**
 * Sets every description in the given part of the schema to null, which is what the schema says where there is none.
 *
 * @param value     schema, or a part of it
 */
function stripDescriptions(value: unknown): void
{
    if (Array.isArray(value))
    {
        value.forEach(stripDescriptions)
    }
    else if (value && typeof value === "object")
    {
        for (const [key, member] of Object.entries(value))
        {
            if (key === "description")
            {
                (value as Record<string, unknown>)[key] = null
            }
            else
            {
                stripDescriptions(member)
            }
        }
    }
}

/**
 * The file name a fixture recorded on the given route is saved as.
 *
 * @param route     route of the view
 */
function fileName(route: string): string
{
    const name = route.split("/").filter(segment => segment.length).join("-")
    return "fixture" + (name ? "-" + name : "") + ".json"
}

/**
 * Records the view on screen and offers the fixture as a download.
 */
async function download(): Promise<void>
{
    const fixture = await recordFixture()

    const url = URL.createObjectURL(new Blob([JSON.stringify(fixture, null, 2)], {type: "application/json"}))
    const link = document.createElement("a")
    link.href = url
    link.download = fileName(fixture.route)
    link.click()
    // not right away: the click only starts the download, which still reads the URL
    setTimeout(() => URL.revokeObjectURL(url), 0)
}

/**
 * Adds the button that records the view on screen to the page. Plain DOM next to the application's root, so that
 * the application's own tree stays what it renders.
 */
export function mountRecorder(): void
{
    const button = document.createElement("button")
    button.type = "button"
    button.className = "qlive-fixture-recorder"
    button.textContent = "Record fixture"
    button.title = "Saves the data of this view as a fixture to run it on without a server (dev mode only)"

    button.addEventListener("click", () => {
        button.disabled = true
        download().then(
            () => {
                button.disabled = false
            },
            e => {
                button.disabled = false
                console.error("Recording the fixture failed:", e)
                alert("Recording the fixture failed: " + (e instanceof Error ? e.message : String(e)))
            }
        )
    })

    document.body.appendChild(button)
}
