import {EventEmitter} from "node:events";
import * as fs from "node:fs";
import * as os from "node:os";
import * as path from "node:path";
import trackUsageData from "babel-plugin-track-usage/data";
import type {Plugin, ResolvedConfig, ViteDevServer} from "vite";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {trackUsage} from "../../src/vite/trackUsage";

/**
 * Covers what the plugin does with a backend: which modules a push carries, and when the browser is told to
 * reload. Both are timing, which is why they are driven here rather than left to a dev server -- the reload
 * has to wait for the push, and nothing about a running Vite would make that visible.
 */
describe("trackUsage", () => {

    const DEBOUNCE_MS = 200;

    const HOME = `
        import {useInjection} from "@quinscape/qlive-ts";
        import {Q_Foo} from "./Q_Foo";

        export default function Home() {
            return useInjection(Q_Foo, {config: {pageSize: 5}});
        }
    `;

    const Q_FOO = `
        import {GraphQLQuery} from "@quinscape/qlive-ts";

        export const Q_Foo = new GraphQLQuery("query Q_Foo { foo }");
    `;

    /** One POST the plugin made, with the response still to be decided by the test. */
    interface Push
    {
        url: string;
        usages: Record<string, unknown>;
        answer(status: number): void;
        fail(): void;
    }

    let projectRoot: string;
    let sourceRoot: string;
    let pushes: Push[];
    let reloads: number;
    let watcher: EventEmitter;

    /** The plugin hooks this test drives, which are declared as plain functions. */
    interface TestPlugin
    {
        configResolved(config: ResolvedConfig): void;
        transform(code: string, id: string): null;
        configureServer(server: ViteDevServer): void;
    }


    function moduleFile(name: string): string
    {
        return path.join(sourceRoot, name);
    }


    function write(name: string, code: string): string
    {
        const file = moduleFile(name);
        fs.mkdirSync(path.dirname(file), {recursive: true});
        fs.writeFileSync(file, code, "utf-8");
        return file;
    }


    /**
     * Lets the promise chain of a resolved push run out. The pushes are fetches, so what follows one is
     * microtasks, which the fake timers do not touch.
     */
    async function settle(): Promise<void>
    {
        for (let i = 0; i < 8; i++)
        {
            await Promise.resolve();
        }
    }


    /** Runs the debounce out and lets whatever it started settle. */
    async function tick(): Promise<void>
    {
        vi.advanceTimersByTime(DEBOUNCE_MS);
        await settle();
    }


    /** `null` starts the plugin the way an application without a QLive backend configures it. */
    function startPlugin(backendOrigin: string | null = "http://localhost:8080"): TestPlugin
    {
        const plugin = trackUsage({
            sourceRoot,
            seedFile: path.join(projectRoot, "no-such-seed.json"),
            backendOrigin: backendOrigin ?? undefined,
            pushDebounceMs: DEBOUNCE_MS,
        }) as unknown as TestPlugin;

        plugin.configResolved({
            root: projectRoot,
            command: "serve",
            mode: "development",
        } as unknown as ResolvedConfig);

        plugin.configureServer({
            watcher,
            ws: {send: () => { reloads++; }},
        } as unknown as ViteDevServer);

        return plugin;
    }


    /**
     * Gets the backend to the state a dev session starts in: both modules analysed and pushed. That first
     * push is the whole analysis, which is what a backend with nothing in it needs.
     */
    async function startWithBothModulesPushed(): Promise<TestPlugin>
    {
        const plugin = startPlugin();

        plugin.transform(HOME, moduleFile("app/Home.tsx"));
        plugin.transform(Q_FOO, moduleFile("app/Q_Foo.ts"));
        await tick();

        expect(pushes).toHaveLength(1);
        expect(pushes[0].url).toContain("full=true");
        expect(Object.keys(pushes[0].usages).sort()).toEqual(["./app/Home", "./app/Q_Foo"]);

        pushes[0].answer(204);
        await settle();
        pushes.length = 0;

        return plugin;
    }


    beforeEach(() => {
        vi.useFakeTimers();

        projectRoot = fs.mkdtempSync(path.join(os.tmpdir(), "track-usage-"));
        sourceRoot = path.join(projectRoot, "src");

        write("app/Home.tsx", HOME);
        write("app/Q_Foo.ts", Q_FOO);

        pushes = [];
        reloads = 0;
        watcher = new EventEmitter();

        // The analysis accumulates in a module-level store of the babel plugin, so one test's modules would
        // otherwise still be in the next one's pushes.
        trackUsageData.clear();

        vi.stubGlobal("fetch", (url: string, init: {body: string}) => new Promise((resolve, reject) => {
            pushes.push({
                url: String(url),
                usages: JSON.parse(init.body).usages,
                answer: (status: number) => resolve({
                    ok: status >= 200 && status < 300,
                    status,
                    statusText: String(status),
                } as Response),
                fail: () => reject(new Error("connection refused")),
            });
        }));
    });


    afterEach(() => {
        vi.useRealTimers();
        vi.unstubAllGlobals();
        fs.rmSync(projectRoot, {recursive: true, force: true});
    });


    it("says nothing to a backend it has nothing for", async () => {
        // No seed file and no module transformed yet. An empty analysis pushed as the whole truth would
        // cost the backend the "not ready" answer the frontend retries on.
        startPlugin();
        await tick();

        expect(pushes).toHaveLength(0);
    });


    it("reloads straight away when there is no backend to tell", async () => {
        // Without a backendOrigin the plugin pushes nothing at all, so the reload has nothing to wait for.
        const plugin = startPlugin(null);
        plugin.transform(Q_FOO, moduleFile("app/Q_Foo.ts"));

        write("app/Q_Foo.ts", Q_FOO.replace("{ foo }", "{ bar }"));
        watcher.emit("change", moduleFile("app/Q_Foo.ts"));

        expect(reloads).toBe(1);

        await tick();
        expect(pushes).toHaveLength(0);
    });


    it("pushes only the module that changed", async () => {
        await startWithBothModulesPushed();

        write("app/Q_Foo.ts", Q_FOO.replace("query Q_Foo { foo }", "query Q_Foo { bar }"));
        watcher.emit("change", moduleFile("app/Q_Foo.ts"));
        await tick();

        expect(pushes).toHaveLength(1);
        expect(Object.keys(pushes[0].usages)).toEqual(["./app/Q_Foo"]);
        expect(pushes[0].url).not.toContain("full=true");
    });


    it("sends one push for the modules of one editing round", async () => {
        await startWithBothModulesPushed();

        write("app/Q_Foo.ts", Q_FOO.replace("{ foo }", "{ bar }"));
        write("app/Home.tsx", HOME.replace("pageSize: 5", "pageSize: 7"));
        watcher.emit("change", moduleFile("app/Q_Foo.ts"));
        watcher.emit("change", moduleFile("app/Home.tsx"));

        await tick();

        expect(pushes).toHaveLength(1);
        expect(Object.keys(pushes[0].usages).sort()).toEqual(["./app/Home", "./app/Q_Foo"]);
    });


    it("holds the reload until the push has been answered", async () => {
        await startWithBothModulesPushed();

        write("app/Q_Foo.ts", Q_FOO.replace("{ foo }", "{ bar }"));
        watcher.emit("change", moduleFile("app/Q_Foo.ts"));
        await tick();

        // The backend renders the page from this analysis, so a browser reloading now would be served the
        // page the edit was meant to change.
        expect(pushes).toHaveLength(1);
        expect(reloads).toBe(0);

        pushes[0].answer(204);
        await settle();

        expect(reloads).toBe(1);
    });


    it("reloads once when a restarted backend asks for the whole analysis", async () => {
        await startWithBothModulesPushed();

        write("app/Q_Foo.ts", Q_FOO.replace("{ foo }", "{ bar }"));
        watcher.emit("change", moduleFile("app/Q_Foo.ts"));
        await tick();

        pushes[0].answer(409);
        await settle();

        // The slice had nothing to be merged into, so everything goes out again -- and the reload belongs to
        // that push, not to the one that was turned away.
        expect(pushes).toHaveLength(2);
        expect(pushes[1].url).toContain("full=true");
        expect(Object.keys(pushes[1].usages).sort()).toEqual(["./app/Home", "./app/Q_Foo"]);
        expect(reloads).toBe(0);

        pushes[1].answer(204);
        await settle();

        expect(reloads).toBe(1);
    });


    it("reloads even when the backend cannot be reached", async () => {
        await startWithBothModulesPushed();

        write("app/Q_Foo.ts", Q_FOO.replace("{ foo }", "{ bar }"));
        watcher.emit("change", moduleFile("app/Q_Foo.ts"));
        await tick();

        vi.spyOn(console, "warn").mockImplementation(() => {});
        pushes[0].fail();
        await settle();

        // The developer is looking at the change they just made. A backend that is down is said so in the
        // console, it does not turn into a dev server that stopped reloading.
        expect(reloads).toBe(1);
    });


    it("takes a failed push's modules into the next one", async () => {
        await startWithBothModulesPushed();

        vi.spyOn(console, "warn").mockImplementation(() => {});

        write("app/Q_Foo.ts", Q_FOO.replace("{ foo }", "{ bar }"));
        watcher.emit("change", moduleFile("app/Q_Foo.ts"));
        await tick();
        pushes[0].fail();
        await settle();

        write("app/Home.tsx", HOME.replace("pageSize: 5", "pageSize: 7"));
        watcher.emit("change", moduleFile("app/Home.tsx"));
        await tick();

        // Q_Foo did not reach the backend, so it goes with the next push rather than waiting for its file to
        // be touched again.
        expect(pushes).toHaveLength(2);
        expect(Object.keys(pushes[1].usages).sort()).toEqual(["./app/Home", "./app/Q_Foo"]);
    });
});
