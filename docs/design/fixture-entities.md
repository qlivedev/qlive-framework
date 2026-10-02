# Fixture entities: storing each object once

Status: shelved 2026-10-02. Written down while it was fresh; nothing is
built, and nothing else waits on it. Extends [fixture-mode.md](fixture-mode.md)
and [fixture-scope.md](fixture-scope.md).

## Problem

A fixture holds query results the way the server sent them. Every
document repeats every object it reaches: the same `Foo` in
`grid/Sorting` and `grid/Filters`, the same owner under each of its
Foos. With demos all over qlive-doc, the same 23 Foos would be stored
a dozen times over.

The bytes are not the reason to change that. Measured on the two
fixtures qlive-doc had on 2026-10-02:

| part | raw | brotli |
|---|---|---|
| config | 48 KB | 2.4 KB |
| data, one fixture | 9 KB | 1.9 KB |
| data, both fixtures in one string | 18 KB | 1.9 KB |

Brotli removes repetition across fixtures, as long as it sees them
together. The config was the real weight, and qlive-doc now stores it
once (`src/demo/config.json`). Normalizing would save at most the 1.9 KB
each extra fixture file costs -- and only by putting all entities in one
file every page fetches.

What normalizing would buy is something else:

1. **One dataset.** Foo #3 is the same Foo in every demo. Fixtures
   recorded at different times can't drift apart: a recording whose
   Foo #3 differs from the stored one is a conflict at the moment it
   is added, not a puzzling difference between two pages later.
2. **Fixtures from other sources.** A dataset written by hand or
   generated -- entities plus the documents over them -- is a natural
   shape to write. A recorded query result isn't.
3. **Answering queries from data.** With entities rather than results,
   the page can answer a query nobody recorded: walk the query's
   selection over the entity store. That turns a fixture from a
   recording of one view into data any view can run on.

The third is the one that changes what a fixture is, and the reason
this is a design rather than a packaging step.

## Shape

```json
{
  "description": "...",
  "entities": {
    "Foo": {
      "3f7c...01": {"id": "3f7c...01", "name": "Foo #1", "num": 12345, "owner": {"$ref": "AppUser:3f7c...ff"}}
    },
    "AppUser": {
      "3f7c...ff": {"id": "3f7c...ff", "login": "admin"}
    }
  },
  "documents": {
    "grid/sorting/Q_FooList": {
      "type": "FooDocument",
      "config": {"sortFields": ["name"], "pageSize": 5, "offset": 0, "condition": null},
      "rows": [{"$ref": "Foo:3f7c...01"}]
    }
  }
}
```

- **Identity** is the type plus `id`. The type is not stored with the
  object: the schema gives it along the selection path, starting from
  the query field's type. Only an interface or union needs
  `__typename`, and QLive's schemas have neither today.
- **Fields** are the union of what every recorded query selected. An
  entity in one fixture may have `owner`, the same entity from another
  only `name`; stored, it has both.
- **Objects without an id** -- embedded values, aggregates -- stay
  inline where they are.
- **Documents** keep what is theirs: config, rowCount, and the order of
  their rows as a list of references.

## Recording

The recorder already queries every document again for all its rows.
Normalizing hooks into that:

- **Field names, not aliases.** A result is keyed by alias; the entity
  stores the field's name. `parseQuery()` has both sides of every alias.
- **Arguments.** A field with arguments can't be stored under its name
  alone: `price(currency: "EUR")` and `price(currency: "USD")` are two
  values. Store it under name plus arguments, the way Apollo's cache
  keys `storeFieldName`. `parseQuery()` skips arguments today and would
  have to read them. No QLive query selects such a field yet, so the
  recorder could refuse one until one does.
- **No id selected.** The view's query is the view's; the recorder
  can't change it. But it runs its own query anyway, so it can add `id`
  to every object selection whose type has one, and drop it again when
  answering the view. Where a type has no id field, the object stays
  inline.
- **Conflicts.** Adding a recording whose entity differs from the stored
  one in a field both have is refused, naming the type, id and field,
  in the same spirit as `addFixture()` refusing a different schema.
  Re-recording everything from one database state is the fix.

## Answering

Two levels, the second optional:

1. **Rebuild the recorded documents.** Walk each document's query
   selection over its row references, picking the selected fields and
   following references. This is what a fixture does today, from
   smaller files; nothing else changes for the views.
2. **Answer any query.** Given a query document query and a config,
   take the type's entities as rows, run them through `evaluateQuery()`
   -- condition, sort, page, which the evaluator already does for local
   documents -- and shape the page by the selection. A view could then
   run on a dataset without a recording for its route at all.

Level 2 needs the root query field mapped to its row type, which the
domain meta data has (`genericTypes` ties `FooDocument` to `Foo`), and a
rule for the query's other arguments, which a generic answer can't
know. A query document query takes only its config as a rule, so level
2 could cover exactly those and refuse the rest.

Automaton's `evaluateMemoryQuery` (automaton-js, `src/util/`) answered
queries over in-memory rows the same way and is prior art for level 2,
not a dependency.

## Writes

[fixture-mode.md](fixture-mode.md#direction) rules out writes: faking
merges, versions and conflicts costs a lot and shows little. An entity
store changes the cost side -- a merge becomes an update of entities,
and every document over them could be answered again -- but not the
conflict and version side, which is where the cost was. This design
doesn't reopen that decision; it notes that a store would be where to
start if it ever is.

## Why shelved

- The size argument is gone: the shared config took the weight, brotli
  takes the rest.
- Points 1 and 2 matter once there are many fixtures from many hands.
  qlive-doc has two, recorded by one script from one database.
- Point 3 is a feature of its own, and nothing asks for it yet.

Pick it up when the demos outgrow recording -- a dataset written for the
docs, a demo of a view that can't be recorded, or a view that should run
on data it never saw.
