# WorkingSet and merge (design)

Status: the build order below is built, steps 1 to 8. What is designed
and not built is everything the build order never listed: the unload
guard and the navigation guard it registers, parking a change set, and
push. Written 2026-09-09.

Write support for QLive, modeled on the MVCC merge built in Automaton.
This is a re-design, not a port: the mechanisms that earned their keep
are carried over, the parts that kept it from being used are not.

## Problem

Querying has a generic path. One `QueryDocument<T>` query per type,
driven by a config, no handwritten query per use case. Writing has
nothing: an application declares its own `*Input` types, writes a
mutation per operation, and maps the input onto jOOQ by hand. On top of
that, every one of those mutations is a last-write-wins update, so two
users editing the same row silently lose one of the two edits, and
nobody writes the code that would notice.

Both halves of that should be as generic as the query side:

- **No `*Input` types.** One mutation stores changes to any domain type.
- **No lost updates.** Concurrent edits are detected per field, merged
  where they do not overlap, and where they do, resolved by the user in
  the form they were editing.

### Who pays

There is no "remote user". Whoever saved first is gone -- their write
sailed through unexamined and they went home. The only person present is
the one who arrived second, and every bit of work this feature creates
lands on them, on behalf of somebody who will never know it happened.

That asymmetry is the design constraint, and it is why this reads
differently from a version control merge:

- **"Ours" and "theirs" is the wrong vocabulary.** It describes two
  parties negotiating. What is actually on the other side is a stored
  value with nobody behind it. The API says `mine` and `stored`, and a
  form labels them "your value" and "saved value", because that is what
  the person looking at the screen is choosing between.
- **The default has to favor the person who is here.** They typed
  those values on purpose, minutes ago. A field they changed keeps their
  value unless they say otherwise; a field they did not change takes the
  stored one, because they have no opinion about it and taking it is
  just catching up.
- **Nothing blocks the save.** An unreviewed conflict is marked, not
  fatal. Making the second user clear a checklist before they may store
  their own work is the failure mode that made the Automaton dialog
  unused, and a modal is not the only shape it comes in.
- **The real fix is upstream.** Every conflict resolved at save time is
  one that could have been avoided by telling the user their row changed
  while they were still editing it. That is out of scope here, and it is
  the most valuable thing to build next.

## What already exists, and why it decides the design

**`GenericScalar` is already registered.** `QLiveDomain.newDomain()`
registers `GenericScalar`, `DomainObject` and the rest. A change can
therefore travel as `{ field, value: { type, value } }` and
DomainQL coerces the value to the Java type the field actually has. That
is what makes one generic mutation possible, and it is the whole reason
no `*Input` type is needed:

```graphql
mutation mergeWorkingSet(
    $changes: [EntityChangeInput]!,
    $deletions: [EntityDeletionInput]!,
    $mergeConfig: MergeConfigInput!
) { ... }
```

The input types here are the framework's four, not the application's
forty.

**The client already has the relation meta.** `config().meta.relations`
carries every `RelationInfo` with source/target type and the
`leftSideObjectName` / `rightSideObjectName` the GraphQL fields are
named after. Automaton's server-side `MergeTypeInfo` derives many-to-many
relations by scanning exactly this; the client can derive the same thing
from what the bootstrap already ships, so resolving `bazLinks` into
`BarLink` inserts and deletions is a client-side operation needing no
new server knowledge.

**The client has no MobX.** Automaton's `WorkingSet` observes domain
objects through MobX reactions -- `registerReaction()` schedules
`recalculateChanges()` whenever an observable property is written.
QLive's state model is the one `QueryDocument` uses: a mutable store with
`subscribe`/`getSnapshot`, read through `useSyncExternalStore`. Change
tracking has to be rebuilt on that, and it is the one part of this design
that is not a port. See "Drafts are proxies".

**`_type` is gone.** `generateTS.js` wrote `_type: "Bar"` into every
generated domain type, nothing stamped it at runtime, and
`renderPickFields()` left it out of the `Pick<>` a query result type is
built from -- a property declared everywhere and present nowhere. It
could have been made real, by stamping it in the converter and always
picking it. It was dropped instead.

A working set does have to know what an object *is* to record a change
against it, and the schema says so. The walk starts at the row type of
the document being registered, and the type of a field says whether the
value under it is a row of its own. That is knowledge the client has
anyway; it costs nothing on the wire, it keeps a property off every row
of every query for the sake of the few that are edited, and it covers a
row `ws.create()` made, which no converter ever saw. The one thing
`_type` would buy that this does not is runtime type inspection without
the schema, and an application that wants that can read the schema.

**`QueryDocumentService` is the read side of the same coin.** Rows come
out of it as plain objects with their relations filled in. Those objects
are what gets edited, and what a working set registers.

## Data model

Two tables, plus one column on every participating type.

```sql
CREATE TABLE public.app_version
(
    id          character varying(36) NOT NULL,
    field_mask  numeric(39,0)         NOT NULL,
    owner_id    character varying(36) NOT NULL,
    created     timestamp without time zone NOT NULL,
    entity_type character varying(100) NOT NULL,
    entity_id   character varying(36) NOT NULL,
    prev        character varying(36),
    field_layout character varying(64) NOT NULL,
    CONSTRAINT pk_app_version PRIMARY KEY (id),
    CONSTRAINT fk_app_version_owner_id FOREIGN KEY (owner_id)
        REFERENCES public.app_user (id)
);

CREATE TABLE public.app_field_layout
(
    id          character varying(64) NOT NULL,
    entity_type character varying(100) NOT NULL,
    fields      text                  NOT NULL,
    CONSTRAINT pk_app_field_layout PRIMARY KEY (id)
);
```

A row of a versioned type carries `version varchar(36)`, holding the id
of the `app_version` record describing the change that produced its
current state. Each version record points at the one before it through
`prev`, so the records for one entity form a chain, and `field_mask` says
which fields that particular change touched.

**Why `version` carries no foreign key.** Version records are pruned
after a lifetime while the rows they describe stay.
A foreign key would either block the cleanup or, with a cascade, null out
the version of a live row -- and a row whose version is null cannot be
written through the merge path at all. The chain is deliberately
best-effort: a base version whose record is gone is not an error, it just
means the server cannot tell which fields changed since and has to assume
all of them (see "Detecting the conflict").

**`numeric(39,0)`** is 128 bits, which is the field limit: 39 is
`ceil(log10(2^128))`. A type with more than 128 fields is rejected at
startup rather than silently masked wrong.

**The field index is the position of the field in the type's GraphQL
field list**, which DomainQL sorts alphabetically. That is not stable:
adding a `flag` field to `Bar` shifts every index from `f` on, so a mask
written before the deployment names a different set of fields after it --
and names them confidently, which is the bad part. A misread mask does
not fail, it merges the wrong fields silently.

