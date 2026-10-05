# Many-to-many relations

Status: built, steps 1 to 7. Written 2026-10-05, built 2026-10-06 on
`feat/many-to-many`. qlive-test's Bar edit view was checked by hand
against the running backend, adding and removing associations both ways.

## Problem

A many-to-many is two ordinary foreign-key relations out of a link table,
configured one by one:

```java
.configureRelation(BAR_LINK.BAR_ID, SourceField.OBJECT_AND_SCALAR, TargetField.MANY, "bar", "bazLinks")
.configureRelation(BAR_LINK.BAZ_ID, SourceField.OBJECT_AND_SCALAR, TargetField.MANY, "baz", "bazLinks")
```

That yields `Bar.bazLinks: [BarLink]` and `Baz.bazLinks: [BarLink]`. Nothing
in the domain says the two relations belong together. Four things follow
from that.

**Recognition is a guess.** Only the merge needs to know, and it guesses
from the table's shape: `isLinkType()` in `qlive-ts/src/merge/meta.ts`
takes a type with exactly two outgoing relations and nothing but `id`,
`version` and the foreign keys. A link table that carries more says so with
`MergeMetadataProvider.linkType()`. Add a `created` column to `bar_link` and
it stops being a link without anything failing: edits of `bazLinks` then
mean something else.

**Only the merge knows.** The schema, the query planner, codegen and the
docs see two unrelated one-to-many relations. `SchemaAssembler` describes
every one-to-many back-reference as "Many-to-many objects", which is wrong
for all of them that aren't.

**The link row sits in the way.** The working set already identifies a
link by the row it points at, not by the link row's id. For a plain link
the row adds nothing a view wants, yet the view has to go through it:

- reading is `bar { bazLinks { id version baz { id name } } }`;
- deleting an association needs the link row's id (`linkIdOf()` refuses
  without one), so an edit has to select it;
- `qlive-test/frontend/src/app/bar/Edit.tsx` declares `QueriedLink`,
  `EditLink` and `EditRow` and a `bazIdOf()` helper only to deal with a
  link that is sometimes a row and sometimes `{ baz }`.

**Prior art.** Automaton tagged both relations with `ManyToMany`
(`AutomatonRelation.MANY_TO_MANY`) and validated that a tagged link type
had exactly two. Going by the code, the reason was that its server-side
`MergeTypeInfo` took any list whose element type had a relation back and
one elsewhere for a many-to-many, which includes every child table with a
second foreign key. The tag was the explicit declaration that guess
lacked. `RelationBuilder.withMetaTags()` still exists in QLive.

## Direction

### Declared, not recognized

One call declares the many-to-many, naming both foreign keys of the link
table and the field each side gets:

```java
.configureManyToMany(BAR_LINK.BAR_ID, BAR_LINK.BAZ_ID, "bazes", "bars")
```

A null name leaves that side without a field.

A link table without foreign-key constraints is declared the way
`withRelation()` declares any relation without one, by naming the fields:

```java
.withManyToMany(
    new ManyToManyBuilder()
        .withPojoFields(BarLink.class, "barId", Bar.class, "bazId", Baz.class)
        .withFieldNames("bazes", "bars")
)
```

Both forms produce the same declaration, and nothing after it can tell
them apart: fetching, filtering, writing and the writability rule only
need the two columns and the types they point at. The constraint only
adds the database's own guarantee that a link points at an existing row.

Startup fails if the two fields aren't on the same table, or if either
one isn't of the type of the id it points at. Each side references the
other type's `id`, because that is what the working set names rows by.

The declaration ships to the client with the other relation meta, as a
kind of its own rather than as a tag on two relations: for each side the
field, the link type, the two foreign-key fields, the type on the other
side and whether the field is writable (below). Everything that needs to
know reads it from there, the merge included.

The link table stays a type of the schema. It gets no relations of its own
unless the application also configures them, which it does when it wants
to show or edit link rows as rows.

### Through fields

The declaration generates `Bar.bazes: [Baz]!` and `Baz.bars: [Bar]!`, fetched
through the link table. Reading an association is reading the rows:

```graphql
bar { id name bazes { id name } }
```

The query planner already fetches a to-many relation by a query of its own
and stitches it onto the parents. A through field is the same, over
`bar_link` joined to `baz`, stitched by `bar_id` and flattened. A FilterDSL
path through it (`bazes.name`) becomes an EXISTS over the same join, the way
a path through a to-many already does. Sorting through it stays refused.

On the client it is a list field like any other, so codegen, the generated
query types and `evaluate.ts` take it as they find it.

### Writing

A through field is set to the rows the source is to be associated with:

```ts
bar.bazes = [...bar.bazes, baz]
bar.bazes = bar.bazes.filter(b => b.id !== id)
```

The working set diffs the ids against the ones read and sends the
difference as additions and removals of pairs, `(bar, baz)`. The server
resolves the declaration and turns each into an insert or a delete of a
link row, keyed by the pair. Link writes come after the entity changes, as
they do now (phase D of the merge pipeline).

This is set semantics, and it doesn't conflict:

- **Removing an association that is already gone** deletes nothing and is
  not an error.
- **Adding one that already exists** inserts nothing and is not an error.
  That needs a unique constraint on the pair. Without one, the insert has
  to check first, and two concurrent inserts can race. Startup warns where
  jOOQ reports no unique key on the pair. `bar_link` has one
  (`uc_bar_link_bar_baz`).
- **Neither needs a version**, of the source row or the link row. A view
  that only edits associations doesn't select one.

Deleting by pair removes whatever else the link row held. That is what
removing an association means.

Through fields are the only arrays the working set diffs. A row has
columns and nothing else, so every other array in a result comes from a
one-to-many back-reference like `AppUser.foos`. What it says is held by
the child's foreign key. Such an array matters to an edit only as the way
the children reach the working set, when they're the rows being edited.
The working set already refuses a write to the array itself.

A self-referential many-to-many, `follow(follower_id, followee_id)` with
`AppUser.following` and `AppUser.followers`, is two through fields on the
same type. The declaration's order of the foreign keys says which field
diffs into which column, and tests cover both directions.

### Writable or read-only

A through field is writable when the merge can insert a link row from the
two foreign keys alone. Every other column of the link table has to be:

- **`id`**: the merge generates it if the column has no default, as the
  client generates ids for new rows today;
- **`version`**: written like any versioned row's, so the version record
  says who added the association and when. It's never checked, because
  nothing about a link can conflict;
- **anything else**: nullable or defaulted in the database. jOOQ's
  `DataType.nullable()` and `defaulted()` tell.

A link table with a required column of its own, an `Assignment` with
`role NOT NULL`, gets read-only through fields. Writing one fails with a
message naming the column and pointing at the link type. Such a link is an
entity: the application configures the link table's relations and edits
`Assignment` rows as rows.

`id` and `version` are therefore allowed and not required. A plain link
table can be `(bar_id, baz_id)` with a composite primary key.

### What goes away

- the shape guess: `isLinkType()`, `hasOnlyLinkFields()`;
- `MergeMetadataProvider.linkType()` and `MergeMeta.LINK_TYPE`;
- the link-array diff over link rows in `WorkingSet.diffLinks()`, with
  `linkIdOf()` and the `{ baz }` short form. The diff over ids replaces it;
- in qlive-test, `Bar.bazLinks`/`Baz.bazLinks` and the link types and
  helper in `Edit.tsx`;
- the "Many-to-many objects" description on plain back-references.

Nothing is released, so none of this needs a migration path.

## Not chosen

- **No `id` and `version` as the marker for a link.** The shape becomes
  the declaration again. A team that requires surrogate keys, or an audit
  trail on links, would turn every link into an entity without noticing.
- **A tag on the two relations**, as Automaton did. It needs validating in
  pairs, names no fields, and leaves the schema showing link rows.
- **A tighter guess.** Still a guess, with the same silent switch at its
  edge.
- **Client-side sugar over `bazLinks`.** An accessor that hides the link
  row while editing leaves it in queries, results and generated types,
  which is where most of the awkwardness is.

## Decided while building

1. **The wire format** is per source row and field with id lists:
   `LinkChange {type, id, field, added, removed}`, a fourth argument of
   `mergeWorkingSet` between the changes and the deletions. It mirrors
   what the working set holds -- one field of one row -- and the server
   applies it after the changes and before the deletions.
2. **`ManyToManyBuilder`** has `withForeignKeyFields(linkField,
   otherLinkField)`, `withPojoFields(linkPojo, linkField, type,
   otherLinkField, otherType)` and `withFieldNames(fieldName,
   otherFieldName)`. It builds the two halves with `RelationBuilder`, so
   both forms resolve fields the way ordinary relations do. The halves
   stay out of the domain's relations: an application configuring the
   same foreign keys as ordinary relations would otherwise get the link
   columns twice.
3. **Field masks** count through fields like any other field of the
   type, on both ends, since both take every field of the schema type.
4. **qlive-test** dropped the ordinary `bazLinks` relations. The
   template shows the declared way; the framework's own test domain
   keeps both over the same keys.

Ordered links are out of scope. An application that needs order makes
its position column required, and that makes the link an entity.

## Build order

1. Fix the "Many-to-many objects" description on plain back-references
   (independent, can go now).
2. `configureManyToMany()` and `withManyToMany()`, the relation meta and
   the startup checks.
3. Through fields: schema, query plan, stitching, filter paths.
4. The merge: pair additions and removals, inserts that complete `id` and
   `version`, the writability check.
5. The working set: diff through fields by id; remove the shape guess and
   `linkType`.
6. qlive-test: switch Bar and Baz to `bazes`/`bars`, simplify `Edit.tsx`,
   adjust `MergeMetadataTest`, `WorkingSet.test.ts` and
   `QueryDocumentServiceTest`.
7. qlive-doc: the many-to-many section of `define-the-domain.md`, and the
   `linkType` mentions in `enable-merging-for-a-type.md`,
   `explanation/merge-service.md` and `api/merge.md`.
