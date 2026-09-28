# DataGrid (design)

Status: built through step 6 of the build order. Written 2026-09-27.

The table component for QLive, and the pieces under it. What it has to
cover is `datagrid-features.md`, a survey of the Automaton DataGrid in
QLive terms. This document is how: which layer each feature lands in,
and the rules that keep the grid from absorbing every use case.

## Problem

Automaton's DataGrid grew into one component that knew about
everything: query paging, filter forms, working sets, column
configuration, drag and drop. Every new need became a prop, because
the grid was the only place its state was reachable. The result was
hard to change and impossible to replace in part: an application that
wanted a different pager or a card layout had no way to reuse the
grid's filtering or sorting.

QLive's grid should serve most table screens with its default and let
an application build the rest from the same parts, without the default
growing a prop for each of them.

## The central fact: the document is the state

A query document already holds everything a grid displays and changes:
`rows`, `rowCount`, and a `config` with offset, page size, condition
and sort fields. `update()` takes a delta, re-runs the query, and every
subscribed component re-renders from a new snapshot.

So the grid needs no state container of its own. Paging, sorting and
filtering are each a read of the snapshot and an `update()` call. Parts
that share a document share its state without knowing about each other:
a pager under a card list, a sort header in a hand-written table and a
search form above the grid all stay consistent because they all read
and write the same config.

This is what makes the layering below cheap. Nothing has to be synced
with effects, and nothing needs a grid context.

## Layers

Three layers, with one rule: **each layer uses only the public API of
the layers under it.** The default grid is built from exactly the parts
an application gets. Whatever it can do, a replacement can do too.

### Layer 1: pure functions

No React, no document. Usable from forms, pickers and services as much
as from the grid. They live next to the FilterDSL.

- **`updateComponent(condition, id, term)`**: the write side of
  component awareness (see below). It replaces the term of the
  component with that id, adds the component if it is missing, keeps an
  empty component as `component(id, null)` rather than removing it, and
  returns the very same condition object when the new term is
  structurally equal to the old one, so an unchanged filter sends no
  `update()`. A missing component is added as another operand of an
  `and` composition, or joined with `and()` to a lone component, so the
  result stays one flat level. An `or` composition without the
  component is refused, and any other condition is replaced whole (see
  "Component awareness"). Prior art:
  Automaton's `updateComponentCondition()`, which wrapped instead of
  joining and so produced a second level.
- **`ownedPart(condition, id)`**, the read side: which part of a
  condition belongs to the holder of an id, by the rules under
  "Component awareness".
- **`simplifySortField(sortField)`**: the simple string form of a sort
  field where it has one. A field, bare, as a node or in `asc` / `desc`,
  becomes `"name"` or `"!name"`; an expression stays a node.
- **`matchSort(sortFields, key)`**: whether a column's sort key appears
  in a sort order, and if so its direction and position. A key is what
  the column sorts by, a field path or an expression node, without a
  direction. It matches a sort field sorting by the same field or a
  structurally equal expression, in either direction. Prior art:
  Automaton's `findSort()`.
- **`toggleSort(sortFields, key)`**: the sort order a header click asks
  for (see "Sorting"): the key descending if it alone is the sort and
  ascending, the key ascending otherwise. A path comes out as `"name"`
  or `"!name"`, an expression as itself or in `desc`.
- **Page math**: `pageCount()`, `pageIndex()`, `pageOffset()` and
  `pageSizeOptions()`, the last clamped to the type's `maxPageSize`.
  Pages count from 0, like offsets.
- **`conditionsEqual(a, b)`**: structural comparison of conditions,
  which `updateComponent()`, `matchSort()` and the filter read-back all
  need. Values compare by their JSON form, so a condition equals the
  plain copy the server echoes.

### Layer 2: headless hooks

Each takes a document snapshot and talks to the document only through
`update()`. None returns markup.

- **`usePagination(doc, options)`**: `{page, pageCount, pageSize,
  pageSizes, goTo(n), setPageSize(n)}`. The page size shown is the one
  in the returned config, which may be capped.