**And it is not only the database that moves it.** The field list is
built from the columns *plus* the `leftSideObjectName` /
`rightSideObjectName` of every configured relation, the computed
`@GraphQLField` properties, and which scalars the domain has registered.
Renaming `bazLinks` in `DomainQLConfiguration` shifts the same bits with
no DDL involved, which rules out fixing this from a migration: there
would be nothing to hang the migration off.

Automaton lived with this because version records expired after 48 hours
and a deployment is a restart. That is a bet on nothing important
happening in a two-day window, and "park it until Monday" is a feature
that deliberately reaches across one.

**`app_field_layout` is the fix.** A version record names the field
layout its mask was written against, and the layout table holds that
layout's ordered field-name list. On read there are three cases and no
migration step in any of them:

- **The layout is the current one.** The mask reads directly.
- **The layout is an older one we still have.** Both lists are in hand,
  so the mask is permuted into today's positions and stays *accurate*.
  Bits for fields that no longer exist are dropped, which is right: a
  field that is gone cannot be in conflict.
- **The layout is unknown**, because the record predates the table or was
  written by something else. The mask is treated as unknown, which
  already has a meaning -- assume every field changed, the same as for a
  record whose version was pruned.

The third case is the fallback rather than the norm, and that is the
whole point. A layout hash on its own can only *detect* that the mask
moved; keeping the list is what lets us undo it. Detection alone would
mean every deployment turns the Friday-to-Monday park -- the case this
feature exists for -- into a full conflict instead of the clean
auto-merge the mask was recorded to make possible.

Nothing about this is a write-path dependency, a step that has to run, or
a lock. Records written under two layouts can sit in the table side by
side, which is what makes a rolling deployment a non-event, and the
remapping happens on the conflict path only -- `rowcount == 0`, walk the
chain -- which is rare and behind the in-memory version holders anyway.

**`id` is a hash of the layout, which makes writing it an idempotent
upsert.** SHA-256 of the type name and the ordered field names, joined
with a separator that cannot occur in a GraphQL name -- without one,
`["ab", "c"]` and `["a", "bc"]` hash alike, which is a real collision at
a rate nowhere near the birthday bound. Hash the list that actually
assigns the bit indices rather than a separately sorted copy of it, or
the two can drift and the hash certifies a layout nothing ever used.

The type name is in the hashed input on purpose, even though the bit
semantics do not need it: `Bar` and `Baz` in qlive-test have field lists
that are identical today, and sharing one row between them would make
`entity_type` meaningless and "what did Bar's fields look like on Friday"
unanswerable.

**A collision is not a design consideration here, and the hash does not
have to be trusted anyway.** At 256 bits the birthday probability over
the ~10^4 distinct layouts a long-lived application accumulates is about
10^-70, far below the rate at which the row corrupts on disk, and there
is no adversary either, the input being the application's own field
names. More to the point, the reader has the list itself and compares
that, and the writer sees a collision for free -- a layout row that
already exists under our id with different `fields` is one, and it fails
loudly at startup rather than silently merging the wrong fields.

`varchar(64)` is exactly SHA-256 hex with no slack for an algorithm tag.
Truncating to 128 bits (32 hex characters, still ~10^-31 over the same
10^4) or storing base64url (43) leaves room for a `v2:` prefix. Either
way an unrecognised layout falls back to the third case above, so getting
this wrong is safe rather than fatal -- worth settling when something
first writes the column, not before.

This table and this column are what this design adds to the model it
inherits from Automaton. Everything else in it was already there.

## Which types take part

**Any type with a `version` field is versioned.** Nothing is declared,
nothing is registered. The server derives it from the jOOQ table, the
client from the GraphQL type it already has in `config().typesByName`.
Both ends reach the same answer from the schema, so there is no way for
them to disagree.

Everything else about a type's merge behavior is opt-in type meta data,
written by a `MergeMetadataProvider` alongside the existing
`QueryConfigMetadataProvider`, and read through a `MergeMeta` companion
holding the property names:

```java
@Bean
public MetadataProvider mergeMetadata()
{
    return MergeMetadataProvider.newProvider()
        .resolveConflicts(Bar.class)
        .ignoreFields(Foo.class, "lastAccessed")
        .linkType(CorgeLink.class);
}
```

- **`resolve`** -- opt in to full merge for this type. Off, a real
  conflict fails the write and the application is told which fields
  clashed. On, the conflict comes back with both sides per field for the
  view to resolve. This is the "metadata configuration to enable full
  merge" split: field-mask conflict *detection* and auto-merge happen for
  every versioned type without asking, because they are always right;
  handing a user two values and asking them to choose is a decision about
  the application's UI and is declared.
- **`ignoredFields`** -- fields whose change neither sets a mask bit nor
  ever counts as a conflict. Automaton's `MergeTypeConfig.isIgnored`.
- **`autoMerge`** -- default true. Off, a non-overlapping concurrent
  change still comes back to the user instead of merging silently.
- **`linkType`** -- only for link tables that carry extra fields and
  therefore escape auto-detection.

The client reads the same meta through declaration merging on
`DomainQLTypeMetaProps`, the way `queryConfig` and `maxPageSize` already
do.

## Server pipeline

`MergeService.merge(changes, deletions, mergeConfig)` returns a
`MergeResult`, in a `REQUIRES_NEW` / `REPEATABLE_READ` transaction that
is marked rollback-only when the result is not done. Either everything in
the working set lands or nothing does.

### A -- order

New entities referenced by a foreign key are inserted before the rows
referring to them. The client generates ids for new entities, so a change
can name a row that does not exist yet, and the ordering is a
topological sort over the foreign keys among the changes.

### B -- write

Per change, an `INSERT` or an

```sql
UPDATE bar SET ..., version = <new uuid> WHERE id = ? AND version = <our base>
```

The version condition is the whole lock. It is optimistic, it costs
nothing when there is no contention, and it needs no row locking. An
`EntityVersion` record is built alongside with the mask of the fields
this change touched, and held back until the whole merge succeeds.

An unversioned type is written the same way without the version
condition and without a version record. Last write wins there, as it
does today -- taking part is what having a `version` column means.

**A row refused for being a duplicate is a conflict, not an error.** A
row that does not exist yet has no version, so a unique constraint is the
only thing that can see a second writer coming, and what it sees is the
same thing every other conflict is: somebody got in first. Everything
else the database refuses -- a foreign key pointing at nothing, a column
that may not be null -- stays a programming error and stays a GraphQL
error.

