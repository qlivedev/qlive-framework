/// <reference path="./babel-plugin-track-usage.d.ts" />
// Referenced rather than left to the tsconfig: an application that resolves qlive-ts through the
// "qlive-source" condition typechecks this file inside its own program, where nothing else would
// pull the ambient declarations for the untyped babel plugin in.
import {createRequire} from "node:module";
import * as fs from "node:fs";
import * as path from "node:path";
import {pathToFileURL} from "node:url";
import * as babel from "@babel/core";
import trackUsageBabelPlugin from "babel-plugin-track-usage";
import trackUsageData from "babel-plugin-track-usage/data";
import deepEqual from "deep-equal";
import type {Plugin, ResolvedConfig} from "vite";

export interface TrackedFunctionSpec
{
    /** Import source, relative to `sourceRoot` (e.g. "./service/i18n") or a bare package specifier. */
    module: string;
    /** "" if the module itself is called as a function; otherwise the method/export name called on it. */
    fn: string;

    /**
     * Enables varargs support where a function can have n static arguments and then a number of variable arguments
     * that have no restriction on being static. If varargs is a number, it defines how many arguments are statically
     * captured (true is the same as 1)
     */
    varArgs?: boolean | number;
    /**
     * Allows recording context expressions against the matched location in the AST. e.g. "parent.id.name"
     */
    captureContext?: boolean;
    /**
     * Enables identifier matching where instead of a static expression, we capture the *name* of an identifier to
     * reference some global namespace (or local in some way).
     */
    allowIdentifier?: boolean;
}

/**
 * The calls QLive's own analysis is built on. Their keys are the symbolic names the server looks up a call under --
 * ModuleFunctionReferences.USE_INJECTION_CALL_NAME and its neighbours name the same strings
 * on the Java side -- so what belongs in here is the framework's to state and not an application's to
 * get right. An application adds its own entries through `trackedFunctions`, and is refused a key that
 * is already one of these.
 */
export const QLIVE_TRACKED_FUNCTIONS: Record<string, TrackedFunctionSpec> = {
    i18n: {
        module: "@quinscape/qlive-ts", fn: "i18n",
        varArgs: true
    },
    useInjection: {
        module: "@quinscape/qlive-ts", fn: "useInjection", allowIdentifier: true
    },
    noSchema: {
        module: "@quinscape/qlive-ts", fn: "noSchema"
    },
    GraphQLQuery: {
        module: "@quinscape/qlive-ts", fn: "GraphQLQuery"
    },
};

/**
 * Where QLive's TrackUsageDevController receives pushed analysis. Both halves have to agree on it, so
 * it is the framework's and not something an application spells out.
 */
const TRACK_USAGE_DEV_URI = "/_dev/track-usage";

/**
 * Marks a push as the complete analysis rather than a slice of changed modules, which is what the backend
 * needs to start from a known state.
 */
const FULL_PUSH_QUERY = "?full=true";

