import config, {QLiveFixture} from "../config";
import {convertVariablesToServer} from "../converter";
import graphql, {firstValue} from "../util/graphql";
import {injectionNote, keptBootstrap} from "./notes";

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
 * document comes back with all rows unless its type sets a maxPageSize the result is larger than, and a fixture
 * holding a part of the rows would page and filter wrong without any sign of it -- so that fails instead.
 *
 * The fixture carries the authentication of whoever records it: record as a demo login, not a personal one.
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

    const fixture: QLiveFixture = structuredClone(bootstrap)
    // a token for a session that will be long gone by the time the fixture is used
    fixture.csrfToken = {...fixture.csrfToken, value: ""}
    fixture.path = location.pathname

    const queryDocumentTypes = config().queryDocumentTypes!
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
            // The config the server answered with, which has the type's defaults in it, not the delta the view gave.
            [configVariable]: {...document.config, offset: 0, pageSize: 0}
        })
        const all = firstValue(result)

        if (all.rows.length !== all.rowCount)
        {
            throw new Error(
                "The injection '" + injectionId + "' got " + all.rows.length + " of " + all.rowCount + " " +
                document.type + " rows: the type's maxPageSize holds a query for all rows to that. A fixture needs " +
                "every row, so record from data that fits."
            )
        }

        // all the rows, under the config the view injected with
        source.data = {
            [key]: {...document, rows: all.rows, rowCount: all.rows.length}
        }
    }

    return fixture
}

/**
 * The file name a fixture recorded at the given path is saved as.
 *
 * @param path  location path
 */
function fileName(path: string): string
{
    const name = path.split("/").filter(segment => segment.length).join("-")
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
    link.download = fileName(fixture.path!)
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
