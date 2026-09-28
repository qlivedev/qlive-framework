# DataGrid feature list

Status: a checklist, not a design. Written 2026-09-27 from a survey of
the Automaton DataGrid (`automaton-js/src/ui/datagrid`, used throughout
`automaton-test`).

Everything the Automaton grid does that QLive's grid has to at least
consider, in QLive terms. Its user column configuration and its
drag-and-drop row reordering are left out on purpose. Where QLive has no
equivalent yet, the entry says so under "Missing in QLive". How the grid
is built to cover this list is `datagrid.md`.

## Data source

- **Query document as the value.** The grid takes the query document
  snapshot a view got from `useInjection()` or `useQueryDocument()`.
  From it the grid reads `type`, `rows`, `rowCount` and `config`
  (offset, pageSize, condition, sortFields), and it changes paging,
  sorting and filtering through `update()` with a `QueryConfigDelta`.
  It never builds a query of its own.
- **Local rows.** Rows the client already holds, displayed without a
  server round trip. In QLive that is a hand-built `QueryDocument`,
  which carries no query and so cannot `update()`. Paging, sorting and
  filtering local rows need a client-side evaluator (see "Missing in
  QLive").
- **Per-type defaults apply.** A document already arrives with the page
  size and sort order the row type declares
  (`QueryConfigMetadataProvider`). The grid shows that config and
  doesn't impose its own.

## Component awareness

The grid shares its document with the rest of the view. Which part of
the condition belongs to the grid depends on the condition's shape.

- **Composed condition: the grid owns one component.** If
  `isComposedComponentExpression(condition)` is true (a logical
  condition whose operands are all `component()` nodes), the grid looks
  only at the component whose id matches its own. It reads its filter
  state from that component (`findComponentNode()`) and writes back only
  that component, leaving the others exactly as they are. That is what
  makes

  ```ts
  and(
      component("foo-grid", ...),
      component("foo-search", ...)
  )
  ```

  work: a complex search form above the grid controls `foo-search`, the
  grid's filter row controls `foo-grid`, and neither overwrites the
  other.
- **A lone component counts as composed.** `and()` unwraps a single
  operand, so a composition of one arrives as a bare component node,
  which `isComposedComponentExpression()` rejects. The grid treats it as
  a composition of one component.
- **Any other condition: the grid owns all of it.** A plain condition,
  or none at all, is taken over whole. The filter row reads it and
  replaces it.
- **Changes from outside show in the grid.** Code outside the grid may
  also change the grid's own part: its component in a composed
  condition, or the whole condition otherwise. The filter inputs then
  show the new state instead of keeping stale values. Where a term
  matches no filter input the grid offers, the grid still has to show
  that a filter is active, and "reset filters" has to clear it.
- **Updating one component** needs a helper that replaces a component's
  condition inside a composed condition. It adds the component if it is
  missing, and returns the same condition object when nothing changed,
  so an unchanged filter doesn't send an `update()`. Automaton's
  `updateComponentCondition()` is prior art; QLive has no equivalent yet
  (see "Missing in QLive").
- **Same rule for the sort.** `sortFields` has no components; the
  document has one sort order, and whoever sets it last wins. Code
  outside the grid can set any sort (see "Sorting"), and the headers
  have to show it as well as they can.

## Columns

- **Field path columns.** A column names a dotted field path, including
  paths through to-one relations (`owner.login`,
  `parent.parent.name`). The scalar type comes from the schema, and the
  value is displayed as that scalar type (Temporal types, decimals,
  enums …), by default through the registered converters. Empty values
  show a consistent "none" placeholder.
- **Checked against the schema.** A path that doesn't end in a scalar is
  an error that names the column.
- **Heading.** Defaults to the field name and goes through `i18n()`.
- **Render function.** A column can render a row with a function, for
  action buttons, computed values, links into another view and so on. If
  the function returns a plain value rather than an element, it gets the
  default value formatting.
- **Columns without a field.** Action or computed columns that can still
  have a filter and a sort expression.
- **Width.** Per column: width, minimum width, maximum width.
- **No wrapping.** A column can be set to cut long text off with an
  ellipsis and show the full value on hover.
- **Per-cell classes.** A column can add CSS classes to its cells, fixed
  or computed from the row.

## Sorting

- **Click a header to sort.** One click sorts ascending, a second click
  descending; from a multi-field sort, a click starts over with that
  column ascending. The offset goes back to 0.
- **Sort indicator** in the header, with accessible labels ("Sort by X
  ascending/descending").
- **Any sort expression.** A column's sort key is the field by default
  or any FilterDSL field expression (`field("numa").plus(field("numb"))`),
  so computed columns can sort. Descending is `"!name"` or `.desc()`.
- **Sort set from outside.** Code outside the grid, e.g. a select
  offering complex sort orders, can set any `sortFields`. The headers
  show that order as closely as they can: each column matching a sort
  field gets that field's direction and its position in the order
  (numbered, or one, two, three arrows up or down). A column matches
  when the sort field is its field or its sort expression, ascending or
  descending. A sort field that matches no column is still shown as
  "sorted by something else", not left out.
- **Header clicks only sort by one field.** A click always replaces the
  whole sort with that column, ascending or descending. There is no
  shift-click and no adding or moving fields within the order; that
  makes the header too finicky to use. A multi-field or expression sort
  is shown in the headers but only ever set by a dedicated control the
  view provides.
- **Per-column opt-out** of sorting.
- **Primary-key sort.** With no sort named, the server sorts by primary
  key, in the SQL only; the returned config names no sort, so no header
  shows as sorted.

## Filtering

- **Filter row** under the header, one cell per filterable column.
- **Filter by operator name.** A column's filter can be any FilterDSL
  operator (`containsIgnoreCase`, `eq`, `between`, …), and the number of
  inputs follows the operator's operand count, so `between` gets two
  inputs.
- **Filter function.** `(fieldName, ...values) => condition` for
  anything an operator name can't say, e.g. a range on a computed sum.
- **Default input by scalar type**: a three-way select for Boolean (any,
  true, false), a date range for date and time types, a typed input for
  everything else.
- **Custom filter input** per column, e.g. a select filled from another
  query document the view injected.
- **Reusable filters.** Automaton registered named filters globally
  (date range, number contains, like-pattern syntax with `*` `&` `/`
  `!`, a foreign-key picker in a modal, a flag/icon set). QLive should
  offer the same things as values the application imports and passes to
  the column, not as strings looked up in a registry.
- **Debounced.** Typing updates the document after a short delay, and a
  filter with several inputs only takes effect once all are filled.
- **Empty filters constrain nothing.** Falls out of `and()` dropping
  null operands; the grid relies on it rather than working around it.
- **Reset filters** clears the grid's own part of the condition (see
  "Component awareness") and leaves outside components alone.
- **Filters beyond the query.** A filter path through a to-many relation
  becomes an `EXISTS` on the server. That needs `selectByFilter(true)`
  on the endpoint, and the grid should fail with an error that says so,
  not a generic one.

## Pagination

- **Pager**: first/previous/next/last plus nearby page numbers, driven
  by `rowCount`, `offset` and `pageSize`.
- **Page size choice** with configurable options, including "all rows".
  The options respect the type's `maxPageSize` from `config().meta`: an
  option above it is either left out or visibly capped. A capped page
  size comes back capped in the document's config, and the pager has to
  display what actually applied.
- **Alignment** of the pager (start/center/end).

## Rows

- **Per-row classes and a highlighted row.** A function from row to
  classes, and a way to mark e.g. the row currently open in a detail
  pane.
- **Selection.** Per-row checkboxes bound to a set of ids the view owns.
  Also to consider: a select-all for the page, and whether a selection
  survives paging.
- **Stable identity.** Rows keyed by id, and the row element carries the
  id for tests and styling.
- **Empty state.** A "no rows" row across the full width, through
  `i18n()`.

## Working set

- **Edits show in the grid.** With a `WorkingSet` passed in, rows show
  their drafts (`ws.edit(row)`) rather than the rows as read, so unsaved
  edits appear in the list.
- **Row status.** Classes and an optional status cell for new, changed
  and deleted rows, and for the merge states: a row with conflicts, and
  a row deleted by someone else (`merge.gone`).
- **Field status per cell.** A cell carries the field's merge status
  (`changed`, `conflict`, `resolved`, `remoteChanged`) through
  `useMerge(row).field(name).className`, so one row accessor serves
  every cell.
- **Created rows.** Rows from `ws.create()` appear on the first page,
  filtered by the document's current condition. That needs client-side
  condition evaluation (see "Missing in QLive").
- **Created rows in view types.** A new row of a base type shown in a
  grid over a view or joined type. Automaton mapped these through
  per-field dependency resolvers; a use case has to be found before
  QLive builds anything for it.
- **Live updates.** With `watch` on the working set, other users' writes
  show up in the grid as they happen. Without a working set,
  `useDocumentWatch()` on the document does the same.

## Validation

- **Invalid cells.** A cell is marked invalid and shows the message in
  place of its value. The error can be attached to another field than
  the one displayed (an FK id while the cell shows the joined name). The
  render function gets the field and row errors. Nothing in QLive
  produces validation errors yet, so this waits on that.

## Styling

Follows `docs/styling.md`; nothing grid-specific should get around it.

- **Shipped CSS.** All grid rules live in `qlive.css`, inside
  `@layer qlive`, so any unlayered application rule wins without
  `!important`.
- **Public class names.** They are `qlive-`-prefixed (`qlive-grid`,
  `qlive-grid-row`, `qlive-grid-cell`, `qlive-grid-filter`,
  `qlive-grid-pager` …) and are API from the first release. The
  stateful classes need names too: sorted, selected, highlighted, the
  working set statuses, invalid.
- **Tokens.** Colors, spacing and borders come from `--qlive-*` tokens,
  and any grid-specific tokens this adds are named up front. Dark mode
  comes free from the token redefinition.
- **Framework-neutral.** No Bootstrap classes baked in as defaults.
  Striped, hover and bordered rows and a compact density are the grid's
  own classes or tokens, so they can be switched on or off.
- **Wide tables** scroll horizontally in a container instead of pushing
  the page wider.

## Beyond the grid

- **Export** what the grid currently shows (Automaton exported to Excel
  through the query document). This is a document or server feature the
  grid would only trigger.

## Missing in QLive

- **Updating a component.** A FilterDSL helper that replaces one
  component's condition inside a composed condition, adds the component
  when missing, and returns the unchanged condition object when the new
  term is structurally equal. `isComposedComponentExpression()` and
  `findComponentNode()` exist; the write side doesn't. It belongs next
  to them in `FilterDSL.ts`, since forms and pickers need it as much as
  the grid does. Prior art: Automaton's `updateComponentCondition()`.
- **Sort matching.** A function comparing a sort field (name, `"!name"`,
  or an expression, optionally wrapped in `desc`) to a column's sort key
  and answering ascending, descending or no match. Prior art:
  Automaton's `findSort()` and `compareConditions()`.
- **Client-side condition evaluation.** A FilterDSL evaluator over
  JavaScript objects, to filter and sort local rows and created
  working-set rows by the same condition the server applies. It has to
  agree with the SQL path and with `PayloadOperators` on what equality
  and ordering mean per scalar (see the planned `scalarEqual(type, a,
  b)`). Prior art: Automaton's `filterTransformer`,
  `evaluateMemoryQuery`.
- **Validation errors** to show in cells.
- **Export.**

## Lessons from Automaton

- **Explicit columns.** Automaton found its columns by walking
  `children` and checking the element type, so a column could not be
  wrapped in a component. Take a column definition that doesn't depend
  on element identity.
- **One source of state.** Automaton kept grid state in three
  overlapping places and synced them with effects. Here the query
  document's config is the only state for paging, sorting and filtering.
- **No required state library or form library.** Automaton's filter row
  was a hidden domainql-form `<Form>` over a MobX object.
