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
        </div>
    );
}
