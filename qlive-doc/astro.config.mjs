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
    site: "https://quinscape.github.io",
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
                    href: "https://github.com/quinscape/qlive-framework",
                },
            ],
            // No sidebar config on purpose: Starlight autogenerates one from
            // everything in src/content/docs, and each page places itself with
            // `sidebar.order` in its frontmatter. Adding a page is therefore a
            // one-file change rather than an edit here as well.
            editLink: {
                baseUrl:
                    "https://github.com/quinscape/qlive-framework/edit/main/qlive-doc/",
            },
            lastUpdated: true,
        }),
    ],
});
