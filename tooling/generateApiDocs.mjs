/*
 * Generates the API reference pages of qlive-doc from the public declarations of
 * @quinscape/qlive-ts.
 *
 * The doc comments in the source are the reference text. Keeping a second copy of
 * them by hand means the copy is wrong the first time a signature changes and
 * nobody remembers the page, so the pages under qlive-doc/src/content/docs/api are
 * written from the build output instead and never edited: the fix for bad text on
 * one of them is the doc comment it came from.
 *
 * Reads dist/index.d.ts rather than the sources, because the dts bundler has
 * already decided what is public. What ends up in that file is exactly what an
 * application can see, so a page generated from it cannot document something
 * unreachable or miss something reachable.
 *
 * Which export lands on which page is tooling/apiTopics.json, not a rule derived
 * from the module layout -- see the comment at the top of that file.
 *
 * A symbol whose page wants more than its doc comment says takes an optional
 * prose fragment, qlive-doc/src/content/apiExtra/<symbol path>.md -- see
 * readExtras below.
 *
 * Reads the build output, so run `pnpm --filter @quinscape/qlive-ts build` first.
 *
 * Usage: node tooling/generateApiDocs.mjs [--check]
 *
 * --check writes nothing and fails if the committed pages differ from what the
 * current build would produce, which is the form CI wants.
 */
import fs from "node:fs";
import path from "node:path";
import {fileURLToPath} from "node:url";

const repoRoot = path.dirname(fileURLToPath(new URL("../package.json", import.meta.url)));
const dtsPath = path.join(repoRoot, "qlive-ts", "dist", "index.d.ts");
const indexPath = path.join(repoRoot, "qlive-ts", "src", "index.ts");
const topicsPath = path.join(repoRoot, "tooling", "apiTopics.json");
const outDir = path.join(repoRoot, "qlive-doc", "src", "content", "docs", "api");
const extraDir = path.join(repoRoot, "qlive-doc", "src", "content", "apiExtra");

const check = process.argv.includes("--check");

// Column-0 declarations are the top-level ones; the bundler indents everything
// that belongs to a body.
const DECLARATION =
    /^(?:declare )?(function|class|abstract class|const|let|var|namespace|type|interface|enum)\s+([A-Za-z_$][\w$]*)/;

const CHUNK_IMPORT = /^import [\s\S]*? from "(\.\/[^"]+)\.js";?\s*$/;

// Column 0 again, and for the same reason. The indented export lists belong to a
// namespace block, and a chunk has one of its own re-exporting under one-letter
// linkage names for whichever entry point imports it. Neither is the surface an
// application sees.
const EXPORT_LIST = /^export \{(.*)\};?\s*$/;

// `export * as FilterDSL from "./FilterDSL"` becomes `declare namespace
// FilterDSL_d_exports` in the bundle, named after the module file rather than the
// namespace an application sees. Read index.ts to get from one to the other, so
// that renaming a namespace export needs no edit here.
const NAMESPACE_REEXPORT = /export\s+\*\s+as\s+([A-Za-z_$][\w$]*)\s+from\s+"([^"]+)"/g;

/**
 * The declaration file and every chunk it draws declarations from. One level deep
 * while the chunks are leaves, the same assumption checkExports.mjs makes.
 */
function declarationFiles(entry)
{
    const dir = path.dirname(entry);
    const chunks = fs.readFileSync(entry, "utf8").split("\n")
        .map(line => CHUNK_IMPORT.exec(line))
        .filter(match => match !== null)
        .map(match => path.join(dir, match[1] + ".d.ts"))
        .filter(file => fs.existsSync(file));

    return [entry, ...new Set(chunks)];
}

/**
 * Every name the entry point exports, spelled as an application says it -- the
 * name after an `as`, which is what the topic map and the headings use.
 *
 * checkExports.mjs asks the other half of the question, whether a declaration
 * that reached the bundle is nameable at all, and its DELIBERATELY NOT EXPORTED
 * list is the answer for the ones that are not. Reading the export list rather
 * than the declarations therefore needs no second copy of that list: what stays
 * internal on purpose is not exported, so it never arrives here.
 */
