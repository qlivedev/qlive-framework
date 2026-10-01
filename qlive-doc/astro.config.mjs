// @ts-check
import {defineConfig} from "astro/config";
import starlight from "@astrojs/starlight";
import react from "@astrojs/react";
import {fileURLToPath} from "node:url";

/*
 * The site is served from GitHub Pages under the repository name, so `base` is
 * that name and every link the theme builds carries it. Hand-written links and
 * `<img src>` have to carry it too: `astro dev` answers a page both with and
 * without a trailing slash and does not redirect between them, so a relative
 * `../` resolves one level too high on the slashless URL -- assets 404, and
 * links land outside the base. Write them absolute, `/qlive-framework/...`.
 */
const base = "/qlive-framework";

/*
 * The demos run views of qlive-test on recorded fixtures, compiled from source:
 * qlive-ts the way qlive-test's own dev server aliases it, and the views from
 * qlive-test/frontend/src/app. Both live outside this workspace, so their
 * dependencies come from the repository's install -- `pnpm install` at the root
 * has to have run (see README.md).
 */
const repoRoot = fileURLToPath(new URL("..", import.meta.url));
const qliveTsDir = repoRoot + "qlive-ts/";

// Subpath entries first: a string alias matches on prefix, so the bare one would swallow them.
const qliveTsAliases = {
    "@qlivedev/qlive-ts/styles.css": qliveTsDir + "src/styles/qlive.css",
    "@qlivedev/qlive-ts/filter": qliveTsDir + "src/filter.ts",
    "@qlivedev/qlive-ts": qliveTsDir + "src/index.ts",
};

export default defineConfig({
    site: "https://qlivedev.github.io",
    base,
    // `astro dev --open` lands on the base *without* its trailing slash, and there
    // every relative path in a page resolves one level too high. Open the canonical
    // URL instead. The package script passes no --open of its own on purpose: the
    // bare flag is a boolean and would override this string.
    server: {open: `${base}/`},
    vite: {
        resolve: {
            alias: qliveTsAliases,
            // qlive-ts and the views resolve React from the repository's install, the islands from this
            // one's. Two copies of React means hooks that fail in the first component that calls one.
            dedupe: ["react", "react-dom"],
        },
        server: {
            fs: {allow: [repoRoot]},
        },
    },
    integrations: [
        starlight({
            title: "QLive",
            // Imported, not served from public/: Starlight resolves these
            // paths from the project root and hashes the files into the
            // build. The title text stays in the header for screen readers.
            logo: {
                light: "./src/assets/qlive-logo-header-light.svg",
                dark: "./src/assets/qlive-logo-header-dark.svg",
                replacesTitle: true,
            },
            customCss: ["./src/styles/qlive.css", "./src/styles/site.css"],
            description:
                "Documentation for building applications on the QLive framework.",
            social: [
                {
                    icon: "github",
                    label: "GitHub",
                    href: "https://github.com/qlivedev/qlive-framework",
                },
            ],
            // One group per Diataxis quadrant, in the order a reader meets
            // them: understand, then do, then look up. Only the labels are
            // named here -- Starlight would otherwise use the bare directory
            // name -- and the pages inside each group still place themselves
            // with `sidebar.order` in their frontmatter, numbered from 1
            // within the group. Adding a page stays a one-file change; only a
            // new quadrant is an edit here. The tutorial group goes on top
            // once there is a generated application to write it against.
            sidebar: [
                {label: "Explanation", items: [{autogenerate: {directory: "explanation"}}]},
                {label: "How-to guides", items: [{autogenerate: {directory: "how-to"}}]},
                // Written from the qlive-ts declarations by
                // tooling/generateApiDocs.mjs. Its own tree rather than a
                // directory inside `reference`, so that "nothing in here is
                // hand-edited" is a property of the whole directory and a
                // regeneration can simply overwrite it.
                {label: "API", items: [{autogenerate: {directory: "api"}}]},
                {label: "Reference", items: [{autogenerate: {directory: "reference"}}]},
                // Views of qlive-test running in the page on recorded fixtures, with their source.
                {label: "Demos", items: [{autogenerate: {directory: "demo"}}]},
            ],
            editLink: {
                baseUrl:
                    "https://github.com/qlivedev/qlive-framework/edit/main/qlive-doc/",
            },
            lastUpdated: true,
        }),
        react(),
    ],
});
