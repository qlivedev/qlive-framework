# Module templating (design sketch)

Status: sketched, not started. Written 2026-09-10.

How a module brings its own domain types and its own views. The
distribution and pairing of modules is `module-distribution.md`; this is
the mechanism the domain/UI species of module described there needs, and
nothing in it applies to a behavior module.

## Problem

Everything load-bearing in QLive is generated ahead of the application's
build: track-usage, query result types, jOOQ classes, `types.d.ts`. None
of it looks inside `node_modules`, and none of it can. So a module that
brings a table or a screen has nothing to link against -- there is no
runtime seam to open, because the artifacts it would need are produced
per application before any runtime exists.

## The module boundary is at install time

Materialise the module's content into the application, and every
downstream mechanism runs on its own with no framework change at all.

A table lands in the application's database, so jOOQ generates it into
the application's package, `objectTypes(Public.PUBLIC)` picks it up,
DomainQL makes it a GraphQL type, `generate-ts` types it, and the client
finds it in `config().typesByName`.

A view lands under `src/app/`, so it is application code: track-usage
sees it, `import.meta.glob` catches it, `useInjection` is legal there,
the route exists, and `VIEW_ROOT` is satisfied.

This is not a workaround for the framework's shape, it is that shape.
The alternative -- teaching every generator to look inside packages --
means reimplementing the babel analysis, the jOOQ generation and the type
emission against a second source of truth.

## Type descriptors

A module describes the types it needs as JSON. The installer reads that
and emits DDL into the application's tree.

Two kinds of entry, and conflating them would be the first mistake:

- **Owned types.** The module brings a table nobody else touches
  (`app_version`). The module reaches it by configured name at runtime,
  exactly as `AppUserDetailsService` and `DefaultPersistentTokenRepository`
  already reach theirs -- an installed table does not give the module a
  typed jOOQ handle to it, because those classes are generated into the
  application's package after the module was compiled.
- **Required traits.** The module needs *existing* application types to
  gain something: merge needs `version varchar(36)` on every
  participating type. This is not rename-or-create, it is "which of your
  types take part, and let me add a column to them" -- which is the
  question the merge design currently answers by telling people to add
  the column by hand.

"Assign to an existing type" is designed as additive and checkable --
*these fields must exist on a type you nominate* -- rather than
substitution. Structural type matching is a rabbit hole; field
requirements verify in one pass, and `validate-modules` can re-check them
on every build.

The descriptor is the design's main format-invention risk: it is
effectively a type language, and the pairing design's stated virtue is
one new format in the whole thing. Keeping it inside DomainQL's and
jOOQ's existing vocabulary is worth real effort.

### Tested against merge

Written as a paper exercise while building the merge schema
(`working-set-merge.md` step 1), which is the descriptor's intended
first customer: one owned type (`app_version`) and one required trait
(the `version` column on the types the application nominates). What the
exercise says about the format:

- **Columns should be jOOQ `SQLDataType` names, not SQL.** `VARCHAR(36)`
  and `DECIMAL_INTEGER(39)` are dialect-neutral, they are the vocabulary
  the generated `Table<?>` already speaks, and `DSL.createTable()`
  renders the DDL from them for whichever dialect the application runs.
  That answers the format-invention risk with an existing vocabulary and
  gets `validate-modules` for free -- comparing a descriptor column
  against the jOOQ field of the same name is a type equality check.
- **A third kind of entry is needed: a type the module references but
  does not change.** `app_version.owner_id` points at `app_user`, which
  merge neither owns nor adds anything to. That is a required trait with
  an empty add-list and a non-empty require-list, which means a trait has
  to mean "a shape a nominated type must have, and may be given" rather
  than "fields the module adds". Cheap generalisation, but it has to be
  made deliberately -- and in QLive's case there is a second reading,
  since `app_user` belongs to the core module and this could be a module
  dependency instead.
- **The manifest is load bearing for merge specifically.** Elsewhere
  recording which types answered a trait is an optimisation for updates.
  Here it is the only record of intent: "this type has a version column"
  *is* what participation means, so `validate-modules` cannot re-derive
  the answer, and a type that lost its column is indistinguishable from
  one that opted out unless the manifest says otherwise.
- **Backfill is not expressible, and adding a column usually needs it.**
  `ALTER TABLE bar ADD COLUMN version varchar(36)` is mechanical;
  `UPDATE bar SET version = gen_random_uuid()` is not DDL and has no slot
  in the descriptor. Skipping it leaves every existing row unmergeable,
  silently. A column `DEFAULT` is the wrong tool -- Postgres would
  backfill, but the default would also apply to every later insert, and
  the version is the merge's to assign. So the slot has to be a backfill
  expression distinct from a default, which reintroduces dialect-specific
  SQL into a format otherwise free of it.
- **Emitting sound DDL is not the same as producing a sound schema.**
  `numeric(39,0)` maps to `java.math.BigInteger`, which QLive did not
  have a scalar registered for; DomainQL falls back to a type reference
  by simple name and the introspection then reports the field as an
  object type nothing answers to. Fixed in the framework, but a module
  bringing a column type the application's domain cannot name would hit
  it again, and only `validate-modules` is positioned to notice.
- **Indexes have no slot.** The merge reads `app_version` by `prev` and
  expires it by `created`; the design's DDL block names neither. Trivial
  to add to the format, easy to leave out of it.