function exportedNames(entry)
{
    const exported = new Set();

    for (const line of fs.readFileSync(entry, "utf8").split("\n"))
    {
        const list = EXPORT_LIST.exec(line);
        if (!list)
        {
            continue;
        }

        for (const item of list[1].split(","))
        {
            const name = /([A-Za-z_$][\w$]*)\s*$/.exec(item.trim());
            if (name)
            {
                exported.add(name[1]);
            }
        }
    }

    return exported;
}

/**
 * Strips the comment framing off a JSDoc block, leaving the markdown inside it.
 * The bodies already read as prose and carry their own fenced examples, so the
 * only thing standing between them and a page is the ` * ` down the left.
 */
function stripComment(lines)
{
    return lines
        .slice(1, -1)
        .map(line => line.replace(/^\s*\* ?/, ""))
        .join("\n")
        .trim();
}

/**
 * Splits a doc comment into its prose and its tags. A tag runs until the next tag,
 * so that a wrapped @param keeps the rest of its sentence.
 */
function parseDoc(text)
{
    const prose = [];
    const tags = [];

    for (const line of text.split("\n"))
    {
        const tag = /^@(\w+)\s*(.*)$/.exec(line.trim());
        if (tag)
        {
            tags.push({tag: tag[1], text: tag[2]});
        }
        else if (tags.length > 0)
        {
            tags[tags.length - 1].text += " " + line.trim();
        }
        else
        {
            prose.push(line);
        }
    }

    return {prose: prose.join("\n").trim(), tags};
}

/**
 * Whether a doc comment marks what it describes as framework-internal: reached
 * from the framework's other modules, and so not expressible as `private`, but no
 * part of what an application is meant to call.
 *
 * `@internal` is TypeScript's own spelling of this, which is what a reader of the
 * declarations will take it for. The tag stays in the shipped .d.ts on purpose --
 * somebody who reaches one anyway should find that out at the call site.
 */
function isInternal(doc)
{
    return !!doc && /^\s*@internal\b/m.test(doc);
}

/**
 * Every top-level declaration of one file, with the doc comment above it, and the
 * member list of the ones that have a body.
 */
function parseDeclarations(text)
{
    const lines = text.split("\n");
    const declarations = new Map();
    const namespaces = new Map();

    let doc = null;

    for (let i = 0; i < lines.length; i++)
    {
        const line = lines[i];

        if (/^\/\*\*/.test(line))
        {
            const start = i;
            while (i < lines.length && !/\*\/\s*$/.test(lines[i]))
            {
                i++;
            }
            doc = stripComment(lines.slice(start, i + 1));
            continue;
        }

        const declaration = DECLARATION.exec(line);
        if (!declaration)
        {
            // Only a comment directly above a declaration documents it.
            if (line.trim() !== "")
            {
                doc = null;
            }
            continue;
        }

        const [, kind, name] = declaration;
        const body = [line];

        // A body closes with `}` for a class or interface and `};` for an object
        // type alias, both at column 0; anything else is a one-liner that ends
        // where its semicolon does.
        if (line.includes("{") && !line.trimEnd().endsWith(";"))
        {
            while (i + 1 < lines.length && !/^\};?$/.test(body[body.length - 1]))
            {
                i++;
                body.push(lines[i]);
            }
        }
        else
        {
            while (i + 1 < lines.length && !body[body.length - 1].trimEnd().endsWith(";"))
            {
                i++;
                body.push(lines[i]);
            }
        }

        if (kind === "namespace")
        {
            namespaces.set(name, body.join("\n").match(/export \{([^}]*)\}/)?.[1]
                .split(",").map(entry => entry.trim()).filter(Boolean) ?? []);
            doc = null;
            continue;
        }

        declarations.set(name, {
            name,
            kind,
            doc,
            header: body[0].replace(/^declare /, "").replace(/\s*\{\s*$/, ""),
            body,
            members: body.length > 1 ? parseMembers(body) : []
        });

        doc = null;
    }

    return {declarations, namespaces};
}