export interface TrackUsagePluginOptions
{
    /**
     * Calls to record on top of {@link QLIVE_TRACKED_FUNCTIONS}. Their keys have to be the application's
     * own: one of QLive's is refused rather than merged over, because the server reads the framework's
     * calls back out under exactly those names.
     */
    trackedFunctions?: Record<string, TrackedFunctionSpec>;
    /**
     * Absolute path to the directory tracking is scoped to. Default: `src/` below Vite's `root`, which is
     * where an application's own code lives.
     */
    sourceRoot?: string;
    debug?: boolean;
    /**
     * Records the source offsets ([start, end]) of every tracked call alongside its arguments. Consumers that
     * rewrite the source at the call site (the QLive backend patches the result type into every
     * `new GraphQLQuery<...>()`) cannot work without them, so leave this on unless you only need the
     * argument values.
     *
     * Default: true.
     */
    indexes?: boolean;
    /** Previously-built track-usage.json used to pre-seed dev mode. Default: <sourceRoot>/../dist/<outputFileName>. */
    seedFile?: string;
    /** Default: "track-usage.json". */
    outputFileName?: string;
    /**
     * Origin of the QLive backend, e.g. "http://localhost:8080". Live track-usage data gets POSTed there as
     * it changes. `vite build` writes track-usage.json to disk for the backend to read, but `vite dev` never
     * touches disk, so this is dev mode's only way to get fresh data to a backend that needs it live (e.g.
     * for codegen). Omit to skip pushing entirely.
     */
    backendOrigin?: string;
    /**
     * How long to collect changed modules before pushing them to `backendOrigin`, in milliseconds. One save
     * can transform several modules, and the backend regenerates types for every module it is handed, so
     * they are worth sending as one push. Default: 200.
     */
    pushDebounceMs?: number;
    /**
     * The GraphQL schema the generated query result types are checked against, relative to Vite's
     * `root`. While a dev server runs, every saved query gets its `GraphQLQuery<T>` rewritten from it,
     * so the type next to a query follows the query.
     *
     * Default: "schema.graphql", generating whenever that file is there and `@quinscape/qlive-codegen`
     * is installed. Naming a file that does not exist is an error -- silence would look like a plugin
     * that does not work. `false` turns the generation off.
     */
    queryTypes?: string | false;
}

/**
 * The options as the hooks use them: defaults applied, sourceRoot absolute and terminated.
 */
interface ResolvedOptions
{
    trackedFunctions: Record<string, TrackedFunctionSpec>;
    sourceRoot: string;
    debug?: boolean;
    indexes: boolean;
}

/**
 * Rewrites the `GraphQLQuery<T>` of every module handed to it. This is what
 * `@quinscape/qlive-codegen` returns; the plugin only ever calls update().
 */
interface QueryTypeGenerator
{
    update(analysis: UsageSnapshot): {
        updated: string[];
        failed: {module: string; message: string}[];
    };
}

interface UsageSnapshot
{
    usages: Record<string, unknown>;
}

function shouldTrack(id: string, options: ResolvedOptions): boolean
{
    const filePath = id.split("?")[0];
    return (
        filePath.startsWith(options.sourceRoot) &&
        /\.(tsx?|jsx?)$/.test(filePath) &&
        !filePath.includes("/node_modules/")
    );
}

/**
 * babel-plugin-track-usage computes each file's module id relative to the
 * top-level babel `root` option, then strips its own `sourceRoot` plugin
 * option (which must itself be *relative to that root*, e.g. "src/" - not
 * absolute) from the result. So we derive a stable project root one level
 * above our absolute `sourceRoot` and re-express `sourceRoot` relative to it.
 */
function runBabelOnFile(absPath: string, code: string, options: ResolvedOptions): void
{
    const srcAbs = options.sourceRoot.replace(/\/$/, "");
    const projectRoot = path.dirname(srcAbs);
    const relativeSourceRoot = path.relative(projectRoot, srcAbs) + "/";

    babel.transformSync(code, {
        filename: absPath,
        root: projectRoot,
        babelrc: false,
        configFile: false,
        parserOpts: {plugins: ["typescript", "jsx"]},
        plugins: [
            [
                trackUsageBabelPlugin,
                {
                    trackedFunctions: options.trackedFunctions,
                    sourceRoot: relativeSourceRoot,
                    debug: options.debug,
                    indexes: options.indexes,
                },
            ],
        ],
    });
}

function toRelativeModuleId(absPath: string, sourceRoot: string): string
{
    const withoutRoot = absPath.startsWith(sourceRoot) ? absPath.slice(sourceRoot.length) : absPath;
    const withoutExt = withoutRoot.slice(0, withoutRoot.lastIndexOf("."));
    return "./" + withoutExt;
}

/**
 * Resolves the options against the Vite config, which is the first point at which the defaults are
 * knowable. The trailing slash is enforced rather than demanded: sourceRoot is compared by prefix, and
 * one missing slash would silently track a sibling directory whose name starts the same way.
 */
