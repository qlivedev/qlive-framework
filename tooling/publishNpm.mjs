/*
 * Publishes @qlivedev/qlive-ts and @qlivedev/qlive-codegen at the version of the Maven release.
 *
 * The parent POM holds the one version the framework has; the packages carry "0.0.0" and
 * "private": true in the repository, so nothing but this script can publish them, and nothing
 * publishes a version Maven did not release. Run it from the release's checkout, where the POM
 * says what was released -- after release:perform, that is target/checkout:
 *
 *     cd target/checkout && node tooling/publishNpm.mjs --dry-run
 *     cd target/checkout && node tooling/publishNpm.mjs
 *
 * For each package it stamps the version, drops "private", copies LICENSE and NOTICE in and
 * publishes; afterwards it puts every file back the way it found it. pnpm turns the workspace:
 * ranges into real ones while packing, so qlive-codegen's peer range becomes ^<version>.
 *
 * Arguments other than --dry-run go to pnpm publish unchanged, e.g. --otp=123456.
 */
import fs from "node:fs";
import path from "node:path";
import {execFileSync} from "node:child_process";
import {fileURLToPath} from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

// qlive-codegen's peer range names qlive-ts, so qlive-ts has to be on the registry first
const PACKAGES = ["qlive-ts", "qlive-codegen"];
const LEGAL = ["LICENSE", "NOTICE"];

const args = process.argv.slice(2);
const dryRun = args.includes("--dry-run");

function pomVersion()
{
    const pom = fs.readFileSync(path.join(root, "pom.xml"), "utf8");
    const m = /<artifactId>qlive-framework-parent<\/artifactId>\s*<version>([^<]+)<\/version>/.exec(pom);
    if (!m)
    {
        throw new Error("No version for qlive-framework-parent in pom.xml");
    }
    return m[1];
}

function run(cmd, cmdArgs, cwd = root)
{
    console.log(`> ${cmd} ${cmdArgs.join(" ")}`);
    execFileSync(cmd, cmdArgs, {cwd, stdio: "inherit"});
}

const version = pomVersion();
if (version.endsWith("-SNAPSHOT") && !dryRun)
{
    console.error(`pom.xml says ${version}. Publish from the checkout of a release tag, or try with --dry-run.`);
    process.exit(1);
}

const restore = [];

function stash(file)
{
    restore.push(fs.existsSync(file) ? [file, fs.readFileSync(file)] : [file, null]);
}

try
{
    for (const pkg of PACKAGES)
    {
        const dir = path.join(root, pkg);
        const manifestPath = path.join(dir, "package.json");
        stash(manifestPath);

        const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
        manifest.version = version;
        delete manifest.private;
        fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + "\n");

        for (const name of LEGAL)
        {
            const target = path.join(dir, name);
            stash(target);
            fs.copyFileSync(path.join(root, name), target);
        }
    }

    run("pnpm", ["--filter", "@qlivedev/qlive-ts", "run", "build"]);
    run("pnpm", ["--filter", "@qlivedev/qlive-ts", "run", "check-exports"]);

    for (const pkg of PACKAGES)
    {
        // --no-git-checks: the stamped manifests leave the tree dirty, and a release checkout
        // sits on a detached tag rather than on main
        run("pnpm", ["publish", "--no-git-checks", ...args], path.join(root, pkg));
    }
}
finally
{
    for (const [file, content] of restore.reverse())
    {
        if (content === null)
        {
            fs.rmSync(file, {force: true});
        }
        else
        {
            fs.writeFileSync(file, content);
        }
    }
}
