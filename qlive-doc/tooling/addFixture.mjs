/*
 * Adds a fixture recorded with qlive-test's "Record fixture" button to the
 * demos.
 *
 * Every demo runs on the same application, so its config -- the schema and
 * the domain meta data, more than half of every recording -- is stored once,
 * in src/demo/config.json, and the fixture goes under src/demo/fixtures
 * without it. The view comes from the route the fixture was recorded on:
 * "grid/sorting" is qlive-test's grid/Sorting.tsx, so the fixture is saved
 * as src/demo/fixtures/grid/Sorting.json, where QLiveDemo looks for it.
 *
 * A recording whose config differs from the shared one is refused: the
 * application changed since the other fixtures were recorded, and they may
 * no longer fit it. --replace-config takes the new config anyway and lists
 * the fixtures to record again.
 *
 * Run with: pnpm -C qlive-doc fixture <recording.json> [--replace-config]
 */
import {existsSync, globSync, mkdirSync, readFileSync, writeFileSync} from "node:fs";
import {dirname, join, relative, resolve} from "node:path";

const ROOT = resolve(import.meta.dirname, "..");
const VIEWS = resolve(ROOT, "../qlive-test/frontend/src/app");
const CONFIG = join(ROOT, "src/demo/config.json");
const FIXTURES = join(ROOT, "src/demo/fixtures");

const args = process.argv.slice(2);
const replaceConfig = args.includes("--replace-config");
const files = args.filter(arg => arg !== "--replace-config");
if (files.length !== 1)
{
    fail("Usage: pnpm -C qlive-doc fixture <recording.json> [--replace-config]");
}

// pnpm -C runs in qlive-doc; the path is the one given where the command was typed
const recordingPath = resolve(process.env.INIT_CWD ?? process.cwd(), files[0]);
const recording = JSON.parse(readFileSync(recordingPath, "utf8"));
if (typeof recording.route !== "string" || !recording.data)
{
    fail(recordingPath + " is no recorded fixture: it has no route or no data.");
}

const view = viewOf(recording.route);
const target = join(FIXTURES, view + ".json");

if (recording.config)
{
    if (!existsSync(CONFIG))
    {
        writeJSON(CONFIG, recording.config);
        console.log("Wrote the shared config, " + relative(ROOT, CONFIG));
    }
    else if (JSON.stringify(readJSON(CONFIG)) !== JSON.stringify(recording.config))
    {
        const others = globSync("**/*.json", {cwd: FIXTURES})
            .map(file => file.replace(/\.json$/, ""))
            .filter(other => other !== view)
            .sort();
        if (!replaceConfig)
        {
            fail(
                "The recording's config differs from the shared one in " + relative(ROOT, CONFIG) + ": " +
                "qlive-test changed since the other fixtures were recorded. Pass --replace-config to take the new " +
                "one and record these again: " + (others.join(", ") || "none")
            );
        }
        writeJSON(CONFIG, recording.config);
        console.log("Replaced the shared config. Record these again: " + (others.join(", ") || "none"));
    }
}
else if (!existsSync(CONFIG))
{
    fail("The recording has no config, and there is no shared one yet to run it on.");
}

writeJSON(target, {...recording, config: null});
console.log("Wrote " + relative(ROOT, target) + (recording.description ? ": " + recording.description : ""));

/**
 * The view of qlive-test the given route leads to, as its path below the app directory without extension. Routes
 * are lower case and the files are not, so the match ignores case.
 */
function viewOf(route)
{
    const matches = globSync("**/*.tsx", {cwd: VIEWS})
        .map(file => file.replace(/\.tsx$/, ""))
        .filter(file => file.toLowerCase() === route);
    if (matches.length !== 1)
    {
        fail(
            matches.length
                ? "The route '" + route + "' matches several views: " + matches.join(", ")
                : "No view of qlive-test for the route '" + route + "' in " + relative(ROOT, VIEWS)
        );
    }
    return matches[0];
}

function readJSON(path)
{
    return JSON.parse(readFileSync(path, "utf8"));
}

function writeJSON(path, value)
{
    mkdirSync(dirname(path), {recursive: true});
    writeFileSync(path, JSON.stringify(value, null, 2) + "\n");
}

function fail(message)
{
    console.error(message);
    process.exit(1);
}
