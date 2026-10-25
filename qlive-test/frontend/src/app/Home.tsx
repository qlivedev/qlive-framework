/**
 * The examples, in the order they build on each other. Each view shows one thing; the last one shows them together.
 */
const EXAMPLES = [
    {href: "/app/grid/basic", title: "A plain grid", text: "a query document and the fields to show, nothing else"},
    {href: "/app/grid/columns", title: "Columns", text: "headings, relation paths, render functions, computed columns"},
    {href: "/app/grid/filters", title: "Filters", text: "the filters QLive ships, one per column"},
    {href: "/app/grid/search", title: "A search form", text: "a form and the grid filtering the same rows"},
    {href: "/app/grid/sorting", title: "Sort orders", text: "several fields and expressions, set from outside"},
    {href: "/app/grid/sum", title: "Sorted by a sum", text: "a column computed from two fields, under a date range form"},
    {href: "/app/grid/scalars", title: "Scalar types", text: "every scalar type as the grid shows and filters it"},
    {href: "/app/grid/local", title: "Local rows", text: "rows the view holds, filtered and paged in the browser"},
    {href: "/app/grid/editing", title: "Editing", text: "changing values in place through a working set"},
    {href: "/app/grid/rows", title: "New and deleted rows", text: "creating and deleting through a working set"},
    {href: "/app/grid/watch", title: "Watching", text: "other people's writes marked as they happen"},
    {href: "/app/grid/errors", title: "A failing update", text: "what the grid shows when the server refuses an update"},
    {href: "/app/grid/everything", title: "Everything at once", text: "all of the above in one view"}
];

/**
 * The form examples, from the form nobody wrote to the one written entirely by hand.
 */
const FORMS = [
    {href: "/app/form/derived", title: "A derived form", text: "a row and nothing else; the layout comes from the row"},
    {href: "/app/form/layout", title: "A layout", text: "sections, widgets by name and by override, modes, a component of its own"},
    {href: "/app/form/named", title: "A named layout", text: "a layout the server delivers, changed by the application"},
    {href: "/app/form/manytomany", title: "A many-to-many field", text: "associated rows listed, removed and searched to add"},
    {href: "/app/form/invariants", title: "Rules on a type", text: "invariants checked before a save sends a row, and shown at the field they blame"},
    {href: "/app/form/reactions", title: "Reactions", text: "a form setting modes and values when a field changes, loses focus, or breaks a rule"},
    {href: "/app/bar/edit", title: "A hand-written form", text: "the application's own choice of fields, with the conflict buttons"}
];

/**
 * Actions on stored rows, guarded by the server's rules.
 */
const ACTIONS = [
    {href: "/app/bar/approve", title: "Approving bars", text: "an action on one row, on several, and on none but input"},
    {href: "/app/bar/review", title: "Reviewing bars", text: "a form's row as an action's subject, and a draft quoted unsaved"},
    {href: "/app/bar/link", title: "Linking a baz", text: "an action over two subjects of different types, set apart"}
];

/**
 * Everything else.
 */
const OTHERS = [
    {href: "/app/i18n", title: "Translation", text: "texts in the locale the browser asks for, and one nobody translated"}
];

export default function Home()
{
    return (
        <div>
            <h1>Examples</h1>
            <h2>DataGrid</h2>
            <ul className="nav-list">
                {
                    EXAMPLES.map(example => (
                        <li key={ example.href }>
                            <a href={ example.href }>{ example.title }</a> -- { example.text }
                        </li>
                    ))
                }
            </ul>
            <h2>Forms</h2>
            <ul className="nav-list">
                {
                    FORMS.map(example => (
                        <li key={ example.href }>
                            <a href={ example.href }>{ example.title }</a> -- { example.text }
                        </li>
                    ))
                }
            </ul>
            <h2>Actions</h2>
            <ul className="nav-list">
                {
                    ACTIONS.map(example => (
                        <li key={ example.href }>
                            <a href={ example.href }>{ example.title }</a> -- { example.text }
                        </li>
                    ))
                }
            </ul>
            <h2>Other</h2>
            <ul className="nav-list">
                {
                    OTHERS.map(example => (
                        <li key={ example.href }>
                            <a href={ example.href }>{ example.title }</a> -- { example.text }
                        </li>
                    ))
                }
            </ul>
        </div>
    );
}