It is caught by SQL state (`23505`) through the whole cause chain rather
than by exception type, because whether the application installed
Spring's exception translator is the application's business. Postgres
aborts the transaction on a constraint violation, so nothing more can be
written and the row in the way cannot even be read back: the conflict
carries the type and id of the row that was refused, no fields and no
stored version, and it is the whole answer. Which row was in the way is
not said and cannot be from here -- a constraint names columns, not a
row.

### C -- detect the conflict

`rowcount == 0` means somebody else wrote the row since we read it. Read
the current row back, then ask the version chain what changed in between:
walk from the version whose `prev` is our base up to the current one,
OR-ing the `field_mask`s. That union is *their* changed fields.

- **No record for our base version** (pruned, or older than the
  lifetime), or a record naming a field layout we no longer have: assume
  every field changed. Conservative, and the only safe answer. A record
  whose layout we do have is remapped into today's field positions first,
  so a deployment in between does not land here.
- **Their fields and our fields do not intersect**: a pseudo-conflict. A
  edited `name`, B edited `num`, and both edits belong in the row. With
  `autoMerge` on, retry the write with their version as the base and
  carry on. This is the case that makes the whole mechanism worth having
  and it is the common one.
- **They intersect**: a real conflict, one `MergeConflictField` per
  overlapping field carrying `mine` and `stored`. Fields changed by the
  other write that we did not touch are attached too, marked
  informational: the merge takes those silently -- catching up on a field
  we have no opinion about is not a decision anybody needs to make -- and
  they are attached so the form can *show* what changed under the user
  rather than only what clashed.

Deletions run the same way: `DELETE ... WHERE id = ? AND version = ?`,
and a rowcount of 0 becomes a conflict saying the row was changed or
already deleted elsewhere.

### D -- links last

Link-type changes are applied only once the entity changes came through
clean. A link row is an insert or a delete of a row that exists to say
two entities are associated; it has no fields of its own to clash over,
so it cannot conflict, and holding it back means a failed merge does not
leave orphaned links behind. It also means the current associations can
be read back for the resolution UI.

### E -- commit

No conflicts: batch-insert the version records, publish them, return
`DONE`. Otherwise return the conflicts and roll back.

**Published, not handed over.** The records go out as a Spring
`ApplicationEvent` rather than straight into the in-memory version
holders. The holders are then one listener and a websocket push module is
the second, which is the difference between adding push and rewriting
this step -- see "When push arrives", where the version record is already
the notification. It costs one event class now.

**Cleanup.** A scheduled task drops version records older than the
lifetime from memory and from the database. Without it `app_version`
grows forever. `app_field_layout` is swept by the same task, dropping a
layout no version record names any more -- except the current one per
type, which is about to be written again.

**`ensureNotVersioned`.** A guard other services call before writing a
table directly, so that a versioned type written behind the merge
service's back fails loudly instead of corrupting the chain.

## Client: the WorkingSet

### A store, like a query document

`WorkingSet` is a mutable store with `subscribe`/`getSnapshot`, read
through `useWorkingSet()`. Same shape as `QueryDocument`, same reason:
the object has to be mutated in place or a subscription would point at a
stale one, and the snapshot is what makes a change visible to React.

```ts
const ws = new WorkingSet()
ws.register(document)          // base versions for every row and relation
```

`register()` walks the object graph along the schema: the document says
what its rows are, and the type of a field says whether the value under
it is a row of its own. Anything with an `id` is an entity; its
`version` is the base the merge will be checked against, and a deep copy
of its scalars and link arrays becomes the base snapshot to diff
against. An object without an `id` is no entity -- there is nothing to
name it by -- but the rows below it still are.

A row of a versioned type whose `version` was not selected is registered
like any other and refuses the write that would need one, in an error
naming both the type and the query that read it. **The refusal is at the
write and not at the registration**, which is the answer to build order
item 9: `register()` walks everything a query selected and most of what a
query selects is displayed rather than edited, so insisting there would
make a query unregisterable because of the lookup table behind a
dropdown. Writing one of the row's own fields, `delete()`, and a link
diff that would otherwise send a deletion with no base all refuse --
still long before a merge could lose an update, which is the whole reason
to say anything.

`edit()` itself does not refuse, and the reason is worth stating: **a
row's `version` is the base for writing that row's own columns and
nothing else.** A change to a link array is not a field of the row at
all. It becomes inserts and deletions of the link type, each held to the
version of the link row, and the source row is neither updated nor sent
-- so a view whose whole job is editing associations never needs the
version of the thing the associations hang off, and is not made to select
one it will never use.

The obligation on the framework user follows the same line: a query whose
rows have fields to be edited selects `id` and `version` on them, and a
query whose link arrays are to be edited selects `id` and `version` on
every link in them.

### Headless by construction

Three layers, and the rule is that React only appears in the top one.

1. **The store.** `WorkingSet`, the per-entity change maps, the base
   snapshots, the link diffing, `merge()`. Plain TypeScript. No React,
   no hook, no component. Every state it holds is an immutable snapshot
   replaced on change, the way a query document's is.
2. **The accessor.** A plain value read off that store, answering what
   the state of a field or an entity is and taking a decision back.
   Also no React: it is an object with functions on it, constructible
   and testable without rendering anything.
3. **The hooks.** `useWorkingSet()` and `useMerge()`, each a few lines of
   `useSyncExternalStore` over the two below them.

This is what lets a form library -- QLive's own later, or somebody
else's, or none -- adopt the merge instead of reimplementing it. A
binding that wants to drive an input from merge state needs layer 2 and
nothing above it. It is also the answer to the framework staying small:
the whole reactive part is one `subscribe`/`getSnapshot` pair per store,
which is the pattern `QueryDocument` already established, rather than a
reactivity system the application has to adopt to talk to us.

### Drafts are proxies

`ws.edit(row)` returns a `Proxy`, one per `(type, id)`, cached. Reads
return the current value, writes record a change and notify subscribers:

```ts
const bar = ws.edit(row)

bar.name = "New name"
bar.bazLinks = bar.bazLinks.filter(link => link.bazId !== id)
```

**The proxy is a convenience, not the foundation.** It is a thin
write-recorder over the entity's change map: a write goes into the map
and notifies, a read comes back out of it. It is not an observable object
graph, nothing below it is deep, and removing it would cost the design
nothing but syntax -- the same write is `merge.field("name").set(value)`
through the accessor, and the same read is `merge.field("name").value`.

That is deliberate. Live dirty state and one-draft-per-entity are
properties of the *store*, not of the proxy, so a form library binding to
layer 2 gives them up for nothing. What the proxy is for is the form
somebody writes by hand today, before there is a library, where
`bar.name = e.target.value` is the whole point. If a form library later
wants nothing to do with proxies, it never has to see one.

