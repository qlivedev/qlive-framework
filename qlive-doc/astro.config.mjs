// @ts-check
import {defineConfig} from "astro/config";
import starlight from "@astrojs/starlight";

/*
 * The site is served from GitHub Pages under the repository name, so `base` is
 * that name and every link the theme builds carries it. Relative links between
 * pages stay correct either way; absolute ones would not.
 */
export default defineConfig({
    site: "https://quinscape.github.io",
    base: "/qlive-framework",
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
