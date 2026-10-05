---
title: Defining the GraphQL Domain
description: How the domain an application works with is defined.
sidebar:
  order: 4
---
The QLive domain is assembled from different sources:

 * POJOs (Plain Old Java Objects), both generated from jOOQ, but also handwritten
 * GraphQL logic beans
 * Relations
 * Typedocs
 * Metadata 

How to build the domain bean from these sources is in
[Define the domain](/qlive-framework/how-to/define-the-domain/).

## Generated POJOs

jOOQ generates and updates the generated POJOs based on your database. qlive-test is set up to work with a PostgreSQL
database, but we generally support all [databases supported by jOOQ](https://www.jooq.org/doc/latest/manual/reference/supported-rdbms/).

The generated POJOs are usually the meat of the domain. Note that jOOQ generates metadata for foreign keys and unique
constraints and so forth, but those are not reflected in the generated POJOs. The POJOs are simple flat row containers.

## Handwritten POJOs

Handwritten POJOs basically work the same in QLive as generated POJOs, but they can't be written into the database unless
you put them in `jsonb` fields. They are used internally for data injection, transporting FilterDSL conditions, 
the MergeService, Pubsub messaging / websockets, and static code analysis.

You can use them for whatever you like, whether you transport your data over GraphQL or websockets. The requirements
for those two vary slightly. GraphQL does not allow for typed maps or generics, for example. 

## GraphQL logic beans

The GraphQL logic beans define GraphQL query and mutation methods which are the actual endpoints you client side access
via GraphQL. We should expect most of your data querying to happen over the special 
[QueryDocumentService endpoints](/qlive-framework/explanation/graphql-endpoints/#querydocumentservice), but you can define
alternate end points including degenerified ones to your needs. 

Alternatively, you can define pubsub channels based on one POJO payload type to which the client can subscribe, optionally
with a filter.

:::note

We already discussed logic beans back in [GraphQL endpoints](/qlive-framework/explanation/graphql-endpoints/), in case you
skipped that.

:::

## Relations

Relations describe connections between domain types. They are often based on foreign keys, but they don't have to be. 
We also support handling implicit relations between things you can query from a database and formally unrelated tables
the point to. Classic example would be a special view on something that contains a user_id column which we can then treat
as a relation to `AppUser` without a formal key being defined.

There can be more than one relation between the same two types. For example, a `ShopOrder` type might have two relations 
to an `Address` type, one named `deliveryAddress` and one named `billingAddress`.

See [Define relations](/qlive-framework/how-to/define-the-domain/#define-relations) for more details.

## Metadata

QLive offers a metadata system with which you can attach metadata at different levels:

 * as an additional metadata block
 * on the type level
 * on the field level

Metadata blocks can be objects of any kind. Type and field metadata is a key/value system with a variable value type
by metadata key.

### Builtin MetadataProviders

QLive defines MetadataProviders for a number of different purposes:

 * Generic Type metadata block
 * Relation metadata block
 * Computed field metadata
 * Unique key metadata
 * Field length metadata, from @Size

Above MetadataProviders reflect the current domain.

Some are also used to configure framework behavior:

 * Name fields, which grid columns of relations and pick options show
 * MergeService configuration
 * QueryConfig defaults and QueryDocumentService limits
 