function resolveOptions(options: TrackUsagePluginOptions, config: ResolvedConfig): ResolvedOptions
{
    const sourceRoot = options.sourceRoot ?? path.join(config.root, "src");
    const trackedFunctions = options.trackedFunctions ?? {};

    // Said out loud rather than settled by the spread order below. An application cannot make its own
    // version of one of these work: the server reads them back out by name, and everything between the
    // call and that read is the framework's. Silently ignoring the entry would leave a config that does
    // nothing, and silently taking it would take QLive's own calls out of the analysis.
    const reserved = Object.keys(trackedFunctions).filter((name) => name in QLIVE_TRACKED_FUNCTIONS);
    if (reserved.length > 0)
    {
        throw new Error(
            `[track-usage] trackedFunctions may not redefine ${reserved.join(", ")}: QLive records its own ` +
            `calls under those names and the server looks them up there. Use a name of your own -- the key ` +
            `is only what the analysis files the call under, so the same function can be tracked twice.`
        );
    }

    return {
        trackedFunctions: {...trackedFunctions, ...QLIVE_TRACKED_FUNCTIONS},
        sourceRoot: sourceRoot.endsWith("/") ? sourceRoot : sourceRoot + "/",
        debug: options.debug,
        indexes: options.indexes ?? true,
    };
}

/**
 * The analysis of one source tree, as `babel-plugin-track-usage` records it. Keyed by module id
 * relative to `sourceRoot` ("./app/Q_Foo"), which is how the backend addresses a module as well.
 */
export interface TrackUsageAnalysis
{
    usages: Record<string, unknown>;
}

export interface AnalyzeSourceTreeOptions
{
    /** Absolute path to the directory to analyze, i.e. the `sourceRoot` the plugin would be given. */
    sourceRoot: string;
    /** Calls to record on top of {@link QLIVE_TRACKED_FUNCTIONS}, same rules as the plugin's. */
    trackedFunctions?: Record<string, TrackedFunctionSpec>;
    debug?: boolean;
    /** See {@link TrackUsagePluginOptions.indexes}. Default: true. */
    indexes?: boolean;
}

/**
 * Runs the track-usage analysis over a whole source tree in one go, outside of Vite.
 *
 * The plugin gets its analysis handed to it file by file, as Vite transforms them, which is the wrong
 * shape for a tool that is not a dev server -- the codegen CLI wants the whole tree before it starts.
 * Both go through the same babel pass, so what a build-time generator sees is what the dev-time one
 * sees: the same recorded calls under the same names, at the same source offsets.
 *
 * @param options    what to analyze and how
 *
 * @returns the analysis of every tracked file below `sourceRoot`
 */
export function analyzeSourceTree(options: AnalyzeSourceTreeOptions): TrackUsageAnalysis
{
    const resolved = resolveScanOptions(options);

    // The plugin's data is a module-global accumulator, so a scan starts from a clean one rather than
    // on top of whatever a plugin instance in the same process left behind.
    trackUsageData.clear();

    for (const file of collectSources(resolved.sourceRoot, resolved))
    {
        runBabelOnFile(file, fs.readFileSync(file, "utf-8"), resolved);
    }

    return trackUsageData.get() as TrackUsageAnalysis;
}


/**
 * Collects the files below `dir` the analysis applies to, in a stable order so that two runs over an
 * unchanged tree produce byte-identical output.
 */
function collectSources(dir: string, options: ResolvedOptions): string[]
{
    const found: string[] = [];

    for (const entry of fs.readdirSync(dir, {withFileTypes: true}).sort((a, b) => a.name < b.name ? -1 : 1))
    {
        const absPath = path.join(dir, entry.name);
        if (entry.isDirectory())
        {
            found.push(...collectSources(absPath, options));
        }
        else if (entry.isFile() && shouldTrack(absPath, options))
        {
            found.push(absPath);
        }
    }

    return found;
}


/**
 * The subset of {@link resolveOptions} that needs no Vite config: `sourceRoot` is given rather than
 * derived from `root`, and nothing here pushes or writes a bundle.
 */
