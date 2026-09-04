/*
 * Reports every type or value that the bundled declaration file declares but
 * does not export, and that src/index.ts does not acknowledge as internal.
 *
 * A dts bundler inlines whatever a public signature refers to, so a type that
 * index.ts forgot to re-export still lands in dist/index.d.ts -- structurally
 * usable, but impossible for an application to name. Those are the entries this
 * finds. Each one is either an export missing from src/index.ts or a deliberate
 * omission belonging on the "DELIBERATELY NOT EXPORTED" list at the end of that
 * file, which this reads as its allow list.
 *
 * Reads the build output, so run `pnpm build` (or `npx tsdown`) first.
 *
 * Usage: node tooling/checkExports.mjs [path/to/index.d.ts]
 */
import fs from "node:fs";
import path from "node:path";
import {fileURLToPath} from "node:url";

const packageDir = path.dirname(fileURLToPath(new URL("../package.json", import.meta.url)));
const dtsPath = process.argv[2] ?? path.join(packageDir, "dist", "index.d.ts");
const indexPath = path.join(packageDir, "src", "index.ts");

const INTERNAL_MARKER = "DELIBERATELY NOT EXPORTED";

if (!fs.existsSync(dtsPath))
{
    console.error(`No declaration file at ${dtsPath} -- run the build first.`);
    process.exit(2);
}

// Only column-0 declarations are top-level; anything nested is indented by the
// bundler and belongs to a namespace or a member list.
const DECLARATION = /^(?:declare (?:function|class|const|let|var|namespace|abstract class)|type|interface|enum|declare enum)\s+([A-Za-z_$][\w$]*)/;

// Indented too: a bundled namespace re-exports its members from inside its own
// block, and those are nameable as members of the exported namespace.
const EXPORT_LIST = /^\s*export \{(.*)\};?\s*$/;

const declared = [];
const exported = new Set();

for (const line of fs.readFileSync(dtsPath, "utf8").split("\n"))
{
    const declaration = DECLARATION.exec(line);
    if (declaration)
    {
        declared.push(declaration[1]);
        continue;
    }

    const exportList = EXPORT_LIST.exec(line);
    if (exportList)
    {
        for (const entry of exportList[1].split(","))
        {
            // "type Foo", "Foo as Bar" -- a declaration is matched against the
            // local name, so keep the part before any "as".
            const local = entry.trim().replace(/^type\s+/, "").split(/\s+as\s+/)[0];
            if (local)
            {
                exported.add(local);
            }
        }
    }
}

/**
 * Every identifier named in the "DELIBERATELY NOT EXPORTED" section of
 * src/index.ts. Read as prose rather than as a structured list so that the
 * section can keep explaining itself -- what matters here is only whether a
 * name was considered.
 */
function acknowledgedInternals()
{
    const source = fs.readFileSync(indexPath, "utf8");
    const start = source.indexOf(INTERNAL_MARKER);

    return new Set(
        start < 0 ? [] : source.slice(start).match(/[A-Za-z_$][\w$]*/g) ?? []
    );
}

const internal = acknowledgedInternals();
const leaked = declared.filter(name => !exported.has(name) && !internal.has(name));

const relative = path.relative(process.cwd(), dtsPath);

if (leaked.length === 0)
{
    const kept = declared.filter(name => !exported.has(name)).length;

    console.log(
        `${relative}: ${declared.length} declarations, ${declared.length - kept} exported, ` +
        `${kept} kept internal on purpose. Nothing unaccounted for.`
    );
    process.exit(0);
}

console.log(`${relative}: ${leaked.length} of ${declared.length} declarations are neither exported nor acknowledged\n`);
for (const name of leaked)
{
    console.log("  " + name);
}
console.log(
    "\nEach of these reaches the public API without being nameable. Either export it from\n" +
    `src/index.ts, or record why it stays internal under "${INTERNAL_MARKER}" there.`
);
process.exit(1);