/**
 * The members of a declaration body that an application can reach. A private
 * member is in the bundle because a class declaration names its own fields, not
 * because anyone may call it.
 */
function parseMembers(body)
{
    const members = [];
    let doc = null;

    for (let i = 1; i < body.length - 1; i++)
    {
        const line = body[i];
        if (!/^  \S/.test(line))
        {
            continue;
        }

        if (/^  \/\*\*/.test(line))
        {
            const start = i;
            while (i < body.length && !/\*\/\s*$/.test(body[i]))
            {
                i++;
            }
            doc = stripComment(body.slice(start, i + 1));
            continue;
        }

        if (/^  (private|protected)\b/.test(line))
        {
            doc = null;
            continue;
        }

        const signature = [line];
        while (!signature[signature.length - 1].trimEnd().endsWith(";")
            && !signature[signature.length - 1].trimEnd().endsWith(",")
            && i + 1 < body.length - 1)
        {
            i++;
            signature.push(body[i]);
        }

        const name = /^  (?:readonly |get |set |static )*([A-Za-z_$][\w$]*|constructor|\[[^\]]+\])/.exec(line)?.[1];
        if (name && !isInternal(doc))
        {
            members.push({name, doc, signature: signature.map(l => l.slice(2)).join("\n")});
        }
        doc = null;
    }

    return members;
}

/*
 * The operators are the one part of the public API that never reaches the
 * declarations. They are installed on the node prototypes at runtime from the
 * maps below, so a .d.ts knows the nodes but not a single method name on them --
 * which is why this reads a source file where the rest of the generator reads the
 * build. The maps are the only record there is, and a table kept by hand beside
 * them is a table that drifts.
 */
const OPERATOR_MAP = name =>
    new RegExp(`const ${name} = \\{([\\s\\S]*?)\\} as const;`);

/**
 * The operator names of one `as const` map, with the number of operands each
 * takes besides the field or condition it is called on.
 */
function parseOperators(source, constName)
{
    const body = OPERATOR_MAP(constName).exec(source);
    if (!body)
    {
        throw new Error(`${constName} is not a map in the FilterDSL source`);
    }

    return [...body[1].matchAll(/"([^"]+)":\s*(\d+)/g)]
        .map(match => ({name: match[1], operands: Number(match[2])}))
        .sort((a, b) => a.name.localeCompare(b.name));
}

/**
 * The operator tables of a topic, each a lookup list rather than prose: the
 * question they answer is whether a name exists and how many operands it takes.
 */
function renderOperators(spec, repoRoot)
{
    const source = fs.readFileSync(path.join(repoRoot, spec.source), "utf8");
    const out = [];

    for (const table of spec.tables)
    {
        out.push(`## ${table.title}`, "");
        if (table.lead)
        {
            out.push(table.lead, "");
        }
        out.push("| Operator | Operands |", "|---|---|");
        for (const operator of parseOperators(source, table.const))
        {
            out.push(`| \`${operator.name}\` | ${operator.operands} |`);
        }
        out.push("");
    }

    return out.join("\n");
}

/**
 * The optional prose fragments beside the generated pages, keyed by the symbol
 * path they are appended to as it is spelled on the page: `startup.md`,
 * `FilterDSL.field.md`, `QueryDocument.commit.md` -- no parentheses on a function.
 *
 * A fragment carries what belongs on the page but not in an IDE hover: a platform
 * difference worth restating where it is looked up, a clarification that would
 * crowd a call site. The doc comment stays the contract, and the fragment is
 * pulled from the docs side, so nothing in the shipped declarations points at a
 * file the package does not contain.
 *
 * Reach for one only after asking whether the text is really reference. A worked
 * example is how-to and a rationale is explanation, and both have a page of their
 * own that these link to.
 */