- **`useSort(doc, key)`**: `{direction, position, toggle()}`. `toggle()`
  is the only thing a header click does (see "Sorting").
- **`useFilters(doc, id, columns)`**: the filter state of one owner of a
  condition part: per column the input values and a setter, plus the
  terms nobody claimed and a reset. Debounced, component-aware, and in
  sync with changes made from outside. It is one hook for all of an
  owner's filters rather than one per column, because reading back
  (see "Reading a condition back") has to see all columns at once to
  assign terms.
- **`useSelection(ids?)`**: a set of row ids the view owns.
- **`useGridRows(doc, {workingSet, watch})`**: the rows to display,
  and per row its status and per field its class. Without a working
  set, the document's rows. With one, the drafts, plus rows created in
  the working set on the first page (see "Working set").
- The merge hooks that already exist, `useWorkingSet()` and
  `useMerge()`, cover the working set's own state and a form's fields.
  The grid reads row and field status through `ws.accessor(row)`, one
  subscription for all rows rather than a hook per row.

### Layer 3: default components

- **`<Pager doc/>`**, **`<SortHeader doc sortKey/>`**,
  **`<FilterInput/>`**, **`<RowSelector/>`**: each small, each usable
  without the grid.
- **`<DataGrid doc columns/>`**: a thin composition of those parts plus
  the table markup. The aim is a component short enough (on the order of
  150 lines) that copying it into an application and changing it is a
  reasonable answer, and the documentation says so.

## Columns

Columns are data, an array passed to the grid, not children the grid
walks and inspects. A column can be built by a function, wrapped, or
shared between grids like any other value.

### A field path is a column

A column is `string | DataGridColumn`, and a string means exactly
`{field: string}`. Every default is derived from the field path and the
schema, and it applies to the object form just the same; the object
form only states what differs. So

```tsx
<DataGrid doc={bars} columns={["name", "num", "owner", "created"]}/>
```

is a complete table screen with display, sorting and filtering for
every column.

The derivation is a layer 1 function, `resolveColumn(type, column)`,
returning the complete column. Hooks and hand-written tables get the
same defaults as `DataGrid`.

The string carries nothing but the path: no `"name:containsIgnoreCase"`,
no `"!created"`. Anything more goes into the object form, where it is
type-checked.

### Paths are checked against the query

The row type of a query document holds exactly the fields its query
selects. A `FieldPath<T>` type derived from it, recursively over to-one
relations with a depth limit, makes every field path a compile-time
check: a typo is an error, and so is a field the query doesn't select,
which couldn't be displayed anyway. To-many relations drop out, since
they can't be a column.

### What is derived

For a **scalar path**:

- **Heading**: `i18n()` of the path, with a key convention that lets a
  type's fields be labeled once.
- **Display**: the scalar type from the schema, through the registered
  converters.
- **Sort**: by the field.
- **Filter**, by scalar type:

  | Scalar | Default filter |
  |---|---|
  | String | `containsIgnoreCase` |
  | Boolean | `eq`, three-way select |
  | Numbers | `eq` |
  | Date and time | date range |

For a **to-one relation path** (`"owner"`, not `"ownerId"`), the
column stands for the related row, named by the target type's
`nameFields` meta:

- **Display**: the name fields of the related row, most significant
  first. They come from the grid's own query through the to-one join;
  the query has to select them (`owner { id name }`), and a missing one
  is a runtime error naming the field.
- **Sort**: by the first name field, not by all name fields. A sort over
  all of them would match the display only for a renderer that
  concatenates them in order; one that rearranges or abbreviates them
  would make it misleading, while "sorted by the most significant name"
  stays true whatever the renderer does.

  The key is the first name field's path alone (`"owner.name"`). Rows
  pointing at two different owners that share a name still come out
  grouped by owner, because the server completes a path into a relation
  toward the related row's identity (see "Sorting"). The client has
  nothing to spell out for that.
- **Filter**: `containsIgnoreCase` on the first name field. The server
  joins it, so no extra query is needed.

A path ending in anything else, a non-scalar that isn't a to-one
relation with name fields, is an error naming the column.

### Catalog columns