The costs are real and go in the documentation: a draft is not the row
(`draft !== row`), it must be read rather than stored in React state, and
anything leaving the working set for a plain object goes through
`ws.raw(draft)`. They are also confined to the convenience layer, which
is the reason to keep it thin.

`ws.create("Bar", { ... })` registers a new entity with a
client-generated UUID -- needed so that new entities can reference each
other before the server has seen either. `ws.delete(row)` marks one
deleted. `ws.undo()` restores the base values, `ws.clear()` drops
everything.

### Many-to-many

The link array is diffed against the base at merge time, exactly as in
Automaton: links in the base that are gone become deletions, links
present that were not in the base become inserts of the link type with
both foreign keys set. Which relation is which comes from
`config().meta.relations` -- the relation from the link type back to us
and the one from the link type to the other side.

So editing `bar.bazLinks` produces `BarLink` changes and never touches
`Baz`, which is what the user of the framework means by it and what the
GraphQL type of the field already says.

**A link is identified by the row it points at**, not by the link row's
own id. That is what a link array says -- which rows are associated --
so an association taken out and put back is no change, a link written
twice is written once, and a new association can be named as
`{bazId: baz.id}` or as `{baz}`, the second of which is also the form a
view can render straight away.

**A link row the application created itself is left alone.** A link type
carrying a field of its own -- the `linkType` declaration above -- cannot
be written by a diff that only knows two foreign keys, so the
application makes the row with `ws.create()` and puts it in the array.
It is a row of the working set and goes over as one; the diff sees that
and does not insert it a second time.

**A link array the query did not select cannot be changed.** There is
nothing to diff against, and an empty base would turn every association
already in the database into a duplicate insert. The working set refuses
the write and says so, the way it refuses a versioned row registered
without its version.

**An association is unique on the pair of rows**, and the database is
where that is said. Without the constraint two people adding the same
association concurrently wrote two link rows with different ids and
identical foreign keys: no version to clash on, nothing to refuse it, no
conflict of any kind. It self-heals on read, a link array being a set,
and removing the association deletes both rows -- but it accumulates rows
nobody asked for and nobody sees. `uc_bar_link_bar_baz` in qlive-test is
the shape an application copies.

#### A conflict about a link is reported against the array

A link diff makes rows nobody named. The user said "the associations of
this Bar are {B}"; the merge said "delete `BarLink` 4f2a..., insert
another". So a conflict about one of those rows cannot be shown where it
happened -- a `BarLink` id is in no form, and a view editing associations
through checkboxes has never heard of the link type.

**The conflict is therefore traced back to the array it came out of.**
`merge()` remembers which link field each synthesised deletion belongs to
and, when one comes back conflicted, marks that field on the source row
as remotely changed. The array gets a status, a class and a `resolve()`
like any scalar, and the same three maps produce all of it -- nothing
new in the accessor, nothing new in the hooks.

It is marked as **changed and not to what**: `undefined` in `stored`, the
same as a scalar whose value was withheld. An association somebody else
took away says nothing about the ones they may have added, so the set
that is stored is not knowable from here. What the form shows for it is
what the row was read with, which is the existing rule for that case.

**An insert the database already holds is not sent again either.** The
mirror image, and it arrives the other way round: nothing local can see
it coming, so the first merge asks, the constraint on the pair refuses,
and the conflict that comes back says the association exists. The working
set records it as made -- the only way a new link row is refused is that
constraint -- and the next merge does not ask for it. `StoredState` grew
a `links` map for it, which is the same seam a push message will use to
say an association appeared.

**A deletion whose row is already gone is not sent again.** Nobody asked
to delete that link row; they asked for a set of associations, and
somebody else has already made the set true. Re-sending it would fail the
whole merge over a state the user wanted and has -- and would keep
failing it, since a row that is gone has no version to move on to. The
diff skips it, which is the one place this can be decided: the server
cannot tell a synthesised deletion from a row the user pointed at and
deleted on purpose, and that second one is worth reporting.

**A merge with nothing left to send still landed.** Where every change
was satisfied elsewhere, the documents are stale by exactly the writes
that satisfied them, so they refresh as after any other merge. The old
shortcut -- nothing to send, so nothing to refresh -- now applies only to
a working set that was never dirty, which is the case it was written for:
a form that saves an untouched row.

### Merging, and resolving in the view

```ts
const result = await ws.merge()
```

Done, and the working set clears and refreshes the documents it
registered -- a stale `version` in a document that stayed on screen would
fail the *next* edit, so refreshing is part of a successful merge rather
than the application's chore.

Not done, and the working set goes into conflict state. There is no
dialog. The form the user was editing is the resolution UI:

```tsx
const set = useWorkingSet(ws)     // dirty, conflicts, view, setView, merge()
const merge = useMerge(bar)       // field API for one entity, one subscription

// the field list can come from the schema, from a config, from anywhere --
// nothing below is written per field
for (const name of fieldNames)
{
    const f = merge.field(name)   // a plain call, not a hook

    <input
        className={ f.className }
        value={ bar[name] }
        onChange={ e => bar[name] = e.target.value }
    />
    { f.status === "conflict" && (
        <>
            <button onClick={ () => f.resolve("mine") }>{ f.mine }</button>
            <button onClick={ () => f.resolve("stored") }>{ f.stored }</button>
        </>
    ) }
}
```

**One hook per entity, not per field.** A hook cannot be called from a
loop over a field list, so a `useMergeField(bar, "name")` would only work
in a form whose every input is written out by hand -- which is the one
kind of form that needs the least help. `useMerge()` is called once and
returns an accessor: `field(name)` is an ordinary function that a
generic renderer calls as often as it likes, in a loop, in a callback, in
a child component it passed the accessor to. One subscription per entity
on screen rather than one per input, as well.

It takes the draft and no working set, because a draft knows the set that
created it. Passing a row that is not a draft is an error rather than a
silent no-op -- it is the mistake that would otherwise show up as a form
that never marks anything.

Beside `field(name)` the accessor answers about the entity as a whole --
`changedFields()`, `conflictedFields()`, `resolvedFields()`,
`remoteChangedFields()` -- so a banner, a tab marker or a "next conflict" button
needs no field list of its own. `merge.of(otherEntity)` returns the
accessor for another entity without a second hook, which is what a form
editing a `Bar` and its `BarLink` rows in one place needs.

**A field's status is derived, not remembered.** What is stored, what the
user changed and what they decided are three maps on the entity, and
`unchanged` / `changed` / `conflict` / `resolved` / `remoteChanged`
falls out of which of them the field is in. Nothing keeps the merge
response around, which is what makes the push case the same case: a
message saying a field changed produces the same status a conflict does,
through the same entry point.