The encouraging half is that the two-kind split held. Everything merge
needs of the application's own types is *additive and checkable*,
because the design deliberately derives participation from the schema
instead of declaring it -- the descriptor stays expressible precisely
because the feature declares as little as it can.

## Views are file copies

A view template is a `.tsx` file. The installer copies a directory tree
under `src/app/`, in a folder the module owns. The single question it has
to ask is where to root that folder; the router derives the routes from
the path.

**There is no JSON view model.** Automaton had one, and it cannot work
here: track-usage runs babel over `.ts`/`.tsx` and never visits a JSON
file, so a view expressed as JSON could declare neither a `useInjection`
nor a `GraphQLQuery` -- which is the only reason the vendored file has to
be application-local in the first place. It would defeat its own purpose,
and cost a component registry, a renderer, prop serialization, a second
analyser and TypeScript's knowledge of any of it. Automaton paid that for
a graphical editor. Absent that ambition it buys nothing.

### Shell and body

A vendored view is a thin shell over a component the module keeps in its
npm half:

```tsx
// src/app/merge/Stashes.tsx -- vendored at install, the application owns it
import { StashList } from "@qlive/merge/views"
import AppLayout from "../../component/AppLayout"

export const Q_Stashes = new GraphQLQuery<...>(`query stashes { ... }`)

export default function Stashes()
{
    const data = useInjection(Q_Stashes)
    return <AppLayout><StashList data={ data } /></AppLayout>
}
```

The split is not a compromise, it is dictated: **what must be
application-local is exactly what static analysis has to see.** The
shell carries the `GraphQLQuery` declaration (so `generate-query-types`
types it against the application's schema), the `useInjection` call (so
the server can resolve it, and so the views-only rule holds), the route
position, and the application's own layout and navigation. Everything
else belongs in the package, where it keeps updating with version bumps.

That last point is a design goal rather than an accident: the more the
module keeps in its npm half, the cleaner every future update is.

QLive has no layout or chrome concept -- each view renders itself -- so
navigation can only be attached inside the shell, which is why the shell
must be editable and why it imports application paths. A layout mechanism
would make shells thinner and this whole problem smaller.

## Updating

The two halves have different semantics and are deliberately not
unified. Text has no state; a database does.

**Views are a three-way merge.** The installer records a hash of every
file it wrote, so an update has all three sides: the previous template as
base, the new template as theirs, the file on disk as mine. Unchanged
files are overwritten silently, edited files merge, and only genuinely
overlapping edits surface. A file the application deleted stays deleted.
The module owning its own folder is what makes this tractable -- nothing
of the application's is interleaved in there.

The shell is where the application is *expected* to edit, so "unchanged
since install" is the exception rather than the rule. A shell that did
not change between versions merges cleanly no matter how heavily it was
edited, which is the second reason to keep shells small.

**DDL only goes forward.** There is no reverting a migration the way a
file is reverted, so the installer **emits DDL and does not apply it**.
It writes a migration into the application's tree and stops; the
application applies it however it already applies schema changes, then
reruns jOOQ codegen and `pnpm generate`. That keeps the tool out of
owning somebody's database, and puts the SQL in their git history where
they can read it before it runs.

Change kinds sort by whether that is mechanical:

- **Additive, module-owned** (new table, new nullable column) --
  generated, safe, and the large majority.
- **Additive, required-trait** (a second column on every participating
  type) -- also generated, and it works only because the manifest records
  which types participate.
- **Narrowing, renaming, dropping, retyping** -- not mechanical, and may
  break application code that referenced the column. The installer
  refuses to invent these and places the module author's own handwritten
  migration unmodified, with a warning.

`validate-modules` is what makes emit-don't-apply safe: the descriptor
states what the module requires of the schema, the plugin compares it
against the resolved database at build time, and a forgotten migration or
a dropped column fails the build with a specific message. That is the
same bidirectional shape the plugin already has for npm/Maven, applied to
a second dimension.

## Not in a first version

- **Renaming a module's types.** A rename propagates through DDL, the
  jOOQ class name, the GraphQL type name, the generated TS name, the
  query text in the shell, the Java config and any metadata keyed by type
  name, and it needs a durable manifest to stay consistent across
  re-runs. Create as-is plus a collision check that refuses cleanly is
  enough until a real collision turns up, and every rename is a permanent
  translation layer in somebody's head. Reserve `${...}` in template
  syntax from the start with an identity map, and the door stays open
  without the machinery being built.
- **Substituting an existing type for a module's own.** See "additive and
  checkable" above.

## Open items

- Where the install manifest lives and what exactly it records. The
  minimum is: which types participate in each required trait, and the
  hashes of the files written.
- Whether the descriptor emits plain SQL or goes through a migration tool
  the application adopts. Emitting SQL fits the current `.backup` reality;
  Flyway would fit a larger application better.
- A template carrying substitutions is not typecheckable in the module's
  own repository, since it names types that only exist downstream. The
  answer is presumably that a module tests its templates by installing
  them into qlive-test and typechecking there, which is the "structural
  template for real applications" role qlive-test already has.
- `qlive mod add` does not exist, and this design makes it non-atomic: it
  leaves a tree needing DDL applied, then jOOQ regenerated, then
  `pnpm generate`. Whether it drives that sequence or prints it as a
  checklist is undecided.
