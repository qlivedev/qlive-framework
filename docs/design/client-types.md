# Client types (design)

Status: designed, not built. Written 2026-09-11, out of the push
design's step 5 -- but deliberately not part of it.

The client's TypeScript type world covers the GraphQL domain and stops
there. Everything else the server sends arrives untyped, and the
framework user holds two vocabularies: the types that are generated for
them, and the types they transcribe by hand.

## Problem

Push is the pressure, but scoping the answer to push would repeat the
mistake one layer up.

**Pub/sub is the first websocket user, not the only one.** A generator
built around "channel payloads" would have to be widened or rebuilt for
the second facility that wants to send something a GraphQL query cannot
express -- and by then its vocabulary is in an application's source.
What the client needs typed is Java objects that reach it as Svenson
JSON. That pub/sub is what sends them today is a fact about today.

**Inside pub/sub, a payload is a Svenson-described class, and Svenson
expresses things DomainQL's GraphQL projection cannot.** A map with
`@JSONTypeHint`, a class carrying declared properties plus dynamic ones,
generics that nest. None of that is exotic -- it is the ordinary shape
of a message that is not a database row. And all of it is expressible in
TypeScript. GraphQL is the wrong ceiling to hold a payload to: Svenson
is the limit, and TypeScript sits comfortably above it.

So: everything the server can send has a name in the client's type
world, the user reads all of it in the editor, and nothing gets
transcribed.

## What already exists, and why it decides the design

**The generation pipeline is already the right shape.** `/_dev/graphql`
-> `generate-schema` -> `schema.graphql` -> `generate-ts` ->
`types.d.ts`, with the endpoint dev-profile only, unauthenticated,
CSRF-exempt, and guarded against leaking into other profiles by
`DevEndpointsOutsideDevProfileTest`. A second description served from
the same endpoint, feeding a second generator, is that pipeline with a
different body -- nothing new to learn operationally. Keeping the JSON
intermediate rather than going endpoint-to-`.d.ts` in one step buys what
`schema.graphql` buys: a generator testable from fixtures with no
backend running, which is how every generator in qlive-codegen is
tested.

**Generated code inside an application may name the application's types;
framework code may not.** `Q_Foo.ts` imports `Foo` from `../types`.
qlive-ts cannot, which is why the merge model is declared twice -- once
in `model.merge` for the server and the schema, once by hand in
`qlive-ts/src/merge/types.ts`, whose header says exactly that. That
duplication is the pressure this design is under and it predates
websockets by a year. It is also the mechanism the design runs on: a
generated file that lives in the application is the one place
framework-shaped declarations can reference application types.

**Svenson's class info is already the server's own answer.**
`PropertyPath.compile` validates a subscription's field paths by walking
`JSONUtil.getClassInfo`, not by reflecting over the class. That is why
`EntityVersion.fieldMask` is a string on the wire: `getFieldMask()` is
`@JSONProperty(ignore = true)` and `getFieldMaskValue()` carries it. A
generator on the same source describes what is actually sent, and -- the
sharper point -- describes exactly the paths a filter may legally name.
Two sources would drift, and the drift would show up as completion for a
path the server throws on.

**DomainQL can say whether a class is already a GraphQL type.**
`domainQL.getTypeRegistry().lookup(javaType)` returns the `OutputType`
or null; `MergeMeta`, `QueryConfigMeta` and the metadata providers all
use it. That is how the walk knows to emit an import from `./types`
instead of a second, differently spelled declaration of a type the user
already has.

## Two layers

**Layer 1, the emitter.** In: a set of root Java classes. Out: named
TypeScript types, transitively closed, with anything the walk reaches
that DomainQL already exposes emitted as an import rather than a
declaration. It knows about Svenson and about DomainQL's registry, and
about nothing else.

**Layer 2, registries that name roots.** A facility that puts Java
objects on the wire declares which classes it can send and, where it has
names for them, what those names mean. Pub/sub's registry is its channel
map, which `DefaultPubSubService` already holds as topic -> `Class`. The
emitter never hears the word "topic"; the facility never spells
TypeScript. A later websocket facility with a different addressing model
-- a request/response pair, a named view stream -- contributes its own
registry and its own small map, and reuses every type already emitted.

For pub/sub the generated map is the artifact the user actually reads:

```ts
import { Foo, Bar } from "./types"

export interface Channels {
    "foo.changed": Foo
    "bar.changed": Bar
    EntityVersion: EntityVersion
}
```

One concept -- channels have names, each name has a payload type -- and
it makes `subscribe("foo.changed", cond, msg => ...)` type itself,
narrow `msg`, and check the condition's field paths. Where the payload
is a domain type, which is the common case in a line-of-business
application, the user is filtering and reading a type they already know
from queries, and the map mints nothing new.

## What TypeScript covers that GraphQL does not

