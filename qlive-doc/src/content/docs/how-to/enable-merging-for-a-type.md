---
title: Enable merging for a type
description: The version column, the tables the merge owns, and what a type declares about conflicts.
sidebar:
  order: 9
---

The merge is how a QLive application writes. What a form does with it is
[Edit rows with a working set](/qlive-framework/how-to/edit-rows-with-a-working-set/);
this page is what has to be true on the server before a row can be edited
that way, and what a type gets to say about it. Why it exists at all is
[MergeService](/qlive-framework/explanation/merge-service/).

## A `version` column makes a type versioned

Add `version varchar(36)` to the table and the type is versioned. That is
the whole rule -- nothing declares it, and nothing has to be registered.

The column holds the id of the `app_version` record describing the change
that produced the row's current state, and it is the optimistic lock: a
write goes out as `WHERE id = ? AND version = <the version it was read
at>`, so a write that matches no row is a write somebody else got in front
of. Nothing is locked, and nothing is paid while nobody else is editing.

A type without the column can still be written through the merge. It
records no version, nothing detects a concurrent change, and the last write
wins -- which is the behavior worth choosing deliberately for a log table
or a counter, and worth avoiding for anything two people edit.

Adding the column is a schema change, so
[regenerate](/qlive-framework/how-to/regenerate-from-the-schema/) the jOOQ
classes, `schema.graphql` and the TypeScript types afterwards.

## The tables the merge owns

Two, by fixed names, reached through plain jOOQ names because the generated
classes live in the application where the framework cannot see them:

```sql
CREATE TABLE public.app_version
(
    id           character varying(36)       NOT NULL,
    field_mask   numeric(39,0)               NOT NULL,
    owner_id     character varying(36)       NOT NULL,
    created      timestamp without time zone NOT NULL,
    entity_type  character varying(100)      NOT NULL,
    entity_id    character varying(36)       NOT NULL,
    prev         character varying(36),
    field_layout character varying(64)       NOT NULL,
    CONSTRAINT pk_app_version PRIMARY KEY (id),
    CONSTRAINT fk_app_version_owner_id FOREIGN KEY (owner_id)
        REFERENCES public.app_user (id)
);

CREATE TABLE public.app_field_layout
(
    id          character varying(64)  NOT NULL,
    entity_type character varying(100) NOT NULL,
    fields      text                   NOT NULL,
    CONSTRAINT pk_app_field_layout PRIMARY KEY (id)
);
```

One `app_version` row per recorded change: which fields it touched, under
which field layout, and the version it was made against. `prev` chains the
records for one row, and walking that chain is what lets two edits that
touched different fields both land instead of one of them failing.

`app_field_layout` holds the ordered field list a mask was written against,
so a mask stays readable across a deployment that added a field.

**`owner_id` references the user table.** The merge records
`AppAuthentication.current().getId()` as the owner of every change, so
writing through it needs an authenticated user whose id is a row there.
`app_user` is what `qlive-test` calls it -- that name is the
application's, unlike the two above.

**Version records expire.** `qlive.merge.versionLifetime` defaults to
`P7D`, swept hourly. Past the lifetime the chain walk can no longer say
what changed since, so a concurrent change becomes a conflict on every
field rather than the silent merge it would have been. That is a cost to
whoever parked their work for longer than the window, never a wrong answer.
The rows themselves are untouched by the sweep.

<!-- TODO: the framework-owned tables (these two, app_user and the
     remember-me table) want one reference page of their own, with the
     `app_user` DDL the auth support expects. Planned in
     docs/design/documentation-gaps.md; link it from here once it exists
     and cut this page's DDL back to a pointer. -->

## What a type declares

Declaring nothing already gets conflict *detection* and auto-merge on every
versioned type, because neither is anybody's decision. What a provider says
is the part that is:

```java
@Bean
public MetadataProvider mergeMetadata()
{
    return MergeMetadataProvider.newProvider()
        .resolveConflicts(Bar.class)
        .ignoreFields(Foo.class, "created")
        .autoMerge(Baz.class, false)
        .linkType(CorgeLink.class);
}
```

| | |
|---|---|
| `resolveConflicts(type)` | A real conflict comes back carrying both values per field, for the form the user is looking at to offer. Without it the write simply fails and names the fields that clashed. |
| `ignoreFields(type, ...)` | Fields whose change is neither recorded nor ever a conflict -- a last-accessed timestamp, a counter, anything two users cannot meaningfully disagree about. |
| `autoMerge(type, false)` | Says a user should see even a change that does *not* clash with theirs before it is folded into their save. True is what happens anyway. |
| `linkType(type)` | Declares a type a link table. Only needed for link tables carrying fields beyond the two foreign keys; a plain one is recognized by its shape, on the client, from the relation meta data it already has. |

Every statement but `linkType` has to be said about a versioned type and is
reported at startup otherwise: declaring how conflicts are resolved for a
type that can have none is a forgotten column, and hearing about it at
startup is much cheaper than finding the write silently clobbering. Each
statement may be made once per type -- a second one either repeats the
first or contradicts it.

Each also takes a GraphQL type name in place of the class, for the types an
application has no class at hand for.

### Where the provider goes

Either as a `MetadataProvider` bean, which the domain picks up through the
`getBeansOfType()` call in
[Wire up a Spring application](/qlive-framework/how-to/wire-up-a-spring-application/),
or inline in the domain builder, which is what `qlive-test` does:

```java title="QLiveDomainConfiguration.java"
.withMetadataProviders(
    QueryConfigMetadataProvider.newProvider()
        .forAllTypes()
            .pageSize(5)
        .build(),

    MergeMetadataProvider.newProvider()
        .resolveConflicts(Bar.class)
        // many-to-many connected to Bar
        .resolveConflicts(Baz.class)
        .ignoreFields(Foo.class, "created")
)
```

Both routes reach the same meta data, and what reads it does not care who
wrote it. See
[Add schema metadata](/qlive-framework/how-to/add-schema-metadata/) for
what else rides along on the same mechanism.

## The mutation is already there

`MergeLogic` contributes one mutation, `mergeWorkingSet`, and it is the
only one an application needs in order to store anything. A change travels
as field names and `GenericScalar` values, which QLive coerces to
whatever Java type the field actually has -- so there is no `BarInput`, no
`BazInput` and no mutation per operation to keep in step with them.

It is a bean in `QLiveConfiguration` rather than a component scan find: an
application's scan covers its own packages and never the framework's. What
picks it up is the `getBeansWithAnnotation(GraphQLLogic.class)` call the
application's domain configuration already makes -- so an application that
hand-lists its logic beans instead has to name this one.

## Next

- The form: [Edit rows with a working set](/qlive-framework/how-to/edit-rows-with-a-working-set/).
- The types the client reads about merging -- `MergeMeta`, the type meta
  data the declarations above produce --
  [Merge in the API reference](/qlive-framework/api/merge/).
