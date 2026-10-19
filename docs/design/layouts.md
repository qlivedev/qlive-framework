# Layouts (design)

Status: concept settled, details open. Written 2026-10-18, revised
2026-10-19. Not built.
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
holds all its state. For a form, the state is the query document, the
working set's drafts and the action's input; if all three are reachable
without a form context, the parts stay as independent as `<Pager/>`
and `<SortHeader/>`.

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

Parsing what an input hands over needs `parse(string, type)` in
`converter.ts`, next to `format`. It replaces `typed()`, and an
unparseable value becomes a client-side error.

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
  client-side errors exist.
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

- **Editability in a derived layout**: whether query fields are
  editable by default, as on a plain edit screen.
- **The building blocks**: what the parts are, and whether the three
  sources of state can really be reached without a form context.
- **The auto-form component's name.**
- **Field-level authorization** in a derived layout: "may see the row,
  not the salary".
- **Rules that act**: a change in one group enabling or recomputing
  fields in another, and whether that is a layout's concern or view
  code.
- **Asynchronous checks** and their pending state.
- **Saving one group alone**, merging only its own entity while other
  groups stay dirty.