Most relations in line-of-business tables point at catalogs: small
tables with a mostly fixed set of rows, where a row just changes which
one its foreign key points at. Filtering those by choosing a value
needs the catalog's rows, and the grid can't fetch them: only views
inject, and runtime queries are declared constants too. The view
supplies them:

```tsx
const owners = useInjection(Q_OwnerCatalog, {config: {pageSize: 1000}});

<DataGrid doc={bars} columns={[
    "name",
    {field: "owner", filter: pick(owners)},
]}/>
```

`pick(doc)` is a shipped filter. `toCondition` writes an `eq` on the
foreign key field, which the column finds through `relations` in the
domain meta and hands to filters that pick the related row
(`ColumnFilter.key`).
`fromCondition` reads the id back and looks its label up in the catalog,
so a filter set from outside shows its name without a lookup. The same
catalog document serves the foreign key select in the view's edit form.

A target table too large to load whole gets a picker with its own
search and document, run through `execute()` of a declared query. A
label for an id set from outside then costs one lookup by id through the
same query. That lives in the filter, not in the grid.

### The object form

A `DataGridColumn` has:

- **`field`**: the path, as above. Without it the column is an action or
  computed column and needs `render`.
- **`heading`**: overrides the derived heading.
- **`render(row)`**: optional. A plain return value gets the default
  formatting; an element is used as is.
- **`sort`**: overrides the derived sort key with another field path or
  an expression node, for a computed column; `false` turns sorting off.
- **`filter`**: overrides the derived filter (see "Filters"); `false`
  turns filtering off.
- **Cell presentation**: width limits, no-wrap, cell classes (fixed or
  from the row).

## Component awareness

A view may share one document's condition between several owners: the
grid's filter row, a search form above it, a picker. The condition's
shape decides what the grid owns.

- **Composed condition: the grid owns its component.** If
  `isComposedComponentExpression(condition)` is true, a logical
  condition whose operands are all `component()` nodes, the grid owns
  only the component with its id. It reads from that component and
  writes only that component. The others stay as they are.
- **A lone component counts as composed.** `and()` unwraps a single
  operand, so a composition of one arrives as a bare component node,
  which `isComposedComponentExpression()` rejects. The grid treats a
  bare component node as a composition of one. If the component is the
  grid's, the grid owns its term. If it belongs to someone else, the
  grid leaves it alone and joins its own component to it with `and()`.
- **Adding means `and`.** The grid's filters narrow what is shown; joined
  with `or()`, typing in a filter would widen the result instead. Where
  the grid's component is missing, it is added to an `and` composition
  as another operand. A lone component can't say whether `and()` or
  `or()` unwrapped it, since both unwrap a single operand, so `and` is
  the rule rather than a guess.
- **An `or` composition must contain the grid's slot.** If the
  composition is an `or` without the grid's component, the grid writes
  nothing and reports a dev error asking for `component(id, null)` in
  the composition. Joining the `or` would widen the result, and wrapping
  it in an `and` would produce a second level, which
  `isComposedComponentExpression()` rejects, so the grid would take over
  the whole condition on the next read.
- **No condition starts a composition.** The first owner to write
  makes its component the lone one, and the next joins it. A grid and a
  search form on a fresh document share it without the view seeding
  their slots. An owner writing nothing leaves no condition.
- **Any other condition: the grid owns all of it**, and writes a plain
  term without a marker.
- **Components exist on one level only.** Inside its component, the grid
  doesn't nest further markers to find its columns; it recognizes them
  by content (see "Reading a condition back").
- **Empty components stay.** An owner that currently filters nothing
  writes `component(id, null)`. `and()` keeps it, since it drops only
  non-objects; the config round trip keeps it; only the SQL
  transformation drops it (`ConditionTransformer.logic()`). The slot
  survives, so every owner finds its place again, and the database pays
  nothing for it.

The sort order has no components. The document has one sort, whoever
sets it last wins, and the grid displays whatever is set.

## Filters

A filter is a value the application imports and passes to a column.
There is no registry of names.