**That entry point is `ws.storedState()`** -- the row, the version it
stands at now, and the fields that changed with their values. `merge()`
calls it for every conflict that came back and is today's only caller.
See "When push arrives", where this is the seam that was left for it.

**The view flag** is `"mine" | "stored" | "merged"` and it changes what a
draft read returns. In `"stored"` the whole form shows what is in the
database, in `"mine"` the user's own edits, in `"merged"` -- the default
-- their edits with the other write's untouched fields folded in. A form
that knows nothing about merging renders all three correctly, because the
proxy is what decides. That is the payoff for proxies over plain copies,
and it is the reason this design does not need a second form.

**The classes** come from a small set in `qlive.css`, which is already
shipped as its own artifact:

- `qlive-changed` -- the user changed this field
- `qlive-conflict` -- both changed it, and their value is the one
  standing
- `qlive-conflict-resolved` -- they looked at it and chose
- `qlive-remote-changed` -- it changed under them and they had no edit
  of their own, so the stored value was taken

The application puts `className={ field.className }` on its input and
styles it, or overrides the stylesheet. What the framework will not do is
render the input.

**Resolving.** `field.resolve("mine" | "stored")` records a choice, which
is only ever a correction: every conflict already stands resolved as
`mine` the moment it comes back, because the person present typed that
value on purpose and the person who did not is not here to argue. Nothing
is undecided, nothing blocks, and `ws.merge()` can be called again
immediately -- it re-writes with the stored version as the base, which is
Automaton's apply-and-remerge loop without the modal in front of it.

`field.resolveWith(value)` is the third case, a value neither side had.
It is a second call rather than a third argument to the first because the
two choices are strings, and a field whose value is a string could not
tell `resolve("stored")` from somebody meaning to store the word.

**Choosing `stored` holds the user's value back rather than dropping
it.** The merge stops sending that field and the form shows the stored
one, but what they typed is still there, so changing their mind is one
call and not a retype. Typing over a clashing field is something else
again -- a new value rather than a choice between the two that clashed --
and the field goes back to being one nobody has decided about.

**What "no change" means moves with the database.** A field the user
types back to the value the row was read with is no change, as before;
once somebody else has written that field, it is their value the
comparison is against. Otherwise a user who deliberately typed the old
value back would have their change dropped and the other write left
standing.

**The second save is the acknowledgment, and it is the user's.** The
working set never re-sends by itself. Defaulting to `mine` *and*
retrying automatically would be a silent clobber with extra steps: the
marks would flash past and the other write would be gone. The user gets
their values back with the clashes marked, and saving again is them
saying they looked.

### Leaving the page

A working set holding changes is unsaved work, and a working set holding
changes *plus* conflict decisions is unsaved work somebody has already
paid for twice. Neither may leave the page quietly, so a dirty working
set installs a `beforeunload` handler and the browser asks.

**The listener is installed while dirty and removed when clean**, rather
than registered once for the working set's life. Two reasons, and the
second is the load-bearing one:

- A page that warns when there is nothing to lose trains the user to
  dismiss the dialog without reading it, which is how the warning stops
  working on the day it matters.
- A registered `beforeunload` listener disqualifies the page from the
  back/forward cache in both Chrome and Firefox. Leaving one attached
  permanently makes every ordinary back-button navigation in the whole
  application slower, in exchange for a guard that is only meaningful
  for the minutes somebody is editing.

So the working set adds it on the transition to dirty and removes it on
the transition back -- a successful `merge()`, an `undo()`, a `clear()`.
Both transitions already exist, because the store notifies on them.

**No custom message.** Every current browser shows its own text and
ignores whatever the handler supplies; the handler's whole job is to
`preventDefault()` and set `returnValue`. The framework will not offer a
message option that browsers silently drop -- an application that wants
its own wording has to ask before it navigates, which is a different
mechanism.

**On by default, off by construction.** `new WorkingSet({ guardUnload:
false })` for the application that has its own idea about this. There is
no per-call escape hatch, because the normal way to leave is to stop
being dirty: save, undo, or discard.

**`beforeunload` only covers true browser navigation** -- a reload, a
typed URL, a closed tab. It does not fire for a `pushState()`, so the
moment QLive grows a `Link` component the guard has a hole in it exactly
where the application's own navigation goes.

So the dirty state is registered as a *navigation guard*, and there are
two consumers of the same registration:

- the `beforeunload` handler, for leaving the document
- `navigate()` -- what a `Link` calls -- for leaving the view

A guard says whether it is safe to leave and why. `navigate()` asks all
of them before it touches the history, and if one objects it asks the
user through `config().confirmNavigation`, which an application replaces
in the `startup()` init hook the way it replaces `errorView`. The default
is a `window.confirm()` with an i18n'd message, so an application that
configures nothing still gets a guard instead of a navigation that
silently does not happen.

The router itself is not this design's to settle, and a `Link` is more
than a guard: a client-side navigation has to `pushState`, load the
view's chunk, handle `popstate` for the back button, and re-resolve the
injections for the new path. That last part already exists on both ends
-- `BootstrapService.provideInjectionData(path)` behind `/api/update`,
which runs the `useInjection()` queries the path's view declares and
returns the injections alone, and `initData()` on the client, which
replaces them wholesale. Neither was written for a router and both are
what one needs, since the schema and the meta data do not change between
two views of the same application.

What belongs here is the seam: one predicate, two consumers, registered
and unregistered on the same two transitions as the unload listener.

**`dispose()` exists** for the working set that is thrown away while
dirty, which would otherwise leave a listener warning about changes
nothing can reach any more. Today that cannot happen -- the page load
that would discard the working set is the one being guarded -- so it is
there for the router, and the hooks deliberately do not call it on
unmount: a working set may well outlive the view that rendered it.


### Parking a change set

It is Friday evening, the merge came back with conflicts, and the only
person who knows what the other value was supposed to mean left hours
ago. Resolving now means guessing. Discarding means retyping everything
on Monday. Both are bad, and the second user is again the one paying.

So there is a third option: park it.

```ts
await ws.park()      // stash() + clear(): the work is safe, the guard goes quiet
```

The stash holds what the working set is, not what the form looked like:
per entity its type, id, **base version**, base snapshot and change map,
plus the deletions and any resolution decisions already made. Values are
serialized through `convertToServer()`, which is what the merge would
have done with them anyway -- a `Temporal.Instant` does not survive
`JSON.stringify` and the conversion for that already exists.

**The stashed base version is what gets restored**, not the version of
whatever the row looks like on Monday. This is the whole point and it is
easy to get backwards. A restored working set registers against freshly
queried rows -- the form has to render current data -- but if it adopted
those rows' current version, the merge would succeed silently and the
other person's Friday afternoon would vanish without anyone seeing a
conflict. Keeping Friday's base version means Monday's merge detects
exactly what changed in between, which is the conversation the user
parked the work in order to have.