function resolveScanOptions(options: AnalyzeSourceTreeOptions): ResolvedOptions
{
    const trackedFunctions = options.trackedFunctions ?? {};

    const reserved = Object.keys(trackedFunctions).filter((name) => name in QLIVE_TRACKED_FUNCTIONS);
    if (reserved.length > 0)
    {
        throw new Error(
            `[track-usage] trackedFunctions may not redefine ${reserved.join(", ")}: QLive records its own ` +
            `calls under those names and the server looks them up there.`
        );
    }

    const sourceRoot = path.resolve(options.sourceRoot);

    return {
        trackedFunctions: {...trackedFunctions, ...QLIVE_TRACKED_FUNCTIONS},
        sourceRoot: sourceRoot.endsWith("/") ? sourceRoot : sourceRoot + "/",
        debug: options.debug,
        indexes: options.indexes ?? true,
    };
}


/**
 * Loads the query result type generator out of the *application's* `@quinscape/qlive-codegen`.
 *
 * Resolved from the application rather than imported, because it is the application's build tool:
 * qlive-ts must not carry `graphql` and `@graphql-tools/*` into the dependency graph of an app that
 * generates nothing -- that is the whole reason the codegen is a package of its own.
 *
 * @param root        Vite's `root`, i.e. the directory the application's package.json sits in
 * @param schemaPath  absolute path of the GraphQL schema
 * @param sourceRoot  absolute path of the tracked source directory
 */
async function loadQueryTypeGenerator(
    root: string,
    schemaPath: string,
    sourceRoot: string
): Promise<QueryTypeGenerator>
{
    const require = createRequire(path.join(root, "package.json"));

    let entry: string;
    try
    {
        entry = require.resolve("@quinscape/qlive-codegen");
    }
    catch
    {
        throw new Error(
            `@quinscape/qlive-codegen is not installed. It is what generates the query result types; ` +
            `add it as a devDependency, or pass queryTypes: false to do without them.`
        );
    }

    const codegen = await import(pathToFileURL(entry).href) as {
        createQueryTypeGenerator(options: {schemaPath: string; sourceRoot: string}): Promise<QueryTypeGenerator>;
    };

    return codegen.createQueryTypeGenerator({schemaPath, sourceRoot});
}