```ts
interface ColumnFilter<V extends unknown[]>
{
    /** Number of inputs. */
    arity: number

    /** Builds the term from the input values; null for "no filter". */
    toCondition(field: string, values: V): FilterExpression | null

    /**
     * Recognizes a term as one this filter produced and returns the
     * input values it came from, or null for "not mine".
     */
    fromCondition?(field: string, term: FilterExpression): V | null

    /** Input component, when the default for the scalar type won't do. */
    Input?: ComponentType<ColumnFilterInputProps<V>>
}
```

- **Operator filters** (`containsIgnoreCase`, `eq`, `between`, …) are
  made from the operator name. Arity is the operator's operand count,
  and `fromCondition` is derived: the term is that operator applied to
  the column's field, and the values are the remaining operands in
  order.
- **Filter functions** supply `toCondition` and, where they can,
  `fromCondition`. Recognizing a term and inverting it are the same
  function, so a filter author writes one thing, not two.
- **Default inputs by scalar type**: a three-way select for Boolean, a
  date range for date and time types, a typed input otherwise.
- **Shipped filters** (date range, number contains, like-pattern,
  `pick(doc)` for catalog columns, a picker for large target tables, a
  flag set) are written against this interface like any
  application filter and implement `fromCondition` themselves.
- A filter with several inputs takes effect only once all are filled,
  unless it is `partial`, like a date range open at one end.

### Reading a condition back

The grid's part of the condition has to be turned back into input
values: on first render, when the injected document already carries a
condition; after someone outside changes the grid's part; after reset.

The grid writes its part as one operand per active column, combined
with `and()`. Because `and()` unwraps a single operand, a column term
that is itself an `and` looks the same as several column terms when it
is the only one active. So the part is read in this order:

1. Offer the **whole part** to every column's `fromCondition`. If
   exactly one column claims it, that column is the only one active.
2. Otherwise, if the part is an `and`, offer **each operand**. Each
   operand must be claimed by exactly one column.

With several columns active, a column term that is an `and` stays
nested, because `and()` doesn't flatten, and the grid must not flatten
either when it builds its part.

What doesn't resolve:

- **Unclaimed terms**, written by someone who doesn't know the grid's
  filters, show once as "additional filter active", not attached to a
  column. Reset clears them, since they are in the grid's part.
- **A term two columns claim** is a configuration error, for example two
  columns filtering one field with one operator. The grid treats it as
  unclaimed and warns in dev rather than choosing.
- **A filter without `fromCondition`** can't take over a term set from
  outside; its term ends up unclaimed. In the normal case that doesn't
  matter, because of the next rule.

### Typing is not overwritten

After the debounce, the input's values go out as a term, and the applied
config comes back. If the grid then read the term back, a filter that
normalizes its input (a pattern, a lowercased string, a date widened to
a day) would change the input while the user types.

So the grid remembers, per column, the term it last sent. If the term
that comes back is structurally equal, the input keeps its raw values
and `fromCondition` isn't called. Reading back happens only when the
grid's part changed to something the grid didn't write. That keeps
filters without an inverse fully usable as long as the grid is the only
one writing their terms.

Carrying the raw input inside the condition is ruled out: the component
node travels as `id` and `condition` only (`ConditionCoercing`), and a
second copy of the input next to the term drifts from it as soon as
outside code changes the term without knowing the payload format.

An FK picker's label (the condition holds only the id) is the filter's
business: its input loads the label or takes it from the view.

## Sorting

- **A header click sorts by one field**: that column ascending, or
  descending if it already is the only sort field and ascending. The
  click replaces the whole sort order and resets the offset. There is no
  shift-click and no way to add or move fields from the header; that
  gets too finicky to use.
- **Complex orders come from the view.** A multi-field or expression
  sort is set by a control the view provides, e.g. a select of named
  orders calling `update({sortFields})`.
- **The headers show any order as well as they can.** Each column whose
  sort key matches a sort field (`matchSort()`) shows its direction and
  its position in the order. Sort fields matching no column show as
  "also sorted by something else", not left out.
- **The primary-key sort** the server applies when none is named stays
  in the SQL, like the completion below. The returned config names no
  sort, so no header shows one.
