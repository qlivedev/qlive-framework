# Layouts (design)

Status: concept settled, client building blocks built, details open.
Written 2026-10-18, revised 2026-10-20. Field modes, input conversion
and the client building blocks are built (`qlive-ts/src/form/`), and
so are the to-one picker and the related-rows endpoint; action input,
catalog types, field conditions, server layouts and rules are not.
The companion concept is `actions.md`.

The auto-form. A layout says which fields a form shows, how they are
grouped and which widgets render them. It is built from the shape the
data already has and works without any definition at all, and an
application can take over by writing it as TSX from the same building
blocks.

## Problem

`qlive-test/frontend/src/app/bar/Edit.tsx` shows the gap: `typed()`
converts input strings by field name, labels are raw field names,
`FIELDS` is a hand-written list, and nothing produces a validation
error, which the grid's invalid cells wait on as well.

The schema can't produce a form, not even with invariants, because what
a form shows is situational. What it can provide is a starting point:
the shape of the data a view already has. Following the read side, the
view should say little -- name what it renders and leave the rest to
metadata -- and still be able to take over completely where the
default isn't enough.

## What a layout is built from

**What's there**: the selection of a query, or the in-memory result of
it. The default layout for a view is derived from that shape, so an
edit screen like `bar/Edit` gets an acceptable form from its query
document alone. Without an action, only invariants apply.

**A layout may refer to an action, or not.** With one, the layout adds
the action's input fields: a derived layout takes them from the action;
an explicit layout doesn't have to show an input for each of them, since
what counts is that all input is filled when the action is issued
(`actions.md`, "Issuing an action"). The action's references don't decide what the
layout shows; they exist for its rules.

## Two forms, one vocabulary

- **Data**, interpreted at runtime by the auto-form component. A
  derived layout is produced in this format on the client.
- **TSX**, written in a view from the same building blocks.

Both are a tree of the same parts: a node in a layout description stands
for a component, and an application's own components are registered
and referred to by name, the way widgets are.

**The escape hatch is the TSX form.** It follows the DataGrid rule
(`design-done/datagrid.md`): the auto-form is built only from the public
parts an application gets, so a hand-written form can do everything the
generated one does. The grid can do that because the query document
holds all its state. A form's state has three sources -- the query
document, the working set's drafts and the action's input -- and the
parts reach them through a form context. The context is public, so the
rule holds: a hand-written form sets it up the same way the auto-form
does.

**The auto-form takes a layout or children, never both.** It refers to a
layout by name or is handed one, and renders it. Given children
instead, it renders nothing of its own and only provides the context,
which saves a separate provider export. There is no middle step where
children replace or extend parts of a layout: whatever children could
do there, a layout does.

**A scaffold** turns a layout, the derived one included, into TSX, a
codegen command next to `generate-ts`. Ejecting from a form library
copies validation and wiring, which then fork; here rules stay in
invariants and actions, so ejecting copies presentation only.

## Widgets

