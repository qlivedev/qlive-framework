---
title: Edit rows with a working set
description: Drafts, field state, saving, and what a form does with a conflict.
sidebar:
  order: 10
---

A working set holds the rows a form is editing, the changes made to them,
and the one call that writes them. This page builds a form on one.
`qlive-test`'s `src/app/bar/Edit.tsx` is the same form as working code, and
what the merge does behind it is
[MergeService](/qlive-framework/explanation/merge-service/).

The server side is
[Enable merging for a type](/qlive-framework/how-to/enable-merging-for-a-type/)
-- a type without a `version` column is editable here and merges like a
type nobody else is editing.

## Select what the merge needs

A query whose rows are to be edited selects `id` and `version` on every
row, and `id` on the rows of a link type:

```graphql {6,8}
query Q_Bar($config: QueryConfig!) {
    queryBarDocument(config: $config) {
        type
        config
        rows {
            id name num description version
            bazLinks {
                id version barId bazId
                baz { id name version }
            }
        }
    }
}
```

`version` is the base every write of that row is held to; the link row's
`id` is what a removed association is deleted by. A row of a versioned type
that arrives without its version registers like any other and refuses the
write that would need one, naming the query that read it -- so this is a
mistake you hear about, not one that loses an update.

## Make it, and register what it edits

```tsx
const bars: Q_BarResult = useInjection(Q_Bar);

const [ws] = useState(() => {
    const set = new WorkingSet();
    set.register(bars);
    return set;
});
```

The working set is made outside React and lives as long as the editing
does. `register()` walks the document and remembers what every row looked
like and which version it was read at -- anything with an id is an entity,
whatever type it is and however deep it sits, so registering the document a
view renders registers everything that view can edit.

Registering once, in the initializer, is deliberate: a view that rendered
before its rows were registered could not edit them, and a merge that lands
refreshes the documents and registers what comes back by itself.

## Read it

```tsx
const {dirty, conflicts, view, merge, undo, setView} = useWorkingSet(ws, {watch: true});
```

A working set is a store like a query document, so this is the same three
lines `useInjection()` is: the component re-renders whenever anything
changes -- a field typed into a draft, a row created, a merge coming back
with conflicts. `dirty` drives the save and undo buttons; `conflicts` is
empty until a merge comes back with some.