- **The server makes every order total.** Offset paging over a sort
  with ties can show a row on two pages or on none. So the server
  completes every named sort: where the sort fields include part of a
  non-nullable unique constraint of the root type, it appends that
  constraint's remaining fields in constraint order; otherwise it
  appends the primary key. A sort already covering such a constraint is
  left alone. A path into a relation doesn't count toward a root key,
  since it orders by the related row.

  A path into a to-one relation is completed first, toward the related
  row's identity, by the same rule applied to the target type: the rest
  of a non-nullable unique constraint of the target the path is part of,
  otherwise the target's primary key. `["owner.name"]` runs as
  `owner.name, owner.id, id`. Without that, rows of two owners sharing a
  name would interleave, and the user couldn't tell which row belongs
  to which owner.

  The completion goes into the SQL only: the
  config that comes back is the sort that was named, so the headers have
  nothing to tell apart from what the user or the view chose. It follows
  from the named sort the same way every time, so echoing the config
  gets the same order again. (`QueryPlanBuilder.completion()`.)

## Pagination

- The pager reads `rowCount`, `offset` and `pageSize` from the snapshot,
  through `usePagination()`.
- Page size options are configurable and include "all rows". They are
  clamped to the type's `maxPageSize` from `config().meta`, and the page
  size displayed is the one the server applied, which may be capped.

## Rows

- Rows are keyed by id, and the row element carries the id.
- Per-row classes come from a function of the row, and a highlighted row
  (e.g. the one open in a detail pane) is a grid prop.
- Selection is a set of ids the view owns, through `useSelection()` and
  `<RowSelector/>`. Select-all for the page and whether a selection
  survives paging are open.
- The empty state is a "no rows" row across the full width.

## Working set

All of it is optional. A grid without a working set carries none of it.

- **Drafts.** With a working set, `useGridRows()` returns `ws.edit(row)`
  for every row, so unsaved edits show in the list.
- **Row and field status.** Row classes for new, changed, deleted,
  conflicted, gone and remote-changed rows; cell classes from the
  accessor's `field(name).className`. A column marks the field it
  shows, a relation column its foreign key, and a column of a related
  row's field nothing. The fields of a new row aren't marked: the row
  says it.
- **Created rows** appear on the first page when the document's current
  condition matches them. That needs client-side condition evaluation,
  which doesn't exist yet (see "What has to exist first"). Until then,
  created rows show unfiltered on the first page.
- **Live updates** come from `watch` on the working set, or from
  `useDocumentWatch()` on the document when there is no working set.
  Watching the document, the grid marks the changed rows and cells and
  offers a reload; the values arrive only with it.
- **Registration.** `useGridRows()` registers the document with the
  working set, and again whenever its rows change, so a watch follows
  the pages turned. The rows of pages already seen stay in the working
  set and in the watch.
- **Relation cells of drafts** show the related row as read. A draft
  whose foreign key changed, or a created row, has no related row to
  show until the merge refetches, so its relation cell shows the old
  one or nothing.

## Styling

Follows `docs/styling.md`.

- The grid's rules live in `qlive.css` inside `@layer qlive`.
- Class names are `qlive-grid-*` and public API from the first release,
  including the state classes: sorted, selected, highlighted, the
  working set statuses, invalid.
- Colors, spacing and borders come from `--qlive-*` tokens. Dark mode
  follows from the tokens.
- No framework classes are defaults. Striped, hover, bordered and a
  compact density are the grid's own classes.
- Wide tables scroll horizontally in their container.

## Keeping the grid small

The rule for adding to `DataGrid` itself:

1. **Can it be done with the hooks, or with a column's `render` or
   `filter`?** Then it goes into a how-to, not into the grid.
2. **If not, the gap is in layer 1 or 2** and gets fixed there, where
   every alternative to the default benefits from it too.
3. **A prop on the grid** is for something that changes how every row
   or cell behaves and that most table screens need: row classes,
   highlighting, selection, working set status.

Composition is the escape hatch, not configuration. The user site
documents "build your own table from the hooks" as a normal path, not
as a last resort.