Customized at two levels: **per application**, by type ("`Money` always
renders as X"), and **per action**, by field.

Carried over from an earlier sketch and not discussed again since:
widgets resolve by scalar type, field metadata and an explicit name.
Built in: String, Int and the decimal types with the reflected `@Size`
and precision, Boolean, the Temporal types, enums, a to-one picker
showing name fields, the many-to-many row set. An unresolved field does
not fall back to a text input; in dev it renders a visible placeholder
naming what is missing ("no widget for `Money` on `Invoice.total`").
An application's own scalars go through the same registry.

Between a live value and what an input edits, each scalar converts
both ways: `inputText(value, type)` and `parseInput(text, type)` in
`converter.ts`, next to `formatValue`, from a converter's `toInput` and
`fromInput` or a built-in for the scalars without a converter. This is
not `format`: a Timestamp reads one way and goes into a datetime-local
input another, and a Currency amount is held in 1/10000th units. Most
inputs edit text; a checkbox takes a Boolean as it is. Blank text is
null except for a String. `parseInput` replaces `typed()`, and an
unparseable value becomes a client-side error. BigDecimal has no
input until a decimal type is chosen for it.

## Field modes and editability

Every field renders in one of four modes, carried over from Automaton
and spelled as a string union like `MergeFieldStatus`
(`FieldMode` in `qlive-ts/src/form/fieldMode.ts`):

- **`normal`**: editable.
- **`disabled`**: a disabled input.
- **`readOnly`**: a read-only input, where the widget has one.
- **`text`**: the value as a plain text span with a class, styled to
  line up with the inputs next to it, the way Bootstrap's
  `form-control-plaintext` does.

**A derived layout edits every field except** primary key fields,
computed fields (the `computed` field meta `@GraphQLComputed` sets),
the `version` field and fields whose `editable` field meta is false;
those render as `text` (`defaultFieldMode()`). The `version` field is
recognized by its name, as the merge recognizes it. An application sets
`editable: false` on others, such as created and modified columns,
which stay editable by default until it does.

**A foreign key is edited**, nullable or not, through a default to-one
picker on the key field itself; a nullable key's picker can clear it.

**A field's mode is state.** The layout initializes it: the derived
default, or the mode a layout node or `Field` gives. Rules change it
while the form is open -- a field enabled once another is filled.
**An authorization rule locks it**, and no rule changes it after that.
How it locks, and in which mode, is left to the authorization work
(`authorization.md`).

## The building blocks

The shape follows the grid: headless hooks carrying all the logic,
default components that are nothing but those hooks plus markup, and an
auto-form composed from those components alone. `Form` is decided: a
form references a layout, and imported from the package's namespace it
can sit next to other `<Form/>` components. So is `Field`: it is the
name an application writes, while FilterDSL's field type, which an
application only gets from `field("name")`, became `FieldReference`
to make way, and the condition node with it, in Java and on the wire. The other names are working names.

The sketch below is what was built, apart from these, as of 2026-10-20:

- **No action input yet.** `Form` has no `action`, the context no
  `input`, `deriveLayout` no action argument, and a path starting with
  `input.` is an error, until actions exist.
- **Nothing locks a mode.** `setMode` changes any field; locking is the
  authorization work's.
- **`modes` holds what the layout gave and `setMode` set.** A field not
  in it has the mode its `Field` gives, or the default. A
  hand-written form's modes aren't known before its fields render.
- **Overrides apply to every field that names no widget**, a
  hand-written `<Field path="price"/>` included, not only to layouts:
  an application's `Money` widget would otherwise be missing exactly
  where the application wrote the form itself. The conflict decorator is
  still a layout's default only, and a hand-written form gives it where
  it wants it.
- **The decorator goes around the control**, the box carrying the merge
  mark, so conflict buttons sit beside the marked input, not inside it.
  It is called a decorator rather than a wrapper: `Field` is given it
  as a prop and applies it, while "wrapper" suggested a component the
  application puts around `<Field/>` in JSX, which can't reach the
  field's state or its place in the grid.
- **Built-in widgets**: a text input; a number input that is a text
  input with `inputMode` (`type="number"` reports unparseable text as
  empty, which would hide it, and knows no bigint); `date` and
  `datetime-local` pickers; a checkbox for a non-null Boolean and a
  yes/no select for a nullable one; a select for an enum, labeled
  `Type.VALUE`, until it becomes the filtering picker (see below). A
  checkbox and a select have no read-only state, so `readOnly` disables
  them. A to-one relation gets `RowPicker` (see
  Related rows). A many-to-many field gets `MissingWidget`, which shows
  the value and says there is no widget, until its row set exists; so
  does a scalar without input text, such as `BigDecimal`.
- **One widget for every closed set** (decided 2026-10-20): an enum, a
  catalog type and any other related type all get the picker with a
  text filter, `RowPicker`. A select loses to it as soon as there are
  enough values to scroll, and the point where a radio group is better
  can't be pinned to a count: it depends on how long the labels are, too.
  So there is no threshold and no rule by count; a radio group and a
  select may come later as widgets an override names, not as defaults.
  For a set the client knows -- an enum's values, a catalog's rows --
  the picker filters in the browser, without the endpoint and without
  a search delay; an enum's values are labeled `Type.VALUE` through
  `i18n()`. `EnumSelect` gives way to this; `Choice`, the select under
  it, stays only for the nullable Boolean.
- **A nullable Boolean keeps its select** of yes, no and nothing. Three
  fixed words are no closed set worth filtering, and a three-state
  checkbox can't show "nothing" in a way people read reliably. A
  non-null Boolean stays a checkbox.
- **Typed text survives while it means the current value.** A field
  keeps the text it was given as long as it parses to the value the
  draft holds, so `1.` in a Float isn't rewritten to `1` mid-typing; a
  value set elsewhere -- undo, a rule, another window -- replaces it.
- **Layout nodes**: a label is a `ReactNode`, and a component node takes
  a component type as well as a registered name, as a widget does.
- **A form with children adds no markup.** `qlive-form` is the grid the
  layout renders into; a hand-written form uses the class or not.

### `Form`, the context and its provider

```tsx
<Form row={ bar }/>                           // derived layout
<Form row={ bar } layout={ barLayout }/>      // given layout
<Form row={ bar } layout="barEdit"/>          // named layout
<Form row={ bar } action="approveBar"/>       // derived, plus the action's input
<Form row={ bar }>                            // provider only
    <Field path="name"/>
    <Field path="owner"/>
</Form>
```

- **`row`** is a draft, `ws.edit(row)` or `ws.create(type)`. One form,
  one entity: its type comes from the working set that made the draft
  (`useMerge(draft).type`), its merge state from the same accessor. A
  screen editing several rows has several forms, which is also where
  field groups go.
- **`layout`** or **`children`**, never both, and a form given both is
  an error, not a precedence rule.
- **`action`** names the action whose input the form also edits.

The context holds the three sources and what only a form has:

```ts
interface FormContext
{
    /** the draft, the first source; the second is the working set it came from */
    row: object
    domainType: string
    /** the name of the layout the form renders, null for none */
    layoutName: string | null
    merge: MergeAccessor
    /** the action's input, the third source; null without an action */
    input: ActionInput | null
    /** the fields whose text didn't parse, by path */
    invalid: ReadonlyMap<string, string>
    /** each field's current mode, initialized from the layout */
    modes: ReadonlyMap<string, FieldMode>
    /** changes a field's mode; refused for a field authorization locked */
    setMode(path: string, mode: FieldMode): void
}

function useFormContext(): FormContext   // throws outside a Form
```

`input` is state the form owns, since action input lives in no working
set: initialized from the action's definition, written through the same
fields. How the action is issued with it is `actions.md`'s.

**`invalid` lives in the form context.** Text a field holds but
couldn't turn into a value, `"12x"` in an Int field, never reaches the
draft, which holds live values, and the input still has to show it.
Kept in the field's own state, it would vanish when the field unmounts;
kept in the context, it lasts as long as the form, and a save from the
form checks it. Automaton kept it as the first of a field's stored error
messages, which was a hack but showed that it belongs with the form and
not with the values. A parse error is therefore not one of the
working set's field errors (see "Carried over"), and `merge()` doesn't
see it.

### `useField` and `Field`

```ts
function useField(path: string, options?: FieldOptions): FieldState

interface FieldOptions
{
    mode?: FieldMode
    widget?: Widget
    label?: ReactNode
}

interface FieldState
{
    path: string
    /** owning type and field name, after following the path */
    domainType: string
    name: string
    /** scalar or enum type, or the related type for a relation */
    fieldType: string
    kind: "scalar" | "toOne" | "manyToMany"
    label: ReactNode            // i18n("Type.field"), as a grid heading
    id: string                  // for the label's htmlFor
    mode: FieldMode
    required: boolean           // non-null in the schema
    meta: DomainFieldMeta       // maxLength, precision, ...

    /** the live value, through the draft or the input */
    value: unknown
    setValue(value: unknown): void

    /** what a text input shows: the text being typed, else inputText(value) */
    text: string
    setText(text: string): void

    /** parse error of the text, a message for the user */
    error: string | null

    /** merge status of the field, and the class it carries */
    merge: MergeField
}

function Field(props: { path: string } & FieldOptions): JSX.Element
```

`setText` runs `parseInput`: valid text sets the value, invalid text
stays in the field as typed and is reported to `invalid`. A widget that
edits a value rather than text -- a checkbox, a picker -- uses
`setValue` and never has an error.

A path is a field of the form's entity, or the action input's when it
starts with `input.`. A to-one relation is named by its object field,
`owner`, and edits the foreign key behind it, as a grid column's
`statusField` does. A path one relation hop further, `owner.login`, is
a field group's business and not a field's.

`Field` is `useField` plus markup: a label, the widget, the error,
and the merge status class. In
`text` mode it renders no widget at all but the formatted value -- the
related row's name fields for a relation, as a grid cell shows them --
in a span with `qlive-field-text`. Widgets then only ever see `normal`,
`disabled` and `readOnly`.

### Widgets

```ts
type Widget = ComponentType<{ field: FieldState }>

/** a component around a field's widget, see "Conflicts" */
type FieldDecorator = ComponentType<{ field: FieldState, children: ReactNode }>

interface WidgetOverride
{
    // where the field is: one of
    layout?: string         // the layout's name
    domainType?: string     // the type of the row the form edits
    // what the field is: one of
    path?: string           // field path from that type
    fieldType?: string      // scalar or enum type, or a relation's related type
    widget?: Widget
    decorator?: FieldDecorator
}

config().widgets: WidgetOverride[]
function registerWidget(name: string, widget: Widget): void
```

A widget gets the whole `FieldState` and renders the input only; label
and error are `Field`'s.

**On the client a widget is a component type.** A view writing TSX
passes it to `Field` directly, and a layout object built in TSX carries
it in its node. Names exist only for layouts the server delivers, which
can't hold a component: `registerWidget` gives a component the name such
a layout uses.

**Overrides are matchers in config**, applied to every field that
names no widget: layouts from the server and from `deriveLayout()`, and
a hand-written form's fields where it names none. A matcher keys on
where the field is and on what it is, and nothing else:

- **Where**: the `layout` by its name, or the `domainType` of the row.
  The two are nearly the same thing, but a layout is one indirection
  away from its type: two layouts of one type are told apart by name,
  and the derived layout and a hand-written form, which have no name,
  are reached by their type.
- **What**: the field's `path`, or its `fieldType`. A relation's field
  type is the related type, since a relation field is named by its
  relation object; that is how a picker for every foreign key to
  `AppUser` is configured. "Scalar" was the wrong name for it.

**One key of each kind at most.** An override giving both a layout and
a domain type, or both a path and a field type, is an error rather than
a ranking. A key left out matches anything. Matching an action seemed
too esoteric to be worth a key.

```ts
config().widgets = [
    { fieldType: "Money", widget: MoneyInput },
    { fieldType: "AppUser", widget: UserPicker },
    { domainType: "Bar", path: "num", widget: Slider }
]
```

**The most specific matcher wins**, and the later of two equally
specific ones. What the field is counts first, a path over a field type
over neither, then where it is, a layout over a domain type over
neither; so `{ domainType: "Foo", path: "price" }` beats
`{ layout: "fooEdit", fieldType: "Money" }`.

Resolution for a field, first hit wins: the component the layout node
or `Field` gives, the name a server layout gives, an override, the
built-in for the field type or relation kind -- `text`, `number`,
`checkbox`, `date`, `datetime`, `select` for enums, `toOne`,
`manyToMany`. No hit is the dev placeholder.

**Conflicts are a decorator.** The buttons that resolve a conflicting
field are merge UI, not a field's, so QLive ships them as a
`FieldDecorator` around the widget, matched like a widget override. The
auto-form applies it; a hand-written form uses it or not, which lets an
escape hatch that doesn't merge ignore merging entirely. Conflict UI
varies by application -- inline buttons, a popover, a side-by-side
view -- and a decorator is where that variety goes, without every widget
having to cooperate.

### The layout format

```ts
interface Layout
{
    name?: string
    domainType: string
    action?: string
    nodes: LayoutNode[]
}

type LayoutNode =
    { field: string, widget?: Widget | string, mode?: FieldMode, label?: string } |
    { section: string, nodes: LayoutNode[] } |
    { component: string, props?: Record<string, unknown>, nodes?: LayoutNode[] }

function deriveLayout(type: string, row: object, action?: string): Layout
function registerComponent(name: string, component: ComponentType<any>): void
```

`field` nodes are `Field`s with their options; `section` is a labeled
group (`<fieldset>`, legend `i18n(section)`); `component` is an
application's own, registered by name like a widget, with its nodes as
children.

**`deriveLayout`** takes the fields the row has -- which are the fields
the query selected, in its order -- and drops what a form doesn't show:
lists other than many-to-many fields, and a foreign key whose relation
object is selected too, since the relation's field edits it. With an
action, the action's input fields follow. The scaffold is
`deriveLayout` plus a printer from `Layout` to TSX.

**A layout name** is looked up in the bootstrap, where a
`LayoutProvider` would put them. With none implemented the lookup
always fails, so until then the form takes layout objects only, which
an application can keep in a module of its own.

### What `Edit.tsx` would become

```tsx
{ bars.rows.map(row => <Form key={ row.id } row={ ws.edit(row) }/>) }
```

The conflict buttons become the decorator the auto-form applies.

### Related rows

A to-one picker needs the rows it offers, and a field whose query
selected the key without the relation object needs the name of the row
the key points at. `pick()` has the same need today and gets a
document handed in. For an explicit field a view can still pass one; a
derived layout knows the target type but has no query for it -- none
may exist, and an application query that does may carry conditions of
its own.

**A field may add a condition**, which is how a picker offers only the
members of this project. The condition is rooted at the form's type, not
at the related one, and reaches the candidate through the relation
itself (decided 2026-10-20):

```ts
// on Foo.owner: the users of the Foo's project
{ field: "owner", condition: field("owner.projectId").eq(field("projectId")) }
```

A candidate qualifies when the condition holds for the form's row with
the candidate in the relation's place, `{ ...row, owner: candidate }`
as the root. The form's row is implicit in the field references, so the
condition needs no node of its own for it, stays plain FilterDSL, and
travels as data in a layout from the server.

Where the rows come from, and where that condition is evaluated,
depends on the target type, and the picker reads the same either way:

- **A catalog type** (`catalog: true`): from the bootstrap, filtered in
  the browser with the FilterDSL evaluator (`conditionPredicate()`),
  against that root. The server is never asked.
- **Any other type**: from the related-rows endpoint, filtered on the
  server through the query document service. The endpoint wants a
  condition rooted at the target type, so the client rewrites it before
  sending: `owner.projectId` becomes the candidate's `projectId`, and
  `projectId` becomes `value(row.projectId)`, a constant from the
  form's row.

What follows from the rooting:

- **The picker depends on the references outside the relation.** When
  one of those fields changes in the draft, the rows are asked for
  again.
- **Whether the current value still qualifies** is the same evaluation
  with the current related row in place: free for a catalog type, one
  request otherwise. Whether a value that no longer qualifies shows as
  invalid or is cleared is open.
- **References through another relation read its relation object**,
  which may be stale after its key changed, as for `fieldText()`. Not
  the fields the foreign key references: `fooType.name` is `row.type`
  itself and is folded into it. Any other field of a stale relation
  object should be an error rather than a silent wrong answer.

**`catalog: true`** is type meta for a type whose rows are constant for
the lifetime of an application run. Its rows ship in the cached part of
the bootstrap. The user documentation must stress what that costs:
every row of every catalog type is in every client's bootstrap, and a
constant table can still be too large to be a catalog. A catalog type
is visible to everyone -- or at least to everyone who can see a form
using it, once there is authorization: nothing filters what is already
in the bootstrap.

**A catalog type is an enum bound at application start** rather than at
compile time: a closed set known before any view renders, its values
named and able to carry data, as a Java enum's constants can. The
relational form of an enum is the lookup table, which is what a catalog
type usually is. Whether application code names particular values -- a
rule on `TYPE_A`, a special user -- is the application author's
decision; QLive neither checks such names at startup nor generates
constants for them. Naming a particular row isn't tied to catalogs
anyway: `AppUser` is none, and some of its rows can still be special.

In qlive-test, `FooType` is the candidate: four rows, nothing writes
them, and `foo.type` references `foo_type.name`, a unique key beside
the surrogate primary key `ordinal`. It would be the first catalog
there; `Q_OwnerCatalog` is named like one, but users are added at
runtime, so `AppUser` stays with the endpoint.

**The related-rows endpoint** serves the other types. Putting a
generated query per type into the schema is hard without an explicit
annotation per query, so it fakes GraphQL instead:

- **One REST endpoint** takes a type name and a `QueryConfig` (condition,
  sort, page) and runs it through the query document machinery, which
  already yields a map graph with every scalar coerced; the endpoint
  only serializes it. It selects every scalar field of the type and no
  relation, a selection it builds itself. A query string per type, to
  select something else, may come later.
- **Valid types** are those a foreign key references and that are not
  catalog types, so the endpoint serves pickers and nothing else. Any
  other type is an error, a catalog type included: its rows are in the
  bootstrap, and a request for them means a client took the wrong
  path.
- The type's `queryConfig` meta, the condition every query of the type
  carries, applies as it does everywhere, and so do
  `authorization.md`'s row-level rules.
- **The field's condition** comes from the client, rewritten to the
  target type as above. It can only narrow what the type's own condition
  and authorization allow, never widen it.

**Built so far** (2026-10-20): the endpoint, `POST /api/related-rows`
(`RelatedRowsController`, which an application declares as a bean like
its other controllers), and on the client `relatedRows()` and
`RowPicker`, the built-in widget of every to-one relation. Catalog types
and the field's condition are not built yet; every picker asks the
endpoint.

- **Departure: the endpoint serves the key and name fields only**, the
  fields foreign keys reference plus the type's `nameFields`, not every
  scalar. Being a foreign key's target says nothing about whether all of
  a row is for everyone to read; `AppUser` has a password hash. The
  config filters and sorts by those fields only. A picker wanting more,
  a second line under the name, is what the later per-type query string
  would be for.
- **The query document service takes a field list** besides a GraphQL
  environment (`buildQuery(type, fields, config)`), since the endpoint
  has no selection set to plan from.
- **`RowPicker` is a combobox**: typing searches the type's String name
  fields with `containsIgnoreCase`, after a pause; the list shows the
  first 20 rows sorted by the name fields and says how many more there
  are. Choosing sets the foreign key; emptying the input clears a
  nullable one; typed text that chose nothing is dropped on blur.
- **A key picked is named by the row the picker fetched.** The row's
  relation object names the key it was read with only, and a draft
  can't be given a new relation object, so `relatedRows()` remembers the
  rows it fetched and `fieldText()` names a changed key by them -- in the
  input, in `text` mode and on the conflict buttons.

## The format on the server

**The data format exists on the server too**, as Java classes rooted
in `Layout` in `io.github.qlivedev.model.layout`, with a
`LayoutProvider` interface that could deliver layouts from there --
from an editor, from files, from a module. No implementation is planned:
the developers building an application write TSX where the derived
layout isn't enough, and an editor serves a user group QLive doesn't
have. The interface keeps the possibility open.

An implementation would work like the action providers: every provider
bean is injected as a list, merged, and a duplicate name fails at
startup. Validation would run in two passes, since a layout may refer to
an action from a different provider: first all actions, then the
layouts against the domain and the actions. Layouts would ship in the
cached part of the bootstrap -- a large form is 3 to 5 KB of JSON, so
even a hundred layouts don't justify a per-route mechanism -- and views
refer to them by name, so delivery could change later without touching
them.

### Named layouts and where they come from (raw notes, 2026-10-20)

Thinking aloud, not settled. This shifts the paragraph above: the server
is now meant to create named layouts, from a builder or an editor,
neither of which exists yet.

- **A name is what makes a layout resolvable.** `<Form layout="barEdit"/>`
  looks it up; only a layout object handed to `Form` directly can do
  without one (`Layout.name` is optional for that reason).
- **Generation is a source of its own**, beyond the defaults
  `deriveLayout()` gives. Prior art: in Automaton's predecessor, a
  customer with a huge database of tiny parts -- screws and the like --
  had "view models" in OpenSAGA terms generated from stereotype or
  blueprint templates filled in from the tables and columns. A view
  model mirrors a layout here, except that a layout isn't bound to a
  view either: it belongs to a domain type, and any view renders it.
- **On the client it is all data arriving.** Layouts come in the
  bootstrap and can be read and changed in startup's `init` before
  anything renders. An application generating its own in TypeScript
  registers them there too: no reason seen to do it beyond taste, and
  none to forbid it.
- **A generated layout may share its domain type's name.** No danger
  seen in a layout `Screw` for the type `Screw`; `ScrewLayout` might
  read better, but isn't a must. The name is the generator's choice,
  not a rule QLive applies.
- **One namespace for all sources**, server providers, generators and
  the application's `init` alike, and they have to arrange themselves
  in it. Open: whether a name may be overridden -- the application
  patching a server layout, as `registerConverter` overrides QLive's
  own -- or the sources only merge by distinct names, a duplicate
  being an error as for providers above.
- Open: generated layouts in the cached bootstrap add up for many types
  -- the catalog warning again -- unless layouts are fetched by name on
  first use.

## Carried over, not discussed again

From the earlier sketch, still plausible, but worked out before the
concept settled:

- **Field errors in the working set**, as their own axis next to
  `MergeFieldStatus`: invariant results evaluated on every change, and
  server verdicts keyed by (type, id, field). `merge()` refuses while
  client-side errors exist. Text that didn't parse is not among them;
  it stays in the form context.
- **A screen layer**, one per screen, holding what only exists while
  there is a screen: touched state, a save attempt, the display policy
  for errors, one submit across all groups, mapping server verdicts onto
  fields, a per-field view. It owns no values and renders nothing; no
  adapters for react-hook-form, which would keep a second copy of the
  draft.
- **Field groups**: a small form over one entity, anchored by a path
  with one relation hop per step, re-bound when the relation is
  reassigned. One big form per screen is not the answer: forms don't
  nest, and an edit screen contains other forms, a grid's filter or a
  picker's search. Each group renders its own `<form>`, or none, and all
  submit to the same screen layer. A group may render as `<fieldset>`.
  **Saving one group alone is not supported**: a screen whose parts are
  saved separately opens a working set per part.
- **Sections** such as "delivery address" hold on any device; where
  they are placed depends on the device.

## Rules that act (raw notes)

Thinking aloud, not a design. Collected 2026-10-19.

**What is assumed so far.** Rules change field modes and field values.
The FilterDSL value operations (`add`, `mul`, `concat`, `upper`, ...)
are enough for a first implementation of values, and a validation rule
is already node data in `actions.md`: a condition, the fields it
blames, a message key. The model holds no functions, which is what
lets one rule run on the server and in the browser.

**Rules are a DAG, layouts stay trees.** Node-based like Blender's
editors: one subexpression feeding several nodes, nodes with several
outputs, so the serialized form is nodes and links rather than nesting.
Layouts stay plain POJO trees. An editor would present both together,
perhaps the layout as one node with a socket per field -- whatever is
easiest.

**Rules act on the client only**, on client events or on the result of
a validation expression, setting a field's mode or value. **Reaction**
is the working term for that: an event, or a validation result, wired
to an action, as Unreal Blueprints wire execution. The DAG is
constant. **A computed value is continuous and one-way**: gross follows
net, nothing flows back. Whether the user may override it is the
target's mode, not a kind of rule: gross in `text` is derived, city in
`normal` is a prefill the user overwrites until the postal code
changes again. So there is one mechanism with timing modes rather than
continuous and triggered rules side by side: on every change, or on
blur where recomputing on every keystroke is too slow.

**The server regards only the validation expressions** and fails a
write where one doesn't hold. It has no event system: **a value a rule
set is a change like an edit**, the merge sends it, and validation
checks it there like any other. Marking some validations client-only
stays an option; the start is without it. Sinks may not be needed; errors
could be one.

**Form rules come from two places**: the layout, situational to one
form, and the type, as invariants are. A type's rules apply to every
form over it; its validations hold for every writer, its actions only
where a form runs them.

**The editor is half a contradiction** with "The format on the server",
which says an editor serves a user group QLive doesn't have. QLive
still targets developers, not people who only work in a graphical
editor. But actions and rules are where developers talk to people who
know the business rules without being developers, and a Java builder
gives only structural compile-time support for concepts this unwieldy.
A typed, generated DSL in the style of jOOQ would be serious
complexity; next to it, a simple helper UI for actions and rules looks
cheap.

**Analogies.**

- **Blender**: typed sockets with implicit conversion or refusal (QLive
  scalars are typed everywhere now); node groups as reusable subgraphs
  ("end >= start" for any type); geometry-node fields, a graph
  evaluated per element in a context, like a condition per row;
  drivers, a property computed from an expression. Pure dataflow, no
  events.
- **Unreal Blueprints**: execution wires say when, data wires carry
  pure values -- the state/event split in node form.
- **XForms binds**: per field `relevant`, `readonly`, `required`,
  `constraint`, `calculate`, which maps almost one to one onto modes,
  required, validation and computed values. `calculate` makes a field
  derived and not overridable.

**Scenarios.**

1. Derived: net + tax = gross. Follows net, kept in `text`.
2. Prefill: postal code -> city, overridable until the postal code
   changes again, usually an asynchronous lookup.
3. Mode toggle: "different delivery address" enables the address
   fields.
4. Cross-field validation: end date >= start date. Which field is
   blamed, or both?
5. Reset: changing the country clears the region.

**Questions.**

- Chains (A sets B, B enables C) and conflicts (two rules on one
  field's mode): probably a cycle error, perhaps with a maximum depth;
  to be seen once there are rules to try it on. A two-way link -- edit
  net or gross, the other follows -- is a reaction on each field, which
  makes it a cycle by construction. It only settles if setting a value
  equal to the current one (`scalarEqual`) fires no event, which the
  cycle check has to allow for.

## Naming

**Layout.** The merge side's internal "field layout", the ordered fields
a version mask indexes, is being renamed to field set, which removes
the ambiguity.

## Open items

- **Rules that act**: see the raw notes above.
- **Asynchronous checks** and their pending state.
- **A picked value the field's condition no longer allows**, after a
  field it depends on changed: shown as invalid, or cleared.
