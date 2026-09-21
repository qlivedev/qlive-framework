// @ts-check
import {defineConfig} from "astro/config";
import starlight from "@astrojs/starlight";

/*
 * The site is served from GitHub Pages under the repository name, so `base` is
 * that name and every link the theme builds carries it. Hand-written links and
 * `<img src>` have to carry it too: `astro dev` answers a page both with and
 * without a trailing slash and does not redirect between them, so a relative
 * `../` resolves one level too high on the slashless URL -- assets 404, and
 * links land outside the base. Write them absolute, `/qlive-framework/...`.
 */
const base = "/qlive-framework";

export default defineConfig({
    site: "https://qlivedev.github.io",
    base,
    // `astro dev --open` lands on the base *without* its trailing slash, and there
    // every relative path in a page resolves one level too high. Open the canonical
    // URL instead. The package script passes no --open of its own on purpose: the
    // bare flag is a boolean and would override this string.
    server: {open: `${base}/`},
    integrations: [
        starlight({
            title: "QLive",
            customCss: ["./src/styles/qlive.css"],
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
            ],
            editLink: {
                baseUrl:
                    "https://github.com/qlivedev/qlive-framework/edit/main/qlive-doc/",
            },
            lastUpdated: true,
        }),
    ],
});