function readExtras()
{
    if (!fs.existsSync(extraDir))
    {
        return new Map();
    }

    const extras = new Map();
    for (const file of fs.readdirSync(extraDir).filter(name => name.endsWith(".md")))
    {
        const text = fs.readFileSync(path.join(extraDir, file), "utf8").trim();

        // The heading structure is the generator's, and Starlight builds the page
        // table of contents out of it.
        if (/^#{1,6} /m.test(text))
        {
            throw new Error(`apiExtra/${file}: a fragment is prose under a heading the generator wrote, so it cannot carry one of its own`);
        }

        extras.set(file.slice(0, -".md".length), text);
    }

    return extras;
}

const extras = readExtras();
const extrasUsed = new Set();

/**
 * The fragment appended to one symbol's section, if there is one.
 */
function extraFor(symbol)
{
    const text = extras.get(symbol);
    if (!text)
    {
        return [];
    }

    extrasUsed.add(symbol);
    return [text, ""];
}

/**
 * Whether a declaration reads better as a header plus a member list than as one
 * block. A class or an interface does -- its members are looked up one at a time
 * and carry their own doc comments. An object type alias does not: it is small,
 * and its body with the comments left in is the clearest thing to show.
 */
function splitIntoMembers(declaration)
{
    return declaration.members.length > 0
        && ["class", "abstract class", "interface"].includes(declaration.kind);
}

/**
 * How an export is titled on the page. A value is shown as it is called, a type as
 * it is named.
 */
function heading(declaration)
{
    return declaration.kind === "function"
        ? declaration.name + "()"
        : declaration.name;
}

const KIND_LABEL = {
    "function": "function",
    "class": "class",
    "abstract class": "abstract class",
    "const": "constant",
    "let": "variable",
    "var": "variable",
    "type": "type",
    "interface": "interface",
    "enum": "enum",
    "namespace": "namespace"
};

function renderTags(tags)
{
    const out = [];
    const params = tags.filter(t => t.tag === "param");

    if (params.length > 0)
    {
        out.push("**Parameters**\n");
        out.push("| | |");
        out.push("|---|---|");
        for (const param of params)
        {
            const [, name, rest] = /^(\S+)\s*(.*)$/.exec(param.text) ?? [, param.text, ""];
            out.push(`| \`${name}\` | ${rest.replace(/\|/g, "\\|")} |`);
        }
        out.push("");
    }

    for (const tag of tags)
    {
        if (tag.tag === "returns" || tag.tag === "return")
        {
            out.push(`**Returns** ${tag.text}\n`);
        }
        else if (tag.tag === "throws")
        {
            out.push(`**Throws** ${tag.text}\n`);
        }
    }

    return out;
}

/**
 * Every name a page gave a section of its own, recorded while the pages are
 * written rather than derived from the topic map: what the map names and what a
 * page ends up holding are the two things the check below compares.
 */
const rendered = new Set();

/**
 * One export as a section: what it is, how it is spelled, and what its doc comment
 * says about it.
 */
function renderExport(declaration)
{
    rendered.add(declaration.name);

    const out = [`## ${heading(declaration)}`, ""];
    const members = splitIntoMembers(declaration) ? declaration.members : [];

    out.push(`<span class="api-kind">${KIND_LABEL[declaration.kind] ?? declaration.kind}</span>`, "");
    out.push("```ts", members.length > 0 ? declaration.header : declaration.body.join("\n"), "```", "");

    if (!declaration.doc)
    {
        out.push(":::note[Undocumented]", "This export carries no doc comment in the source.", ":::", "");
    }

    const {prose, tags} = declaration.doc ? parseDoc(declaration.doc) : {prose: "", tags: []};
    if (prose)
    {
        out.push(prose, "");
    }
    out.push(...extraFor(declaration.name), ...renderTags(tags));

    for (const member of members)
    {
        out.push(`### ${declaration.name}.${member.name}`, "");
        out.push("```ts", member.signature, "```", "");
        const {prose, tags} = member.doc ? parseDoc(member.doc) : {prose: "", tags: []};
        if (prose)
        {
            out.push(prose, "");
        }
        out.push(...extraFor(`${declaration.name}.${member.name}`), ...renderTags(tags));
    }

    return out.join("\n");
}

/**
 * A namespace is rendered as the list of what it holds, each entry the declaration
 * it resolves to. The names are claimed short on purpose -- `field`, `and`, `not`
 * -- so the namespace prefix stays on every heading.
 */
function renderNamespace(name, memberNames, declarations)
{
    rendered.add(name);

    const out = [`## ${name}`, "", `<span class="api-kind">namespace</span>`, ""];
    out.push(`Imported as a namespace, and re-exported member by member from a second entry point.`, "");
    out.push(...extraFor(name));

    for (const member of memberNames)
    {
        const declaration = declarations.get(member);
        if (!declaration)
        {
            throw new Error(`${name}: the bundle exports "${member}" from the namespace but declares it nowhere`);
        }

        if (isInternal(declaration.doc))
        {
            continue;
        }

        rendered.add(declaration.name);

        out.push(`### ${name}.${heading(declaration)}`, "");
        const inner = splitIntoMembers(declaration) ? declaration.members : [];
        out.push("```ts", inner.length > 0
            ? declaration.header
            : declaration.body.join("\n"), "```", "");

        const {prose, tags} = declaration.doc ? parseDoc(declaration.doc) : {prose: "", tags: []};
        if (prose)
        {
            out.push(prose, "");
        }
        out.push(...extraFor(`${name}.${declaration.name}`), ...renderTags(tags));

        for (const member of inner)
        {
            out.push(`\`${member.signature.replace(/;$/, "")}\`` + (member.doc ? ` -- ${parseDoc(member.doc).prose.replace(/\n/g, " ")}` : ""), "");
        }
    }

    return out.join("\n");
}

function renderPage(topic, order, resolve, externals)
{
    const extrasBefore = extrasUsed.size;

    const sections = topic.members.map(name =>
    {
        if (externals[name])
        {
            rendered.add(name);

            return [
                `## ${name}`, "",
                `<span class="api-kind">re-export</span>`, "",
                `Re-exported from [\`${externals[name]}\`](https://www.npmjs.com/package/${externals[name]}), ` +
                `so that an application names the same copy the framework converts into.`, ""
            ].join("\n");
        }

        const found = resolve(name);
        if (!found)
        {
            throw new Error(`${topic.slug}: "${name}" is in the topic map but not in the build output`);
        }

        if (!found.namespace && isInternal(found.doc))
        {
            throw new Error(`${topic.slug}: "${name}" is in the topic map and marked @internal, which cannot both be true`);
        }

        return found.namespace
            ? renderNamespace(name, found.namespace, found.declarations)
            : renderExport(found);
    });

    if (topic.operatorTables)
    {
        sections.push(renderOperators(topic.operatorTables, repoRoot));
    }

    const banner = ["<!-- Generated by tooling/generateApiDocs.mjs -- edit the doc comments in",
        "     qlive-ts/src instead, then run `pnpm docs:api`."];

    // Named only where there is one to find, so that the usual page sends the
    // reader straight to the source and nothing else.
    if (extrasUsed.size > extrasBefore)
    {
        banner.push("     Paragraphs that are in no doc comment come from qlive-doc/src/content/apiExtra.");
    }
    banner[banner.length - 1] += " -->";

    const front = [
        "---",
        `title: ${topic.title}`,
        `description: ${topic.description}`,
        // The edit link would point at a file that is overwritten on the next
        // build; the doc comment it came from is the thing to edit.
        "editUrl: false",
        "sidebar:",
        `  order: ${order}`,
        "---",
        "",
        ...banner,
        ""
    ];

    if (topic.narrative)
    {
        front.push(`:::tip[Start here]`,
            `[${topic.title} in the reference](${topic.narrative}) explains how these fit together.`,
            ":::", "");
    }

    return front.join("\n") + "\n" + sections.join("\n") + "\n";
}

// --- main ------------------------------------------------------------------

if (!fs.existsSync(dtsPath))
{
    console.error(`No declaration file at ${dtsPath} -- run the qlive-ts build first.`);
    process.exit(2);
}

const config = JSON.parse(fs.readFileSync(topicsPath, "utf8"));

const declarations = new Map();
const namespaces = new Map();

for (const file of declarationFiles(dtsPath))
{
    const parsed = parseDeclarations(fs.readFileSync(file, "utf8"));
    for (const [name, declaration] of parsed.declarations)
    {
        if (!declarations.has(name))
        {
            declarations.set(name, declaration);
        }
    }
    for (const [name, members] of parsed.namespaces)
    {
        namespaces.set(name, members);
    }
}

// The bundle names a namespace after its module, so map the public name onto it.
const namespaceAlias = new Map();
for (const match of fs.readFileSync(indexPath, "utf8").matchAll(NAMESPACE_REEXPORT))
{
    namespaceAlias.set(match[1], path.basename(match[2]) + "_d_exports");
}

function resolve(name)
{
    const alias = namespaceAlias.get(name);
    if (alias && namespaces.has(alias))
    {
        return {namespace: namespaces.get(alias), declarations};
    }
    return declarations.get(name) ?? null;
}

const pages = config.topics.map((topic, index) => ({
    file: path.join(outDir, topic.slug + ".md"),
    text: renderPage(topic, index + 1, resolve, config.external)
}));

// A fragment is invisible from the source it belongs to, so somebody renaming an
// export does not see it. Unappended means wrong, and it has to say so here or it
// never says so at all.
const orphaned = [...extras.keys()].filter(symbol => !extrasUsed.has(symbol));
if (orphaned.length > 0)
{
    console.error("These prose fragments name a symbol no page has a section for:\n");
    for (const symbol of orphaned)
    {
        console.error("  " + path.relative(repoRoot, path.join(extraDir, symbol + ".md")));
    }
    console.error("\nRename the file after whatever the symbol is called now, or delete it.");
    process.exit(2);
}

// The bargain tooling/apiTopics.json describes, in the direction the topic map
// cannot keep on its own: it fails on a member the build output does not have,
// and this fails on an export no page took.
const undocumented = [...exportedNames(dtsPath)].filter(name =>
{
    if (rendered.has(name))
    {
        return false;
    }

    // An @internal export is accounted for by the tag: it says the name is not for
    // applications, which is the whole of what a page would have had to say.
    const found = resolve(name);
    return !(found && !found.namespace && isInternal(found.doc));
});
if (undocumented.length > 0)
{
    console.error(`${undocumented.length} exports of @quinscape/qlive-ts are on no page:\n`);
    for (const name of undocumented)
    {
        console.error("  " + name);
    }
    console.error("\nGive each one a topic in tooling/apiTopics.json, mark it @internal, or stop exporting it.");
    process.exit(2);
}

if (check)
{
    const stale = pages.filter(page =>
        !fs.existsSync(page.file) || fs.readFileSync(page.file, "utf8") !== page.text);

    if (stale.length === 0)
    {
        console.log(`${pages.length} API pages are up to date.`);
        process.exit(0);
    }

    console.log("These API pages no longer match the build output:\n");
    for (const page of stale)
    {
        console.log("  " + path.relative(repoRoot, page.file));
    }
    console.log("\nRun `pnpm docs:api` to regenerate them.");
    process.exit(1);
}

fs.mkdirSync(outDir, {recursive: true});
for (const page of pages)
{
    fs.writeFileSync(page.file, page.text);
}

console.log(`Wrote ${pages.length} API pages to ${path.relative(repoRoot, outDir)}.`);
