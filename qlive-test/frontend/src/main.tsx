// QLive ships its stylesheet as a separate artifact rather than pulling it in
// from the JS, so the application controls where it lands in the cascade.
// Import it before your own styles: everything in it sits in @layer qlive,
// which anything unlayered overrides regardless of specificity.
import "@qlivedev/qlive-ts/styles.css";
import "./style.css"
import { registerWidget, startup } from "@qlivedev/qlive-ts";
import ViteDevHome from "./component/ViteDevHome";
import { Stepper, TextArea } from "./component/widgets";

// The three accounts the backup seeds app_user with. Plaintext here is fine -- QuickLogin exists for
// exactly this kind of throwaway dev/test account and none of these guard anything real.
const quickLoginUsers = [
    { login: "admin", password: "admin" },
    { login: "userA", password: "userA" },
    { login: "userB", password: "userB" }
]

document.addEventListener("DOMContentLoaded", async () => {

    // Vite resolves import.meta.glob() at build time and only accepts literal patterns, so the view lookup
    // has to be declared here rather than inside QLive. Without `eager` the map holds loader functions --
    // each view becomes its own chunk and is fetched the first time loadView() asks for it.
    await startup({
        views: import.meta.glob("./app/**/*.tsx"),
        root: () => <ViteDevHome quickLoginUsers={ quickLoginUsers }/>,
        init: async config => {
            // A layout written in TSX holds its widgets; a name is for a layout that can't, such as one the
            // server delivers. app/form/Layout.tsx refers to this one by name all the same, to show it.
            registerWidget("textarea", TextArea)

            // Applies wherever a form shows Foo.num without naming a widget: the derived form, a layout, and a
            // hand-written <Field path="num"/> alike.
            config.widgets = [
                { domainType: "Foo", path: "num", widget: Stepper }
            ]
        }
    });
});