**Restoring needs no new server machinery.** A base version three days
old, a row that changed twice since, a row that was deleted -- the merge
already answers all of those, because they are the same questions it
answers for a browser tab that sat open over the weekend.

**A stash is not named, it is described.** Asking the application to
invent a key would be asking it to solve identity and findability at
once, and it needs neither: a stash gets a generated id, and everything a
user needs in order to recognize it is already in the thing being stashed.

```
Bar "Bar #2" and 2 more        parked Fri 19:47
Baz "Sprocket"                 parked Mon 09:12
```

The date comes free. The rest is the entity types and their count, and a
label per entity from the type's `nameFields` -- the meta domainql's
`NameFieldProvider` already writes, which qlive-test already populates
with `.configureNameField("name")`. It is the framework's existing answer
to "show an instance of this type to a human", and this is the second
place that answer is worth having. A type with no name fields falls back
to its id, which is worse to read and still identifies the row.

**Finding it again is the route's job.** A stash records the route it was
taken on, so `WorkingSet.stashesFor(route)` is what a view asks in order
to offer "you parked work on this page", and `WorkingSet.stashes()` is
what a "parked work" page lists. Derived, again, rather than declared:
the route is where the user was and where they will look.

Descriptors come back without the change sets attached -- a list of ten
should not deserialise ten working sets to render ten labels -- and
`resume(id)` is what reads one for real. `discard(id)` throws it away.

**A failed stash must not look like a successful one.** `localStorage`
throws `QuotaExceededError`, and the moment it does is the moment the
user is closing the laptop believing their work is safe. `park()` rejects
rather than resolving, the working set stays dirty, and the unload guard
stays armed. The cap -- twenty stashes, oldest dropped -- exists so that
it does not come to that, and the listing is how a user notices before it
does.

**Where it goes is pluggable.** `localStorage` by default, behind a small
`WorkingSetStore` interface -- `save`, `load`, `list`, `remove` -- so an
application can put it in IndexedDB, or on the server where it would
survive a different machine on Monday morning. Same shape as the
converter registry: a default that works, replaceable in one call.

**Domain data at rest in the browser is a real cost.** A stash is
unencrypted, it outlives the session, and on a shared machine it outlives
the user. That is why nothing is ever stashed unless `park()` is called
-- the application decides whether to offer the button at all -- and why
`clearStashes()` exists to be called on logout. An
application handling anything sensitive should point the store at the
server instead of taking the default.

## When push arrives

Websockets are not in this design. Two things about it are shaped so that
they will be additive rather than a rewrite, and they are worth naming
while the reasons are fresh.

**The version record is already the notification.** A successful merge
ends by inserting a batch of `EntityVersion` rows, each carrying the
entity type, the entity id, the new version and the `field_mask` of what
that write touched. That is exactly the payload a subscriber needs, and
it needs nothing else: the mask says which fields changed, so a client
can mark those fields precisely without re-querying the row to find out.
The event is not something the push design would have to invent -- it is
the
row the merge already writes, and step E of the pipeline is where it
would be handed to a broker instead of only to the version holders.

**Stored state needs one entry point, not a merge-specific one.** A
field's status is a function of three things: the base it was registered
with, the change the user made, and the stored value. Today the stored
value only ever becomes known as part of a failed merge, which makes it
tempting to model the whole conflict state as "what came back from
`merge()`". That would be the mistake. The store takes "the stored state
of this entity changed, here are the fields and their values" as an
input, and the merge response is merely today's only caller. A push
message is then the second caller and the status computation does not
change.

With those two in place, the vocabulary this design already has starts
answering questions it was not built for:

- `qlive-remote-changed` stops being something a user learns about at
  save time
  and becomes something they see while they are still typing -- the
  same class, the same accessor, the same "no opinion, take it" rule
  folding in a field they never touched.
- A conflict can be marked *before* the save rather than after it. The
  fields both sides changed are known the moment the other write lands,
  and marking them then is the difference between "choose one" and
  "maybe go ask them, they are still online".
- Parking a change set becomes the rarer case it should be, because the
  Friday evening surprise turns into a Friday afternoon notification.

What the status flags do **not** cover is presence -- "somebody else has
this row open" -- which is a genuinely new concept rather than an earlier
delivery of an existing one, and it is the part of a push design that
will need its own thinking.

### Subscribing with a condition

Automaton's pubsub subscribes a connection to a topic with a FilterDSL
condition, evaluated server-side against each outgoing payload. That
model and the version record fit together better than either was designed
for, because the record is already a flat object whose fields a condition
can name:

```
entityType eq "Bar"
  and entityId in [ ...the ids on screen ]
  and fieldMask bitAnd <mask of the fields this form shows> ne 0
  and ownerId ne <me>
```

Three things fall out of that and none of them need new machinery.
`bitAnd` is already on the operator whitelist, so **field-level**
subscription is expressible today -- a form asks to hear about the fields
it actually renders and stays quiet for the rest. `ownerId` is on the
record, so **not hearing your own writes** is a clause rather than a
special case in the broker. And the subscription is per connection, so
what a client is told is decided where the data is, not by shipping
everything and filtering in the browser.

**The piece QLive is missing is an in-memory evaluator.** Its condition
sub-service compiles a `CNode` to jOOQ; filtering a message means
evaluating the same node against a Java object instead. Automaton has
that as `runtime/filter` -- a `JavaFilterTransformer` and a class per
operator -- and it is the substantial half of adopting the pubsub, not
the topics. The two backends share the parsed node model and the operator
whitelist, which is the part worth keeping: one gate, two evaluators.

**They will not agree on everything**, and that should be admitted rather
than discovered. `contains` in SQL is the database's collation and
`contains` in Java is `String.contains`; null comparisons differ; case
folding differs by locale. Close enough for deciding whether to deliver a
message, not close enough to promise that a condition means exactly the
same thing in both places -- which is an argument for keeping the
in-memory evaluator to message filtering rather than offering it as
general client-side filtering.

## The condition model

The condition model ends up with three implementations. The reason to
care is not that a condition can travel between them -- that is a weak
case, and it barely happens. It is that there is one vocabulary, learned
once, with tooling around it, and that the tooling is worth more than any
individual condition ever is.

| backend | applies to | compiles to | drives |
| --- | --- | --- | --- |
| jOOQ | SQL | a jOOQ `Condition` | query documents -- built |
| Java | a Java object | a predicate tree | pubsub message filtering |
| JS | a JS object | a generated function | filtering in the browser |

