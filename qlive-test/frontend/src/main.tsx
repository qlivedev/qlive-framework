// QLive ships its stylesheet as a separate artifact rather than pulling it in
// from the JS, so the application controls where it lands in the cascade.
// Import it before your own styles: everything in it sits in @layer qlive,
// which anything unlayered overrides regardless of specificity.
import "@quinscape/qlive-ts/styles.css";
import "./style.css"
import { startup } from "@quinscape/qlive-ts";
import ViteDevHome from "./component/ViteDevHome";

document.addEventListener("DOMContentLoaded", async () => {

    // Vite resolves import.meta.glob() at build time and only accepts literal patterns, so the view lookup
    // has to be declared here rather than inside QLive. Without `eager` the map holds loader functions --
    // each view becomes its own chunk and is fetched the first time loadView() asks for it.
    await startup({
        views: import.meta.glob("./app/**/*.tsx"),
        root: ViteDevHome
    });
});