TanStack Table was considered as the headless core and not adopted. It
brings its own state model, which would have to be kept in sync with
the query document, the overlapping-state problem this design exists to
avoid. Its API shape is worth borrowing.

## What has to exist first

- Built already: the layer 1 functions above, in `FilterDSL.ts`,
  `grid/paging.ts` and `grid/columns.ts`; the server's completion of
  named sorts described under "Sorting", relation paths included; and
  the `uniqueKeys` type meta (`UniqueKeyProvider`).
- **Client-side condition evaluation**: a FilterDSL evaluator over
  JavaScript objects that agrees with the SQL path and with
  `PayloadOperators` on equality and ordering per scalar (the planned
  `scalarEqual(type, a, b)`). Needed for created working-set rows on the
  first page and for local rows (a hand-built `QueryDocument`, which
  can't `update()`). Prior art: Automaton's `filterTransformer`,
  `evaluateMemoryQuery`.
- **Validation errors** to show in cells. Nothing produces them yet.

## Build order

1. Layer 1 functions, with tests. Done, `resolveColumn()` with the
   column types in step 4.
2. `usePagination()` and `useSort()`, then `<Pager/>` and
   `<SortHeader/>`. Useful on their own the moment they exist. Done,
   in `qlive-ts/src/grid`, with `pageWindow()` added to the page math
   for the numbers a pager lists.
3. `useFilters()` with operator filters, the read-back and the
   typing protection, then `<FilterInput/>`. Done, in
   `qlive-ts/src/grid`: `operatorFilter(name, scalarType)` takes text
   inputs and converts them to the scalar type, `claimTerms()` is the
   read-back, and the typing protection keeps every term a column sent
   until it comes back, so a slow response overtaken by further typing
   doesn't rewrite the input either.
4. `<DataGrid/>` composing them, and a qlive-test view that uses it
   next to a search form owning a second component. Done: Home in
   qlive-test. `resolveColumn()` and `FieldPath<T>` are in
   `grid/columns.ts`, with `rowKey()` keying rows by the type's primary
   key from `uniqueKeys` rather than assuming an `id`. Cells display
   through `formatValue()`, the new optional `format()` of a converter.
   Date and time columns have no default filter until the date range
   filter of step 6. Selection (`useSelection()`, `<RowSelector/>`) is
   not built yet.
5. Working set integration through `useGridRows()`, without created-row
   filtering. Done: `DataGrid` takes `workingSet` and `watch`, and Home
   in qlive-test edits, duplicates and deletes rows through a working
   set. The merge layer gained `WorkingSet.created(type)`, `isNew` and
   `deleted` on the accessor, drafts of new rows answering `in` for
   every field of their type, and `register()` telling subscribers
   about rows it hadn't announced.
6. The shipped filters. Done, except the picker for large target
   tables, which needs its own search document and a dialog, and
   waits until a view needs one: `dateRangeFilter()` (the default for
   Date and Timestamp, a Timestamp filtered by whole days in the user's
   time zone), `numberContainsFilter()`, `patternFilter()` (`*`, `&`,
   `|`, `!`, built as `containsIgnoreCase` and `likeRegex` terms rather
   than one regular expression, so reading back is a walk over the
   term), `pick(doc)` and `flagSetFilter()`. Home in qlive-test uses
   each. Three additions came with them. `ColumnFilter.partial` lets
   a filter take effect with some inputs empty, a range open at one
   end. `ColumnFilter.key` marks a filter that picks the related row,
   and `resolveColumn()` hands it the foreign key instead of the first
   name field; `pick()` so needs no domain meta and no row type, and a
   search form gives it the foreign key directly. Any other filter on a
   relation column gets the first name field, like the default. And
   the server learned the `toString` operation the DSL always had, as a
   cast to text in SQL and `String.valueOf()` for payloads.
7. Client-side evaluation, then created rows filtered on the first page
   and local rows.

## Open

- Select-all and selection across pages.
- Whether `useFilters()` should also serve a form that isn't a grid:
  a search form is an owner of a component too, with fields instead of
  columns.
- Export, which belongs to the document or the server; the grid only
  triggers it.
