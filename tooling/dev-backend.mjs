#!/usr/bin/env node
// Launches the already-built qlive-test jar directly, skipping Maven entirely.
// Kept snappy for the frontend inner loop: rebuilding via Maven on every
// `pnpm dev` forces jOOQ DB codegen and a full pnpm frontend build even though
// Vite (started alongside this) is what actually serves the frontend.
//
// Skipping the build is why this checks two things first. A jar older than the
// Java sources runs code that is not what the tree says, which looks like a
// feature that mysteriously does not work rather than like a stale build. And
// port 8080 belongs to whoever took it first -- usually the IDE's run
// configuration, which is the one with a debugger attached and the one to keep.
import { existsSync, readdirSync, statSync } from "node:fs";
import { spawn } from "node:child_process";
import { createConnection } from "node:net";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const PORT = 8080;

const rootDir = join(dirname(fileURLToPath(import.meta.url)), "..");
const targetDir = join(rootDir, "qlive-test", "target");

const jar = existsSync(targetDir)
    ? readdirSync(targetDir)
        .filter((name) => /^qlive-test-.*\.jar$/.test(name) && !name.endsWith(".original"))
        .sort()[0]
    : undefined;

if (!jar) {
    console.error("No backend jar found at qlive-test/target/. Run 'pnpm build' once, then 'pnpm dev' again.");
    process.exit(1);
}

const jarPath = join(targetDir, jar);

if (await inUse(PORT)) {
    console.error(
        `Something already serves :${PORT}, and it is not this script.\n` +
        "If that is your IDE's run configuration, leave it be and run 'pnpm dev-ts' instead:\n" +
        "it starts the frontend alone and proxies to whatever holds the port."
    );
    process.exit(1);
}

const newer = newerThanJar();

if (newer) {
    console.error(
        `${jar} is older than ${newer}, so it does not contain that change.\n` +
        "Run './mvnw install -DskipTests' and try again, or set QLIVE_ANY_JAR=1 to run the jar as it is."
    );
    process.exit(1);
}

const child = spawn("java", ["-jar", jarPath], { stdio: "inherit" });

for (const signal of ["SIGINT", "SIGTERM"]) {
    process.on(signal, () => child.kill(signal));
}

child.on("exit", (code, signal) => {
    process.exit(code ?? (signal ? 1 : 0));
});


/**
 * Whether anything is listening on the given port of this machine.
 */
function inUse(port)
{
    return new Promise((resolve) => {
        const socket = createConnection({ host: "127.0.0.1", port });

        socket.on("connect", () => {
            socket.destroy();
            resolve(true);
        });
        socket.on("error", () => resolve(false));
    });
}


/**
 * The first Java source found that is newer than the jar, or null where the jar is current.
 *
 * Only the sources the jar is built from: the frontend is served by Vite in dev, so its copy
 * inside the jar is not what the browser gets and its age says nothing.
 */
function newerThanJar()
{
    if (process.env.QLIVE_ANY_JAR)
    {
        return null;
    }

    const built = statSync(jarPath).mtimeMs;

    for (const module of ["qlive", "qlive-test"])
    {
        const sources = join(rootDir, module, "src", "main");

        if (existsSync(sources))
        {
            const newer = findNewer(sources, built);

            if (newer)
            {
                return newer;
            }
        }
    }

    return null;
}


function findNewer(dir, than)
{
    for (const entry of readdirSync(dir, { withFileTypes: true }))
    {
        const path = join(dir, entry.name);

        if (entry.isDirectory())
        {
            const newer = findNewer(path, than);

            if (newer)
            {
                return newer;
            }
        }
        else if (statSync(path).mtimeMs > than)
        {
            return path.slice(rootDir.length + 1);
        }
    }

    return null;
}
