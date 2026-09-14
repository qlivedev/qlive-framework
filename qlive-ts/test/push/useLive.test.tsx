// @vitest-environment jsdom
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act, Component, ReactNode, StrictMode} from "react";
import {createRoot, Root} from "react-dom/client";
import {init} from "../../src/config";
import {GraphQLQuery} from "../../src/GraphQLQuery";
import {maskOf} from "../../src/merge/fieldMask";
import {initPubSub} from "../../src/pubsub";
import {DocumentWatchSnapshot} from "../../src/push/entityVersion";
import {useLiveRows} from "../../src/push/useLive";
import {connected, FakeWebSocket, lastSocket} from "../fixtures/fakeWebSocket";
import {barDocument, mergeConfig} from "../fixtures/mergeConfig";
import {testAuthentication} from "../fixtures/testConfig";

/**
 * The live view's query: the two fields it shows and nothing else, as qlive-test's Q_BarNames has them.
 */
const Q_BarNames = new GraphQLQuery<any>(
    `query Q_BarNames($config: QueryConfig!) {
        queryBarDocument(config: $config) {
            type
            config
            rows {
                id
                name
            }
        }
    }`
)

/** the snapshot the last render of the view was given */
let live: DocumentWatchSnapshot

function BarsLive()
{
    live = useLiveRows(Q_BarNames)

    return (
        <p>{ live.stale ? live.remoteChanged.length + " changed" : "current" }</p>
    )
}


/** The subscription ids the module has live: every Subscribe it sent that it has not unsubscribed. */
function liveSubscriptions(): string[]
{
    const gone = new Set(
        lastSocket().messages().filter(m => m.type === "Unsubscribe").map(m => m.id)
    )

    return lastSocket().messages()
        .filter(m => m.type === "Subscribe")
        .map(m => m.id)
        .filter(id => !gone.has(id))
}


/** Delivers one EntityVersion message to the subscription of the given id. */
function publish(id: string, entityType: string, entityId: string, fields: string[])
{
    act(() =>
    {
        lastSocket().receive({
            type: "Topic",
            topic: "EntityVersion",
            ids: [id],
            payload: {
                id: "ver-2",
                entityType,
                entityId,
                prev: "v1",
                fieldMask: maskOf(entityType, fields).toString(),
                fieldLayout: "layout-1",
                ownerId: "somebody",
                created: "2026-09-11T10:00:00Z"
            }
        })
    })
}


let container: HTMLElement
let root: Root


beforeEach(async () => {
    vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true)

    FakeWebSocket.instances = []
    vi.stubGlobal("WebSocket", FakeWebSocket)

    await init({
        config: mergeConfig,
        csrfToken: mergeConfig.csrfToken!,
        authentication: testAuthentication(),
        data: {
            Q_BarNames: {data: {queryBarDocument: barDocument()}, type: "BarDocument", meta: null}
        }
    })

    initPubSub()

    container = document.createElement("div")
    document.body.appendChild(container)
    root = createRoot(container)
})


afterEach(() => {
    act(() => {
        root.unmount()
    })
    container.remove()
    initPubSub()
    vi.unstubAllGlobals()
})


describe("useLiveRows", () => {

    // StrictMode calls the useState() initializer twice and mounts, unmounts and mounts again, and a view
    // that hears nothing afterwards is a live view that is dead in dev and works in production.
    it("hears about a change after StrictMode's remount", () => {

        act(() => {
            root.render(<StrictMode><BarsLive/></StrictMode>)
        })
        connected()

        // one subscription, not the two a watch opened in the initializer would leave behind
        expect(liveSubscriptions()).toHaveLength(1)

        expect(container.textContent).toBe("current")

        publish(liveSubscriptions()[0], "Bar", "bar-1", ["name"])

        expect(container.textContent).toBe("1 changed")
        expect(live.stale).toBe(true)
        expect(live.remoteChanged).toEqual([{type: "Bar", id: "bar-1", fields: ["name"]}])
    })


    it("unsubscribes when the view goes", () => {

        act(() => {
            root.render(<StrictMode><BarsLive/></StrictMode>)
        })
        connected()

        expect(liveSubscriptions()).toHaveLength(1)

        act(() => {
            root.render(<StrictMode><p>gone</p></StrictMode>)
        })

        expect(liveSubscriptions()).toHaveLength(0)
    })
})


/**
 * A render React throws away: a sibling throws after the live view rendered, so the boundary takes over and
 * the view's fiber -- hooks, memoized state and all -- is discarded without a single effect having run.
 */
function Boom(): ReactNode
{
    throw new Error("a sibling threw while rendering")
}

class Boundary extends Component<{ children: ReactNode }, { error: unknown }>
{
    state = {error: null as unknown}

    static getDerivedStateFromError(error: unknown) { return {error} }

    render() { return this.state.error ? <p>caught</p> : this.props.children }
}


describe("a render that is thrown away", () => {

    // Deliberately without StrictMode: nothing about this one is a dev-only simulation. A subscription
    // opened while rendering belongs to a commit that never happened, so nothing is left to close it.
    it("leaves nothing subscribed", () => {

        vi.spyOn(console, "error").mockImplementation(() => {})

        act(() => {
            root.render(<Boundary><BarsLive/><Boom/></Boundary>)
        })

        expect(container.textContent).toBe("caught")

        // The socket exists at all only if something subscribed from that discarded render.
        if (FakeWebSocket.instances.length > 0)
        {
            connected()
            expect(liveSubscriptions(), "subscription leaked by a render that was thrown away").toEqual([])
        }
    })
})
