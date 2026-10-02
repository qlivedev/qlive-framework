// @vitest-environment jsdom
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import {init} from "../../src/config";
import {GraphQLQuery} from "../../src/GraphQLQuery";
import DataGrid from "../../src/grid/DataGrid";
import {maskOf} from "../../src/merge/fieldMask";
import {initPubSub} from "../../src/pubsub";
import {useInjection} from "../../src/useInjection";
import {connected, FakeWebSocket, lastSocket} from "../fixtures/fakeWebSocket";
import {barDocument, mergeConfig} from "../fixtures/mergeConfig";
import {testAuthentication, atViewRoute} from "../fixtures/testConfig";

/**
 * A grid without a working set, watching its document: other people's writes mark rows and cells, and the grid
 * offers to read the rows again.
 */

const Q_Bars = new GraphQLQuery<any>(
    `query Q_Bars($config: QueryConfig!) {
        queryBarDocument(config: $config) {
            type
            config
            rowCount
            rows {
                id
                name
                num
            }
        }
    }`
);

let update: ReturnType<typeof vi.fn>;

function Bars({watch}: { watch: boolean })
{
    const bars = useInjection<any>(Q_Bars);
    update = vi.spyOn(bars, "update").mockResolvedValue(bars) as any;
    return <DataGrid<{ id: string, name: string, num: number }> doc={ bars } watch={ watch } columns={ ["name", "num"] }/>;
}

function subscriptions(): string[]
{
    const gone = new Set(lastSocket().messages().filter(m => m.type === "Unsubscribe").map(m => m.id));
    return lastSocket().messages().filter(m => m.type === "Subscribe").map(m => m.id).filter(id => !gone.has(id));
}

function publish(entityId: string, fields: string[])
{
    act(() => {
        lastSocket().receive({
            type: "Topic",
            topic: "EntityVersion",
            ids: [subscriptions()[0]],
            payload: {
                id: "ver-2",
                entityType: "Bar",
                entityId,
                prev: "v1",
                fieldMask: maskOf("Bar", fields).toString(),
                fieldLayout: "layout-1",
                ownerId: "somebody",
                created: "2026-09-11T10:00:00Z"
            }
        });
    });
}

let container: HTMLElement;
let root: Root;

beforeEach(async () => {
    atViewRoute()
    vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true);
    FakeWebSocket.instances = [];
    vi.stubGlobal("WebSocket", FakeWebSocket);

    await init({
        config: mergeConfig,
        csrfToken: mergeConfig.csrfToken!,
        authentication: testAuthentication(),
        data: {
            "home/Q_Bars": {data: {queryBarDocument: barDocument()}, type: "BarDocument", meta: null}
        }
    });
    initPubSub();

    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
});

afterEach(() => {
    act(() => root.unmount());
    container.remove();
    initPubSub();
    vi.unstubAllGlobals();
});

describe("DataGrid watching its document", () => {

    it("marks what changed elsewhere and offers a reload", () => {
        act(() => root.render(<Bars watch={ true }/>));
        connected();

        expect(container.querySelector(".qlive-grid-reload")).toBeNull();

        publish("bar-2", ["num"]);

        const rows = Array.from(container.querySelectorAll("tbody tr"));
        expect(rows.map(r => r.className)).toEqual(["qlive-grid-row", "qlive-grid-row qlive-grid-remote-changed"]);
        expect(Array.from(rows[1].querySelectorAll("td"), td => td.className)).toEqual(["", "qlive-remote-changed"]);

        expect(container.querySelector(".qlive-grid-note")!.textContent).toBe("[Rows changed elsewhere]");
        act(() => (container.querySelector(".qlive-grid-reload") as HTMLElement).click());
        expect(update).toHaveBeenCalledWith({});
    });

    it("doesn't watch unless asked", () => {
        act(() => root.render(<Bars watch={ false }/>));

        expect(FakeWebSocket.instances).toHaveLength(0);
    });
});