`watch` is [live updates](#find-out-before-the-save) below, and optional.

## Edit through a draft

```tsx
const bar = ws.edit(row);

<input value={bar.name ?? ""} onChange={e => { bar.name = e.target.value; }}/>
```

`edit()` returns the draft of a row: the same row with every change made to
it so far, and writes to it recorded rather than applied. The row itself is
never touched.

A draft is read rather than kept -- hold the row, call `edit()` on every
render. It is not the row (`draft !== row`), there is one per row however
many components ask for it, and handing a draft back in returns it
unchanged, so calling it twice is free.

An input hands over a string whatever the field is, and a working set
records the value it is given. Convert on the way in where the field is not
a string:

```tsx
onChange={e => { bar.num = Number(e.target.value); }}
```

## Render what happened to a field

```tsx
const bar = ws.edit(row);
const merge = useMerge(bar);

for (const name of fields)
{
    const field = merge.field(name);
    // <input className={field.className} .../>
}
```

One hook per row, not per field. A hook cannot be called from a loop over a
field list, so what `useMerge()` returns is an accessor: `field(name)` is an
ordinary function a generic renderer calls as often as it likes, in a loop,
in a callback, or in a child component it was handed. `merge.of(row)`
reaches another row of the same working set without a second hook.

`field.className` goes on the input and carries the status:

| status | |
|---|---|
| `unchanged` | nobody touched it since the row was read |
| `changed` | the user changed it and nobody else did |
| `conflict` | both changed it, and the user's value is the one standing |
| `resolved` | both changed it and the user looked at it and chose |
| `remoteChanged` | somebody else changed it and the user did not, so their value was taken |

The accessor also answers the question in the other direction --
`changedFields()`, `conflictedFields()`, `resolvedFields()`,
`remoteChangedFields()` -- for a summary line or a check of whether
anything is left to decide.

## Associations

A link array is set like any other field and means something else: it says
which rows this one is associated with, and the merge turns the difference
into inserts and deletions of the link type.

```tsx
bar.bazLinks = checked
    ? [...bar.bazLinks, {baz}]
    : bar.bazLinks.filter(link => (link.bazId ?? link.baz.id) !== baz.id);
```

A new association is written as the row it is about, `{baz}` and nothing
else. Nothing here writes a `Baz`, and nothing here has to know that
`BarLink` exists beyond naming the rows it points at.

## New rows and deletions

```tsx
const fresh = ws.create("Bar", {name: "New bar"});
ws.delete(row);
```

`create()` generates the id here rather than in the database, so that new
rows can refer to each other before the server has seen any of them -- a
new `Bar` and a new `BarLink` pointing at it go over in one merge. What
comes back is a draft like any other.

`delete()` marks a row for deletion; a row that was only ever created here
is dropped instead, there being nothing to delete and nothing to tell the
server about.

## Save

```tsx
<button disabled={!dirty} onClick={() => merge()}>Save</button>
```

`merge()` writes everything the working set holds -- every change and every
deletion, in one transaction or none of them.

**It landed.** The changes are gone and the registered documents run their
query again. A version left standing in a document that stayed on screen
would fail the *next* edit, so refreshing is part of a merge that landed
rather than the application's chore.

**It did not.** Nothing was written. The user's changes are all still here,
the fields that clashed are marked, and every conflicted row now stands
against the version that is in the database -- so saving again writes the
user's values over the other ones. That second save is deliberately theirs
to make: a working set never re-sends by itself.

## Offer the choice

A conflict comes back already standing as the user's value, because the
person present typed it on purpose and the person who did not is not here
to argue. Resolving is a correction, and this is what offers it:

```tsx
field.status === "conflict" && field.storedKnown && (
    <>
        <button onClick={() => field.resolve("mine")}>yours: {String(field.mine)}</button>
        <button onClick={() => field.resolve("stored")}>saved: {String(field.stored)}</button>
    </>
)
```

`mine` is what the user has, `stored` is what is in the database as far as
this working set has been told. Choosing `stored` leaves the other value in
place and does not throw the user's away, so choosing `mine` afterwards
brings it back. `resolveWith(value)` takes a third value that is neither --
separate from `resolve()` rather than an overload of it, because the
choices are strings and a string field could not tell `resolve("stored")`
from a user meaning to store the word.

**Ask `storedKnown` first.** It is false where the field is known to have
changed and not known to what: a push message carries a mask and no values,
and a type that did not declare
[`resolveConflicts`](/qlive-framework/how-to/enable-merging-for-a-type/#what-a-type-declares)
carries none either. Labeling the read value "saved" there names it as
something it is not, so show the marking and leave the buttons out:

```tsx
field.status === "conflict" && !field.storedKnown && (
    <span>somebody else changed this too</span>
)
```

`merge.gone` is the other end of it: the row was deleted while the user was
editing. There is nothing to merge into and no field of it carries a
conflict.

## Three views of the same form

```tsx
setView("mine");     // what the user typed
setView("stored");   // what is in the database
setView("merged");   // the two folded together -- the default
```

One flag for the whole working set, and no input has to know about it: a
draft read is what returns the value, so the same form renders all three.
`merged` is the one a form wants -- the user's edits, with the fields
somebody else changed and this user has no opinion about taken silently.

## Undo and clear

`undo()` takes every change back, leaving the rows as they were registered.
Conflicts go with them, and so do the decisions taken about them: they are
all about a write that no longer exists. What stays is what the working set
was told about the database -- a field somebody else changed is still
changed, and the form still shows it as such. That is knowledge rather than
unsaved work.

`clear()` drops everything, the registered documents included. After it,
nothing is being edited.

## Find out before the save

```tsx
useWorkingSet(ws, {watch: true});
```

With the flag on, other people's writes to the rows this working set holds
land in it as they happen: a field somebody else changed takes the status
it would have taken at save time, so the user sees it while they are still
typing. Nothing else changes -- the merge is still what detects a conflict
and still the authority on the version -- so a working set read without it
works and finds out at save time.

A view that sets this must not also call `useDocumentWatch()` on the
document these rows came from. That is the same news twice, and acting on
the document half means `update()`, which swaps out the row objects the
drafts are standing on.

<!-- TODO: link the push explanation here once it exists -- what the
     subscription actually is, and that watching costs one. -->

## Without React

`ws.accessor(row)` is the plain call under `useMerge()`, for a form library
binding to it, a test, or a headless check. `ws.raw(row)` gives the current
values of a draft as a plain object, for anything that wants a value rather
than a draft. `mergeWorkingSet(changes, deletions, config)` is the write
without a working set around it, for a service with nobody in front of it
-- and `new WorkingSet({conflictValues: false})` is the same caller
declining a copy of the other user's values it has no use for.

<!-- TODO: a worked "bind a form library to the accessor" example would be
     the natural follow-up here, once one exists in qlive-test. -->