The third is already spoken for: `DomainTables` carries filter plumbing
that nothing sets, kept deliberately because the search it will grow is
meant to be a client-side FilterDSL expression rather than a typed
string. So all three have a caller, and two of them do not exist.

### What the standard is worth

More of it exists than it looks. A condition can be built fluently
(`FilterDSL.ts`), parsed (`ConditionParser`), carried as a scalar
(`ConditionType`), transported inside a query config, rendered back to
readable source on *both* sides (`decompileFilter` in TypeScript,
`FilterDSLDecompiler` in Java), and it has a page in the framework-user
documentation. That is a standard with a toolchain, not a syntax.

And because a condition is JSON all the way down, everything that is
usually hard is free: a saved search is a stored condition, an audit log
can record the condition a query ran with, an error message can print the
filter that failed, a support case can be reproduced by pasting one.

**The editor is the piece worth building, and it should be a runtime
one.** Automaton had a node-based editor as a developer tool. What is
worth having here is aimed at the end user instead: a filter builder
inside the running application, whose output is a condition that goes
straight into `update({ condition })`, because a query config already
carries exactly that. Saving one is saving JSON. Showing somebody what
they built is the decompiler that already exists.

**What a runtime editor needs and does not have is the operator
vocabulary as data.** Today it is a gate: `FilterOperators.POSITIVE_LIST`
answers "is this allowed" and `FilterDSL.ts` carries names and arities to
generate its fluent methods. An editor has to *enumerate* -- which
operators exist, how many operands each takes, which ones make sense for
a timestamp as against a string, what to call them in a dropdown. That is
the same list promoted from a whitelist to a description, and it is a
better reason to have one source of truth for it than the duplication is.

**A shared fixture suite is how the standard gets defined** rather than
described. Each backend needs a statement of what correct is regardless
of the other two; writing it once as data -- `(condition, object,
expected)` cases, run by the jOOQ backend against qlive-test's database,
by the Java evaluator as a unit test, and by the JS evaluator under
vitest -- is cheaper than writing it three times as prose, and it is the
artifact a fourth implementation would be written against. Agreement
between them falls out of that; it is not what the exercise is for.

They will not agree everywhere regardless -- SQL `contains` is the
database's collation, Java's is `String.contains`, and null comparison
and case folding differ too. That matters in exactly one place in
practice: a client deciding locally whether a row it has modified still
satisfies the condition its document was queried with. Everywhere else a
condition is evaluated once, in one place, and the divergence is
invisible. Worth pinning down in the fixtures, not worth treating as a
crisis.

### Compile once, apply many

None of the three is an interpreter over the condition tree. Each
*transforms* a condition into something executable, once, and what it
produces is then applied per row or per message. The subscription case is
the one that makes this obvious -- a filter registered once and evaluated
against every published message for as long as the connection lives -- but
it is equally true of a table filtering a page of rows, and the jOOQ
backend already works this way by nature.

Automaton's `JavaFilterTransformer` is halfway there: it builds a tree of
`Filter` objects once instead of re-reading the JSON. What it still pays
on every evaluation is the part worth removing, and it is mostly not the
operators:

- **Field paths.** `field("owner.login")` should become a resolved
  accessor at transform time, not a string split per evaluation. On a
  deep path this dominates the operator cost.
- **Values.** The operands of a condition are constants. A timestamp
  arrives as a string and becomes an instant once, not per message --
  which is what `ConditionCoercing` already does for the SQL side.
- **Types.** A compiled filter is bound to the domain type it filters, so
  every field's type is known before the first evaluation rather than
  discovered at each one.

**How far to take the transform is per platform, and should be.** In the
browser, building a source string and handing it to `new Function` is
genuinely faster than any tree of closures -- one function the engine
compiles and inlines, no dispatch per node -- so that is what the JS
backend should do, with a closure tree as the fallback for pages whose
CSP forbids `unsafe-eval`, which a framework cannot assume away. In Java,
a composed predicate tree is the right stopping point: generating
bytecode would be a lot of machinery for a gain nobody has measured, and
pubsub message volume is not where that would first show up. What matters
is that both sit behind one interface, so the fixture suite tests
behavior rather than strategy and either can be taken further later
without a caller noticing.

**An operator a backend cannot do throws at transform time**, which is
the second reason to separate the two phases. The in-memory backends will
not cover everything jOOQ does -- `likeRegex` against a database's regex
dialect, for one -- and the right moment to say so is when a subscription
is registered or a table's filter is set, naming the operator, with
somebody still looking at it. A filter that quietly evaluates to false
delivers no messages, shows no rows, and is indistinguishable from one
that correctly matched nothing.

This wants its own design document once it is built rather than
discussed; it is written down here because two of the three backends are
what this feature and its follow-up need, and the constraint they share
is easier to see now than after the second one is written.

## Decided details

- **No `*Input` types on the write path.** One generic mutation, values
  as `GenericScalar`.
- **The version field is called `version`.** Automaton made the name
  configurable and nobody ever configured it.
- **New entities get client-generated UUIDs.** A working set has to be
  able to wire up references between new objects before the server sees
  any of them.
- **`_type` is dropped from the generated types.** What an object is
  comes from the schema, which the client has anyway.
- **Unversioned types are writable through the working set**, without
  conflict detection. Adding a `version` column is how a type opts in.
- **Detection and auto-merge are automatic, resolution is declared.**
  The first two are always correct; the third is a UI decision.
- **A merge is all or nothing.** One transaction, rollback on conflict.
- **Conflicts are data.** The framework ships no dialog, and the hooks
  that feed a form are the supported way to resolve one.
- **A conflict defaults to the present user's value and never blocks
  the save.** They are here and they meant it; the other party is not
  and cannot be asked. See "Who pays".
- **A retry is always a user action.** The working set does not re-send
  on its own once conflicts have come back.
- **A dirty working set guards the unload**, with the listener attached
  only while it is dirty, and registers the same predicate as a
  navigation guard for in-app navigation to consult.
- **The version record lifetime spans a weekend.** 7 days, not
  Automaton's 48 hours: Friday evening to Monday morning is 72, and
  parking a change set over exactly that gap is a feature.
  `app_field_layout` is what makes a longer lifetime safe.
- **A field layout is stored, not just hashed.** A hash alone detects
  that the mask moved; the list lets us move it back, so a deployment
  costs no accuracy instead of degrading every older mask to "assume
  everything changed". Remapping happens on read, so there is no
  migration to run, nothing to lock, and mixed layouts in one table are
  legal.
- **A stash keeps the base version it was taken with.** Adopting the
  version of the freshly queried row would turn a parked conflict into a
  silent clobber three days later.
- **Stashes are listed, not named.** An id, a date, and a description
  derived from the entities and their `nameFields`. Any number of them,
  found again by route or from a list.
