import * as fs from "node:fs";
import * as path from "node:path";
import * as babel from "@babel/core";
import trackUsageBabelPlugin from "babel-plugin-track-usage";
import trackUsageData from "babel-plugin-track-usage/data";
import deepEqual from "deep-equal";
import type {Plugin} from "vite";

export interface TrackedFunctionSpec
{
    /** Import source, relative to `sourceRoot` (e.g. "./service/i18n") or a bare package specifier. */
    module: string;
    /** "" if the module itself is called as a function; otherwise the method/export name called on it. */
    fn: string;
    varArgs?: boolean | number;
    captureContext?: boolean;
    allowIdentifier?: boolean;
}

export interface TrackUsagePluginOptions
{
    trackedFunctions: Record<string, TrackedFunctionSpec>;
    /** Absolute path to the directory tracking is scoped to. Must end with "/". */
    sourceRoot: string;
    debug?: boolean;
    indexes?: boolean;
    /** Previously-built track-usage.json used to pre-seed dev mode. Default: <sourceRoot>/../dist/<outputFileName>. */
    seedFile?: string;
    /** Default: "track-usage.json". */
    outputFileName?: string;
    /**
     * Absolute URL of a backend dev endpoint that live track-usage snapshots get POSTed to
     * as they change. `vite build` writes track-usage.json to disk for the backend to read,
     * but `vite dev` never touches disk, so this is dev mode's only way to get fresh data to
     * a backend that needs it live (e.g. for codegen). Omit to skip pushing entirely.
     */
    pushUrl?: string;
}

interface UsageSnapshot
{
    usages: Record<string, unknown>;
}

function shouldTrack(id: string, options: TrackUsagePluginOptions): boolean
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
function runBabelOnFile(absPath: string, code: string, options: TrackUsagePluginOptions): void
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

export default function trackUsage(options: TrackUsagePluginOptions): Plugin {
    const outputFileName = options.outputFileName ?? "track-usage.json";
    let command: "build" | "serve" = "build";
    let devData: UsageSnapshot = {usages: {}};

    function mergeIntoDevData(absPath: string): boolean
    {
        const fresh = trackUsageData.get() as UsageSnapshot;
        const key = toRelativeModuleId(absPath, options.sourceRoot);
        if (!deepEqual(devData.usages[key], fresh.usages[key]))
        {
            devData.usages[key] = fresh.usages[key];
            return true
        }
        return false
    }

    let pushWarned = false;

    function pushToServer(): void
    {
        if (!options.pushUrl)
        {
            return;
        }
        fetch(options.pushUrl, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(devData),
        })
            .then((res) => {
                if (!res.ok && !pushWarned)
                {
                    console.warn(`[track-usage] push to ${options.pushUrl} failed: ${res.status} ${res.statusText}`);
                    pushWarned = true;
                } else if (res.ok)
                {
                    pushWarned = false;
                }
            })
            .catch((e) => {
                if (!pushWarned)
                {
                    console.warn(`[track-usage] could not reach ${options.pushUrl} (is the backend running?)`, e);
                    pushWarned = true;
                }
            });
    }

    return {
        name: "track-usage",
        enforce: "pre",

        configResolved(config)
        {
            command = config.command === "serve" ? "serve" : "build";
        },

        transform(code, id)
        {
            if (!shouldTrack(id, options))
            {
                return null;
            }
            runBabelOnFile(id, code, options);
            if (command === "serve")
            {
                mergeIntoDevData(id);
                pushToServer();
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
            const seedPath = options.seedFile ?? path.join(options.sourceRoot, "..", "dist", outputFileName);
            try
            {
                devData = JSON.parse(fs.readFileSync(seedPath, "utf-8"));
            } catch
            {
                devData = {usages: {}};
            }
            pushToServer();

            // Only "change" is handled live - adding, renaming or deleting a tracked
            // file requires a dev server restart to be reflected.
            server.watcher.on("change", (file) => {
                if (!shouldTrack(file, options))
                {
                    return;
                }
                const code = fs.readFileSync(file, "utf-8");
                runBabelOnFile(file, code, options);
                if (mergeIntoDevData(file))
                {
                    pushToServer();
                    server.ws.send({type: "full-reload"});
                }
            });
        },
    };
}
