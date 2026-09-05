import {vi} from "vitest";

/**
 * Stubs out what the server puts into the page and what it answers with. The suite runs
 * without a server, and in node without the globals graphql() reads out of the page.
 *
 * @param response  GraphQL response the next request resolves with
 *
 * @returns the fetch mock, to assert on what went out
 */
export function respondWith(response: any)
{
    const fetchMock = vi.fn().mockResolvedValue({
        json: () => Promise.resolve(response)
    })

    vi.stubGlobal("fetch", fetchMock)
    vi.stubGlobal("contextPath", "")
    vi.stubGlobal("csrfToken", {header: "X-CSRF", value: "token"})

    // only where there is none: a test running in jsdom has a real window, and
    // replacing it would take react-dom's document with it
    if (typeof window === "undefined")
    {
        vi.stubGlobal("window", {location: {origin: "http://localhost"}})
    }

    return fetchMock
}

/**
 * The variables of the request the given mock recorded.
 *
 * @param fetchMock     mock from respondWith()
 * @param call          index of the request, the first one by default
 */
export function sentVariables(fetchMock: ReturnType<typeof vi.fn>, call: number = 0)
{
    return JSON.parse(fetchMock.mock.calls[call][1].body).variables
}