- **`park()` fails loudly or not at all.** A stash that did not get
  written leaves the working set dirty and the guard armed.
- **"The stored state changed" is an input to the store**, not a shape
  the merge response happens to have. `ws.storedState()`, called by
  `merge()` today and by a push message next. See "When push arrives".
- **A field's status is derived from the three maps**, not recorded when
  a merge comes back. That is what makes the push case the same case.
- **A decision holds the other value back rather than dropping it.**
  Choosing `stored` stops the field being written and keeps what the user
  typed, so changing their mind costs one call.
- **`resolve()` and `resolveWith()` are two calls.** The choices are
  strings and so are plenty of field values.
- **A versioned row read without its version fails at the write of one of
  its own fields**, not at `edit()` and not at the registration. A
  version is the base for the row's own columns and nothing else. See
  build order item 9.
- **A conflict about a link row is reported against the link array.**
  The row was synthesised by the diff and is in no form; the array is
  what the user edited. See "Many-to-many".
- **A resolution holds a link array back the way it holds a scalar
  back.** The link diff runs over the pending changes, not over every
  change, so `resolve("stored")` on an array means what it says.
- **A unique constraint refusal is a conflict; every other constraint
  refusal is an error.** A row that does not exist yet has no version, so
  the constraint is the only lock it has. See "B -- write".
- **The view flag lives on the working set and every draft read goes
  through it**, so switching it re-renders a form that knows nothing
  about merging against other values.
- **`QueryDocument` needs the same entry point**, and does not have one:
  `rows`, `config` and `rowCount` are public and mutable while `notify()`
  is private, so nothing outside the document can move its state and have
  React hear about it. Whatever the working set gets called, the query
  document grows the same seam -- it is the other store a push message
  lands in.

## Build order

Each step is useful on its own and testable where it lands. qlive-test's
tests run against the application's real Postgres (`@SpringBootTest`), so
everything on the server side is covered there; qlive's own tests build a
`DomainQL` with a null `DSLContext` and stay useful for the meta and the
type analysis.

1. **Schema.** `app_version` plus `version` columns on `bar`, `baz`,
   `bar_link` and `foo` in qlive-test, regenerated jOOQ classes and
   backup. `qux` and `foo_type` stay unversioned on purpose, so the
   unversioned path has a subject.
2. **Meta.** `MergeMeta` / `MergeMetadataProvider`, the client-side
   derivation of versioned types and link relations, the
   `DomainQLTypeMetaProps` addendum. No behavior yet.
3. **Server merge, scalars only.** Model types, `MergeService` and
   `DefaultMergeService`, `MergeLogic` as the framework's `@GraphQLLogic`
   bean, optimistic locking, deletions, conflicts as data. No masks yet:
   any concurrent change is a conflict on every field.

   `MergeLogic` and every other framework-side bean is declared
   explicitly in `QLiveConfiguration`. An application's component scan
   does not reach `com.dataciders.qlive.*` -- qlive-test's is narrowed to
   its own logic and service packages -- so `@GraphQLLogic` being
   meta-annotated `@Component` picks up nothing. Declared, it is found by
   the `getBeansWithAnnotation` / `getBeansOfType` calls the application's
   own DomainQL configuration already makes, which is all it takes.
4. **Field masks and auto-merge.** `EntityVersion`, `VersionHolder`, the
   chain walk, the cleanup task. This is where the pseudo-conflict case
   starts merging silently.

   `app_field_layout` and the read-time remapping land here as well,
   since this is where a mask first exists. The `field_layout` column is
   already in the schema from step 1 and nothing writes it before this.
5. **Client WorkingSet.** Registration, proxy drafts, dirty state,
   scalar changes, `merge()`, refresh on success.
6. **Many-to-many.** Link diffing against the base, the `Bar`/`BarLink`/
   `Baz` triple in qlive-test as the subject.
7. **In-view resolution.** View flag, `useWorkingSet` / `useMerge` and
   the field accessor, the classes, the apply-and-remerge loop.
8. **An edit view in qlive-test** exercising the whole thing, which is
   also the template an application copies from.
9. **`register()` and a row that selected no `version`.** Settled: it
   refuses neither there nor only warns. A warning in a framework is
   noise nobody reads, and refusing at registration refuses a query
   because of a table nobody is editing. The row is registered, carries
   the sentence, and throws it at the write of one of its own fields, at
   `delete()`, or at a link diff that would delete it. Not at `edit()`:
   the version is the base for the row's own columns, and a link-only
   edit writes none of them. See "A store, like a query document".
10. **Cascading deletes** are not the framework's. The user either
    deletes everything right or they put a cascade on the foreign key,
    which the database has and does better.


## Open items

- **Whether the framework ships the listing.** The descriptors are layer
  2 and the application renders them, consistent with everything else
  here. But a "parked work" list is a page rather than a modal in a
  flow, and it is the same kind of thing `DomainTables` already is --
  so this is a weaker "no" than the one about the conflict dialog.
- **Auto-stashing on unload.** The unload handler could write the stash
  itself, the way a mail client keeps a draft, and turn the scary dialog
  into a safe exit. Against it: ghost data nobody asked for, on a machine
  that may not be theirs, restored weeks later by a user who has
  forgotten what it was. Parking stays an explicit act until there is a
  reason to change that.
- **A `Link` component and in-app navigation.** Its own design, and
  further along than it looks -- `/api/update` and `initData()` are the
  data half and both exist. The working set is the reason it needs a
  guard at all, which is how it got named here.
- **Validation.** Automaton carried `ValidationRules` through the same
  path. Out of scope; the application's own mutation-time concern for
  now.
- **Group fields.** Automaton had a notion of fields that change
  together, for composite and JSONB values. Whether QLive needs it
  depends on whether a JSONB column is edited field-wise, which nothing
  does yet.
- **Push, and it is the important one.** Everything in this document is
  damage control after the fact; telling a user that the row under their
  form just changed, while they can still do something cheap about it,
  is worth more than any amount of conflict UI. Its own design, and the
  one to write next -- see "When push arrives" for the two seams this
  one leaves it.
- **Whether the accessor travels by prop or by context.** Above it is a
  value the form passes down. A deep generic field tree would rather find
  it in a React context, which is what Automaton had in domainql-form's
  FormContext -- but QLive ships no form library, and a `MergeScope`
  provider is either the first piece of one or a context with a single
  consumer. Deliberately left open, and step 8 is the evidence so far: the
  edit view passes the accessor one level, to the component rendering the
  association editor, and wants nothing. That is one handwritten form of
  three fields, so it argues for waiting rather than against a context --
  the tree that would want one is the one a form library builds.
