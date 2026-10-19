# Layouts (design)

Status: concept settled, building blocks sketched, details open.
Written 2026-10-18, revised 2026-10-19. Field modes and input
conversion are built, the rest is not.
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

## The building blocks (sketch)

API sketch, not built. The shape follows the grid: headless hooks
carrying all the logic, default components that are nothing but those
hooks plus markup, and an auto-form composed from those components
alone. `Form` is decided: a form references a layout, and imported from
the package's namespace it can sit next to other `<Form/>` components.
The other names are working names.

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
    type: string
    merge: MergeAccessor
    /** the action's input, the third source; null without an action */
    input: ActionInput | null
    /** the fields whose text didn't parse, by path */
    invalid: ReadonlyMap<string, string>
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
    type: string
    name: string
    /** scalar type name, or the related type for a relation */
    scalarType: string
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
type FieldWrapper = ComponentType<{ field: FieldState, children: ReactNode }>

interface WidgetOverride
{
    /** root type of the layout the field is in, `"*"` for any */
    layout: string
    /** field path from that root */
    field?: string
    /** scalar type of the field */
    scalar?: string
    widget?: Widget
    wrapper?: FieldWrapper
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

**Overrides are matchers in config**, applied to what the application
didn't write itself: layouts from the server and from `deriveLayout()`.
A hand-written form names its components and needs none. A matcher
keys on `layout`, `field` and `scalar` and nothing else. `layout` is
the layout's root type rather than its name, since field paths start
there; two layouts of one type are told apart by the field paths they
use, if at all, and the derived layout, which has no name, is matched
by its type like any other. Matching an action seemed too esoteric to
be worth a key.

```ts
config().widgets = [
    { layout: "*", scalar: "Money", widget: MoneyInput },
    { layout: "Bar", field: "num", widget: Slider }
]
```

**The most specific matcher wins**, and the later of two equally
specific ones: a field beats a scalar, a named root type beats `"*"`.

Resolution for a field, first hit wins: the component the layout node
or `Field` gives, the name a server layout gives, an override, the
built-in for the scalar type or relation kind -- `text`, `number`,
`checkbox`, `date`, `datetime`, `select` for enums, `toOne`,
`manyToMany`. No hit is the dev placeholder.

**Conflicts are a wrapper.** The buttons that resolve a conflicting
field are merge UI, not a field's, so QLive ships them as a
`FieldWrapper` around the widget, matched like a widget override. The
auto-form applies it; a hand-written form uses it or not, which lets an
escape hatch that doesn't merge ignore merging entirely. Conflict UI
varies by application -- inline buttons, a popover, a side-by-side
view -- and a wrapper is where that variety goes, without every widget
having to cooperate.

### The layout format

```ts
interface Layout
{
    type: string
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

The conflict buttons become the wrapper the auto-form applies.

### Related rows

A to-one picker needs the rows it offers, and a field whose query
selected the key without the relation object needs the name of the row
the key points at. `pick()` has the same need today and gets a
document handed in. For an explicit field a view can still pass one; a
derived layout knows the target type but has no query for it -- none
may exist, and an application query that does may carry conditions of
its own.

**A field may add a condition**, which is how a picker offers only the
members of this project. Where the rows come from, and where that
condition is evaluated, depends on the target type, and the picker
reads the same either way:

- **A catalog type** (`catalog: true`): from the bootstrap, filtered in
  the browser with the FilterDSL evaluator (`conditionPredicate()`).
  The server is never asked.
- **Any other type**: from the related-rows endpoint, filtered on the
  server through the query document service.

**`catalog: true`** is type meta for a type whose rows are constant for
the lifetime of an application run. Its rows ship in the cached part of
the bootstrap. The user documentation must stress what that costs:
every row of every catalog type is in every client's bootstrap, and a
constant table can still be too large to be a catalog. A catalog type
is visible to everyone -- or at least to everyone who can see a form
using it, once there is authorization: nothing filters what is already
in the bootstrap.

**The related-rows endpoint** serves the other types. Putting a
generated query per type into the schema is hard without an explicit
annotation per query, so it fakes GraphQL instead:

- **One REST endpoint** takes a type name and a `QueryConfig` (condition,
  sort, page) and runs it through the query document machinery, which
  already yields a map graph with every scalar coerced; the endpoint
  only serializes it. It selects every scalar field of the type and no
  relation, a selection it builds itself. A query string per type, to
  select something else, may come later.
- **Valid types** are those a foreign key references, so the endpoint
  serves pickers and nothing else.
- The type's `queryConfig` meta, the condition every query of the type
  carries, applies as it does everywhere, and so do
  `authorization.md`'s row-level rules.
- **The field's condition** comes from the client as it is. It can only
  narrow what the type's own condition and authorization allow, never
  widen it.

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
- **Sections** such as "delivery address" hold on any device; where
  they are placed depends on the device.

## Naming

**Layout.** The merge side's internal "field layout", the ordered fields
a version mask indexes, is being renamed to field set, which removes
the ambiguity.

## Open items

- **Where a field's mode comes from** beyond the derived default: the
  layout, authorization, rules -- and which of them wins.
- **Field-level authorization** in a derived layout: "may see the row,
  not the salary".
- **Rules that act**: a change in one group enabling or recomputing
  fields in another, and whether that is a layout's concern or view
  code.
- **Asynchronous checks** and their pending state.
- **Saving one group alone**, merging only its own entity while other
  groups stay dirty.