export function trackUsage(options: TrackUsagePluginOptions = {}): Plugin {
    const outputFileName = options.outputFileName ?? "track-usage.json";
    const pushUrl = options.backendOrigin ? options.backendOrigin + TRACK_USAGE_DEV_URI : undefined;
    const pushDebounceMs = options.pushDebounceMs ?? 200;
    let resolved: ResolvedOptions;
    /** Vite's `root`, i.e. where the application's package.json and schema.graphql live. */
    let root = "";
    let command: "build" | "serve" = "build";
    let isDevMode = false;
    let devData: UsageSnapshot = {usages: {}};

    /** Modules whose analysis has changed since the last successful push. */
    const changedModules = new Set<string>();
    /** Set when the backend needs the whole analysis instead of a slice, i.e. before the first push and after
     *  the backend has been restarted. */
    let needsFullPush = true;
    let pushTimer: ReturnType<typeof setTimeout> | undefined;
    let pushWarned = false;
    /** Set when an edit is waiting for its push to reach the backend before the browser reloads. */
    let reloadPending = false;
    /** Reloads the browser, once there is a dev server to do it through. */
    let reloadBrowser: (() => void) | undefined;
    /** Rewrites the query result types, or null while they are switched off. Created once per server. */
    let queryTypes: Promise<QueryTypeGenerator | null> = Promise.resolve(null);


    /**
     * Brings the result types of the given modules back in line with their queries.
     *
     * Failures are reported rather than thrown: a query that does not fit the schema is something the
     * developer is in the middle of writing, and taking the dev server down over it would be a poor way
     * of saying so.
     */
    async function generateQueryTypes(usages: Record<string, unknown>): Promise<void>
    {
        const generator = await queryTypes.catch((e) => {
            console.error(`[track-usage] query result types are off: ${e.message ?? e}`);
            return null;
        });
        if (!generator)
        {
            return;
        }

        try
        {
            const {updated, failed} = generator.update({usages});

            if (updated.length)
            {
                console.info(`[track-usage] updated query result types in ${updated.join(", ")}`);
            }
            for (const {module, message} of failed)
            {
                console.error(`[track-usage] no result type for ${module}: ${message}`);
            }
        }
        catch (e)
        {
            console.error(`[track-usage] query result type generation failed: ${(e as Error).message ?? e}`);
        }
    }

    /**
     * Records one module's fresh analysis, reporting whether it differs from what the backend already has.
     */
    function mergeIntoDevData(absPath: string): boolean
    {
        const fresh = trackUsageData.get() as UsageSnapshot;
        const key = toRelativeModuleId(absPath, resolved.sourceRoot);
        if (!deepEqual(devData.usages[key], fresh.usages[key]))
        {
            devData.usages[key] = fresh.usages[key];
            changedModules.add(key);
            return true
        }
        return false
    }

    /**
     * Collects further changes before pushing: one save transforms several modules, and the backend
     * regenerates types for every module it is handed.
     */
    function schedulePush(): void
    {
        if (!pushUrl)
        {
            return;
        }
        if (pushTimer !== undefined)
        {
            clearTimeout(pushTimer);
        }
        pushTimer = setTimeout(pushToServer, pushDebounceMs);
    }

    /**
     * Sends what the backend does not have yet: the modules changed since the last push, or the whole
     * analysis while {@link needsFullPush} stands. Nothing to send is not a push -- the dev server transforms
     * every module the browser asks for, and all but the edited one match what was pushed before.
     */
    function pushToServer(): void
    {
        if (pushTimer !== undefined)
        {
            clearTimeout(pushTimer);
            pushTimer = undefined;
        }
        if (!pushUrl)
        {
            return;
        }

        const full = needsFullPush;
        const modules = full ? Object.keys(devData.usages) : [...changedModules];

        // Taken with the modules, so that an edit made while this push is in flight keeps its own reload:
        // that one belongs to the next push, which is the one carrying it to the backend.
        const reload = reloadPending;
        reloadPending = false;

        if (modules.length === 0)
        {
            // Nothing the backend does not have: an earlier push took these modules, or -- at a dev-server
            // start with no seed file to read -- there is no analysis yet. Pushing that emptiness would only
            // cost the backend its "not ready" answer, which is the one the frontend knows how to retry.
            reloadIf(reload);
            return;
        }

        // Cleared before the request, so that edits made while it is in flight are pushed by the next one.
        // A failed push puts them back.
        changedModules.clear();
        needsFullPush = false;

        const usages: Record<string, unknown> = {};
        for (const module of modules)
        {
            usages[module] = devData.usages[module];
        }

        const url = full ? pushUrl + FULL_PUSH_QUERY : pushUrl;

        fetch(url, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({usages}),
        })
            .then((res) => {
                // The backend holds the analysis in memory only, so a restarted one has nothing to merge a
                // slice into and says so. Everything it missed goes out in one full push.
                if (res.status === 409 && !full)
                {
                    // Handed on rather than done here: the resync is the push that gets this edit to the
                    // backend, so it is the one the browser waits for.
                    reloadPending = reload;
                    needsFullPush = true;
                    pushToServer();
                    return;
                }

                if (!res.ok)
                {
                    requeue(full, modules);
                    if (!pushWarned)
                    {
                        console.warn(`[track-usage] push to ${url} failed: ${res.status} ${res.statusText}`);
                        pushWarned = true;
                    }
                } else
                {
                    pushWarned = false;
                }
                reloadIf(reload);
            })
            .catch((e) => {
                requeue(full, modules);
                if (!pushWarned)
                {
                    console.warn(`[track-usage] could not reach ${url} (is the backend running?)`, e);
                    pushWarned = true;
                }
                reloadIf(reload);
            });
    }


    /**
     * Reloads the browser for an edit whose push has been dealt with -- including one that failed, because
     * the developer is looking at the change they just made and the warning above says why the backend does
     * not have it. Not reloading would make an unreachable backend look like a broken dev server.
     */
    function reloadIf(reload: boolean): void
    {
        if (reload)
        {
            reloadBrowser?.();
        }
    }

    /**
     * Takes a failed push's modules back into the queue. No retry is scheduled: the backend is unreachable or
     * unhappy, and the next edit is soon enough to try again without hammering it in between.
     */
    function requeue(full: boolean, modules: string[]): void
    {
        if (full)
        {
            needsFullPush = true;
        }
        else
        {
            modules.forEach((module) => changedModules.add(module));
        }
    }

    return {
        name: "track-usage",
        enforce: "pre",

        configResolved(config)
        {
            resolved = resolveOptions(options, config);
            root = config.root;
            command = config.command === "serve" ? "serve" : "build";
            // `command === "serve"` alone isn't enough: Vitest also runs the
            // dev-server pipeline but with mode "test", and `vite --mode
            // production` keeps command "serve" too. Require mode === "development"
            // so pushes only happen for an actual `vite dev`/`vite`.
            isDevMode = command === "serve" && config.mode === "development";
        },

        transform(code, id)
        {
            if (!shouldTrack(id, resolved))
            {
                return null;
            }
            runBabelOnFile(id, code, resolved);
            if (isDevMode && mergeIntoDevData(id))
            {
                schedulePush();
            }
            return null;
        },

        generateBundle()
        {
            this.emitFile({
                type: "asset",
                fileName: outputFileName,
                source: JSON.stringify(trackUsageData.get()),
            });
        },

        configureServer(server)
        {
            const seedPath = options.seedFile ?? path.join(resolved.sourceRoot, "..", "dist", outputFileName);
            try
            {
                devData = JSON.parse(fs.readFileSync(seedPath, "utf-8"));
            } catch
            {
                devData = {usages: {}};
            }
            reloadBrowser = () => server.ws.send({type: "full-reload"});

            // Immediately and in full: the backend answers page requests from this data, and the first of
            // them arrives before the browser has asked the dev server for a single module.
            if (isDevMode)
            {
                pushToServer();
            }

            // Once per server, and before the first save: a checked-in result type can be behind its query
            // -- someone else edited it, or the schema moved -- and the first thing the developer would
            // otherwise see is a type error in a module they have not touched.
            if (isDevMode && options.queryTypes !== false)
            {
                const schemaPath = path.resolve(root, options.queryTypes ?? "schema.graphql");

                if (options.queryTypes !== undefined || fs.existsSync(schemaPath))
                {
                    queryTypes = loadQueryTypeGenerator(root, schemaPath, resolved.sourceRoot);
                    generateQueryTypes(analyzeSourceTree({
                        sourceRoot: resolved.sourceRoot,
                        trackedFunctions: options.trackedFunctions,
                        debug: options.debug,
                        indexes: resolved.indexes,
                    }).usages);
                }
            }

            let errorCount = 0

            // Only "change" is handled live - adding, renaming or deleting a tracked
            // file requires a dev server restart to be reflected.
            server.watcher.on("change", (file) => {
                if (!shouldTrack(file, resolved))
                {
                    return;
                }
                const code = fs.readFileSync(file, "utf-8");
                try
                {
                    runBabelOnFile(file, code, resolved);
                }
                catch(e)
                {
                    if (errorCount === 0)
                    {
                        console.error("Error", e);
                    }
                    errorCount++
                    return
                }
                if (mergeIntoDevData(file))
                {
                    // The write shifts the source offsets of the very call it was generated from, so this
                    // module comes back through here once more -- and renders the same type, which is
                    // where it stops.
                    const key = toRelativeModuleId(file, resolved.sourceRoot);
                    generateQueryTypes({[key]: (trackUsageData.get() as UsageSnapshot).usages[key]});

                    if (isDevMode && pushUrl)
                    {
                        // The reload waits for the push. The backend renders the page from this analysis, so
                        // a browser reloading first is served the page the edit was meant to change.
                        reloadPending = true;
                        schedulePush();
                    }
                    else
                    {
                        // Nothing is pushed, so there is nothing for the reload to wait for.
                        reloadBrowser?.();
                    }
                }
                errorCount = 0
            });
        },
    };
}