The emitter should reach Svenson's limit, not GraphQL's.

- **`Map<String, X>` with `@JSONTypeHint(X.class)`** -> `Record<string,
  X>`. `PropertyPath.elementType` already reads `getTypeHint()` with a
  generic-signature fallback, so validation and emission read the same
  two sources in the same order.
- **`DynamicProperties`** -> declared properties plus an index
  signature. GraphQL cannot say "these fields, and also whatever else
  arrived" at all. On what a client receives this is exact; a payload
  read back the other way keeps only what its class declares a place
  for, `RecastUtil` being property-driven.
- **Renamed and pruned properties** -- `@JSONProperty(value=, ignore=,
  readOnly=)` -- fall out for free, the `fieldMask` case being the
  motivating one.
- **Nested generics** -- a map of lists of a union -- nest the way they
  read.
- **`Object`** -> `unknown`, matching `PropertyPath.known()`, which
  treats `Object` as a class that says nothing rather than one that says
  everything.

## What a payload cannot be, and why that is already decided

Not a discriminated union. The transport spends its type mappers at the
frame level: `PushMessageParser` registers `byClassName(PushMessage)`
and `byClassName(CNode)`, composed through `TypeMappers.firstAnswer`
because Svenson's own composite cannot consult two. A payload class
discriminating its own subtypes would want a third, scoped to its base
type -- and the parser's javadoc already gives the reason it cannot have
one: which class a payload belongs to is decided by its channel, which
is runtime state no parser can be handed at construction time. That is
why `Publish.message` is declared `Object`, lands as a `Map`, and gets
typed on request through `PayloadRecast`.

`RecastUtil.recast` closes the other door. It walks the parsed map graph
into an instance of the class the caller names, driven by that class's
declared properties, with no discriminator dispatch anywhere in it. A
polymorphic field inside a payload would recast into its declared type
and lose the subtype in silence.

So the emitter describes declared property types and does not attempt to
enumerate subtypes -- which is also all it could do, there being no
registered mapper to enumerate them from. Alternatives between payload
shapes belong to the addressing layer that already exists for them: two
channels, not one channel with two kinds of message on it.

## Nullability

Svenson declares nothing about it; GraphQL declares `NON_NULL`. Emitting
every property optional would make `?` mean "the schema says nullable"
in `types.d.ts` and "nobody said" in the file beside it -- the same
syntax carrying two meanings in a world the user is trying to hold
whole. Java primitives are free information and should be emitted
non-optional. Past that, a marker annotation is new API surface for the
framework user, and the decision should wait until a real payload makes
the ambiguity bite rather than be taken on speculation.

## Build order

1. **The emitter, against fixtures.** Description JSON in, `.d.ts` out,
   no backend, tested the way `generateTS` is.
2. **The description endpoint and the root-provider seam.** A bean
   interface a facility implements to contribute roots and name maps;
   the endpoint aggregates whatever is registered.
3. **Pub/sub contributes its channel registry**, and a
   `generate-channels` CLI writes the map beside `types.d.ts`.
4. **The client's `subscribe` types off the channel map** -- payload and
   condition both.

## Open items

- One generated file or two. A separate `channels.d.ts` is one more
  place to look; emitting into `types.d.ts` gives a single "everything
  the backend told me" file but couples two generators with different
  inputs and makes an application with no channels pay for the
  machinery. A third option is a separate file that `types.d.ts`
  re-exports, so at least the import path is one thing.
- **FilterDSL means two different things on the two sides of the wire.**
  A query's `field("bazLinks.baz.name")` crosses a to-many as "some
  element satisfies"; a subscription's must be
  `field("bazLinks.0.baz.name")`, positional, because a published
  payload is a dead data tree. Both are right and the divergence is
  deliberate, but it is one DSL with two dialects, which is a larger
  thing for a user to hold than a second generated file, and it is
  currently recorded only in `PropertyPath`'s javadoc.
- Channels created lazily are invisible to generation. `register()`-ed
  channels exist from bean construction, so a booted dev backend has
  them all; `publish()` creating one from `payload.getClass()` happens
  after generation has run. That argues for documenting `register` as
  the way to get a typed channel, with publish-created ones staying the
  untyped convenience they already are.
- Whether a facility ever turns up that genuinely needs alternatives
  under one name, and what it costs then. The channel-per-shape answer
  above is cheap for pub/sub because channels are free; an addressing
  model with no such axis would have to pay for the third mapper some
  other way, most likely by binding it per connection rather than at
  parser construction.
- Whether the framework's own Svenson types -- the push frames,
  `EntityVersion`, and the merge model -- should go through the same
  emitter into qlive-ts at framework build time instead of being written
  by hand. It would retire the `merge/types.ts` duplication, but the
  lifecycle is different: generated once into the framework rather than
  per application, against roots the framework declares about itself.
