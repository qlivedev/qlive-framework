# Push (design)

Status: designed, not built. Written 2026-09-10, reordered 2026-09-11.

A general-purpose pub/sub mechanism for QLive, with a precompiled
FilterDSL evaluator doing the per-subscription filtering. Entity-version
push -- notifying a client that a row it has open just changed under it --
is this design's motivating use case and its first consumer, not the
thing the mechanism is built around. The relationship is the same one
Automaton has between its generic `PubSubService` and the one real
feature built on it, `DomainMonitorService`, which is a presence feature
and has nothing to do with row versioning.

## Problem

`docs/design/working-set-merge.md` left push out of scope on purpose but
built two seams for it in advance. A successful merge already publishes
an `EntityVersionsEvent` -- entity type, id, new version, field mask,
owner -- through Spring's `ApplicationEventPublisher`, and
`WorkingSet.storedState()` is already public specifically so a push
message can be its second caller; `merge()` is the only caller today.
That doc calls push "the important one... worth more than any amount of
conflict UI" and sketches the target subscription model directly:

```
entityType eq "Bar"
  and entityId in [ ...the ids on screen ]
  and fieldMask bitAnd <mask of the fields this form shows> ne 0
  and ownerId ne <me>
```

-- a FilterDSL condition, evaluated server-side per subscription, and
names the missing piece precisely: an in-memory evaluator. QLive has a
FilterDSL already, and a compiler for it, but the compiler only ever
targets SQL. Nothing evaluates a condition against a plain Java object,
and nothing pushes anything anywhere.

Framing it around entity versions only would answer today's question and
close the door on the next one. An application will want to tell its own
users about its own events -- a job finished, a document was countersigned,
a chat message arrived -- and none of that is a row version. So this
design builds one mechanism, addressed by named, typed channels, with
entity-version push as the first thing built on top of it rather than
baked into it.

## What already exists, and why it decides the design

**The event-publish seam is real and already wired.** `EntityVersionsEvent`
(`qlive/.../runtime/merge/EntityVersionsEvent.java`) is published by
`DefaultVersionService.write()` after every successful batch insert of
version records, and `DefaultVersionHolder` is already a listener on it,
filling the in-memory version cache the merge conflict path reads from.
`EntityVersionsEvent`'s own javadoc anticipates a second listener: a
websocket push module. That listener is what the build order below adds;
nothing about the event itself needs to change.

**DomainQL's relation-fetching model is the one thing this design leans
on hardest, and it is worth restating precisely, because it rules out
the obvious wrong design.** A jOOQ-generated POJO has no Java getter for
a GraphQL-only relation field -- `Bar` has no `getBazLinks()`. The GraphQL
field is served by `ReferenceFetcher` / `BackReferenceFetcher`
(`domainql/.../fetcher/`), and both start the same way:

```java
if (source instanceof DomainObject &&
    (fetcherContext = source.lookupFetcherContext()) != null)
{
    return fetcherContext.getProperty(relationModel.getLeftSideObjectName());
}
```

`FetcherContext` (`domainql/.../fetcher/FetcherContext.java`) is a tiny
dynamic property bag attached to one POJO instance. If it is missing, or
has no property under that relation's field name, both fetchers fall
through to their own live jOOQ query. That fallback exists, but it is not
a second legitimate way to fetch data -- it is an emergency escape hatch,
meant never to be exercised. The framework's real, general-purpose
data-access tool is the batched, eager-materialize approach
`QueryDocumentService`/`QueryExecution` already implements: one query per
to-one-joinable subtree, one follow-up per to-many edge, and a
`FetcherContext` attached to every row afterward, covering every relation
the plan selected. To-many children are pre-registered with the property
set to `null` and filled in later -- deliberately `null`, never omitted,
because a **missing** property, not merely an empty one, is what triggers
the live-query fallback. `docs/design/query-document-service.md` states
the invariant this leans on:

> Every relation edge in the GraphQL selection must be covered by the
> plan, and anything the planner cannot handle must fail loudly at plan
> time rather than silently null out a field.

Lazy per-field fetching is the least useful of the alternatives DomainQL
offers, by design, for a framework that already covers named views with
implicit relations through the eager path -- it is not a shortcut worth
leaning on. A pub/sub publisher of a database-backed payload is just
another caller of that same batched-materialize mechanism, not a special
case that has to invent its own defence against accidental N+1 queries.

**Nothing WebSocket- or pubsub-related exists yet**, client or server.
**`QueryDocument` has no push seam yet** -- `notify()` is private, `rows`
is a public mutable array nothing outside the document can safely
mutate and have React hear about it. This is an explicit open item in
the merge design, not an oversight here.

**Field access is plain property access, never GraphQL field
resolution.** No `DataFetcher` is ever invoked while evaluating a filter
or delivering a message, and no query runs either. A published payload is
a dead, fully-materialized data structure from the instant it leaves the
publisher -- there is no lazy path to fall back to, by design, not as an
optimisation. The one special case is a `DomainObject` instance, where a
path segment naming a relation with no getter is read from its attached
`FetcherContext` as a plain map lookup, never as a trigger for fetcher
logic. Property access itself goes through Svenson directly -- the same
library DomainQL's own type analysis already runs on -- not a hand-rolled
reflection layer, and specifically not Svenson's own `JSONPathUtil`, the
generic multi-segment dotted-path walker Automaton's filter evaluator
uses: it has no notion of `FetcherContext` at all, and would either fail
or silently do the wrong thing on a relation-only field.

## The general message model

This starts here, before anything about pub/sub's own internals, because
everything downstream -- the evaluator, the registry, the transport --
sends and receives instances of it. QLive gets one general-purpose,
typed message facility over its websocket connection, not a protocol
built for pub/sub with everything else grafted on afterward. Every
message, in either direction, is a real Java class, dispatched by a
`"type"` discriminator field the same way
`com.dataciders.qlive.model.condition.CNode` already is: an abstract base
class per direction, one concrete subclass per message kind, each
contributing a read-only, class-name-derived `getType()`. Parsing goes
through a Svenson `JSONParser` configured with a `ClassNameBasedTypeMapper`
scoped to that base class and package -- the exact setup `ConditionParser`
already uses for `CNode`:

```java
ClassNameBasedTypeMapper typeMapper = new ClassNameBasedTypeMapper();
typeMapper.setBasePackage(CNode.class.getPackage().getName());
typeMapper.setEnforcedBaseType(CNode.class);
typeMapper.setDiscriminatorField("type");
typeMapper.setPathMatcher(new SubtypeMatcher(CNode.class));
```

(`ConditionParser` exists today but is not currently wired into a live
parse path anywhere in the codebase -- worth confirming during
implementation whether it is dormant code to revive and relocate, or a
deliberate standalone utility that push now becomes the second user of.)

This closed-set discriminator approach works cleanly for choosing *which
kind of message* a frame is. Inbound:

```
Subscribe   { topic, id, condition }
Unsubscribe { topic, id }
Publish     { topic, message }
```

Outbound:

```
Topic       { topic, ids, payload }   // ids: this connection's matching subscription ids, batched
Subscribed  { topic, id }
Error       { topic, id, message }
```

`ids` on a `Topic` message is a list for the same reason Automaton's
`TopicUpdate` carries one: a connection can have several subscriptions on
the same channel with different conditions, and if a publish matches more
than one of them, they are batched into a single outgoing message rather
than sent once per match. The set of message kinds is fixed at compile
time, exactly like the set of `CNode` subclasses is, and this is meant to
be general infrastructure -- whatever message kind QLive needs next goes
in beside these, not into a second protocol.

Pub/sub is one of those message kinds, and it is the one with a dynamic
payload. This is where the closed-set discriminator approach stops
working, and it's worth being precise about why, rather than discovering
it midway through an implementation.

### The JSON cycle, and why the CNode pattern doesn't extend to it

A `Topic` message looks like `{ type: "Topic", topic: "Bar", ids: [...],
payload: {...} }`. The `payload` field's concrete Java type isn't fixed
by the message kind -- every `Topic` message has the same shape -- it's
fixed by *which channel* the message belongs to, and that binding
(`"Bar"` -> `Class<Bar>`) lives in the pub/sub core's channel registry, a
runtime, stateful map populated as channels get registered while the
application runs. That's the cycle: parsing `payload` correctly in one
pass means the parser needs to already know its type; the type comes
from `topic`'s value; and resolving a topic string to a class requires
consulting a registry that doesn't exist as static configuration the way
`CNode`'s fixed subclass list does -- it can't be wired into the parser
once at construction time the way `ClassNameBasedTypeMapper` is.

Svenson's own type-mapper family, read directly from source
(`/home/sven/ideaprojects/svenson`), confirms this isn't solvable by
reaching for a different built-in mapper. `TypeMapper.getTypeHint(
tokenizer, parsePathInfo, typeHint)` only ever sees the *upcoming* token
stream at the position it's asked about -- it can look forward into the
object about to be parsed, never backward at a sibling already consumed.
`PropertyValueBasedTypeMapper` / `AbstractPropertyValueBasedTypeMapper`
(`org.svenson.PropertyValueBasedTypeMapper`) is built exactly for "a
field inside this object tells you what type this object is": it scans
forward for a `discriminatorField` *within* the object at the matched
path and looks its value up in a `Map<String, Class>` built once, at
parser setup time. That's the right tool for dispatching `Subscribe` vs.
`Topic` vs. `Error` on the envelope's own `"type"` field, where the
discriminator and the object it describes are the same object. It's the
wrong tool here: `topic` is a *sibling* of `payload`, not a field inside
it, and the topic-to-class mapping is a live registry, not a map anyone
can hand the parser at construction time before the application has
registered a single channel.

**The fix is to stop asking one pass to do this at all.** Parse the
envelope with `payload` (and `Publish.message`) declared as plain
`Object`, which is Svenson's ordinary untyped result for a nested JSON
object -- no custom type mapper involved for this field, nothing dynamic
about phase one. That gives immediate, cheap access to `topic`. Then, now
that the topic is known, resolve it against the pub/sub core's channel
registry to get the bound `Class<?>`, and convert the untyped value into
that type -- the simplest correct version of this being
org.svenson.util.RecastUtil which can reuse strings etc from the generic map graph
and fill them into new typed containers (better than a JSONification/Parsing cycle)

Two phases, no cycle: phase one needs nothing phase two
produces, and phase two has everything it needs by the time it runs.
Whether Svenson offers a cheaper direct conversion from an
already-parsed generic structure into a typed instance, avoiding a
second tokenize pass, is worth checking during implementation -- the
round-trip through a string is guaranteed to work and is where to start.

**This makes pub/sub messages the one kind, in a general facility, that
needs an extra step.** Every other message kind is fully described by its
own class -- the discriminator alone tells the parser everything it
needs. `Topic` and `Publish` are the only kinds whose full type isn't
knowable from the message kind alone, precisely because their payload's
shape is delegated to whichever channel they're on. That's a property of
pub/sub specifically, not a limitation of the general message model --
exactly the "pub/sub is one of the message types, with a dynamic
payload" framing this design starts from.

## Field resolution: Svenson and FetcherContext, not GraphQL

A channel's payload is not always a `GeneratedDomainObject`. Plenty of
channels will carry an ordinary, handwritten POJO hierarchy with no
DomainQL registration at all -- real nested objects and collections,
fully populated, nothing lazy anywhere. For those, resolving a path
segment is nothing more than an ordinary Svenson property read;
`FetcherContext` never enters into it. The `FetcherContext` fallback is
specifically for the case where the current instance is a `DomainObject`
-- `GeneratedDomainObject` or `GenericDomainObject` -- and the segment
names a GraphQL-only relation with no getter on the POJO at all. So the
rule per segment is: if the instance is a `DomainObject` and Svenson
finds no property for the segment, read
`lookupFetcherContext().getProperty(name)` instead, a plain map lookup
and nothing more; otherwise, read it normally through Svenson. Both
shapes are first-class. Most channels, being handwritten POJOs, will
only ever exercise the plain-property path -- database-backed payloads
are the legitimate but less common case, not the default assumption.

The evaluator never falls through to a live query, or to any GraphQL
resolution, when a relation is missing from the context. A relation
absent from a payload's `FetcherContext` makes that condition branch not
match, full stop -- the same no-fallback invariant `QueryExecution`
already commits to for query documents. This puts a real, explicit
obligation on the publisher: code calling `PubSubService.publish(topic,
payload)` for a database-backed type is responsible for materializing a
`FetcherContext` covering every relation any subscriber's condition
might reference, before it publishes -- reusing `QueryExecution`'s own
materialize step where that turns out to be practical, rather than a
second implementation of the same idea.

**Path validation** -- is this field name real for this channel's type,
is it to-one or to-many, what type does it lead to -- is a separate,
schema-level question, answered at subscribe time rather than by walking
a live instance, and it reuses knowledge the SQL-side condition compiler
already has: `DomainQL.getRelationModels()` for `RelationModel`s
(`sourceType`/`targetType`, `leftSideObjectName`/`rightSideObjectName`,
`targetField` of `ONE` or `MANY`), and `DomainQL.lookupField(domainType,
property)` for scalar fields. `FieldResolver`
(`qlive/.../runtime/query/condition/FieldResolver.java`) and
`QueryPlanBuilder.PathResolver` already implement dotted-path walking
against exactly these two lookups for the jOOQ backend -- the shape to
imitate for the in-memory transform step, resolving each hop to "getter"
or "`FetcherContext` property" instead of a jOOQ join or field. Where a
channel's type isn't registered with DomainQL at all, there is simply no
relation metadata to validate against, and this step is skipped.

**A to-many hop is addressed by numeric index, not existential
quantification -- a deliberate departure from the SQL backend's
semantics.** The SQL condition compiler treats a to-many hop as `EXISTS`
(`ExistsScope`, see `docs/design/query-document-service.md`'s section on
conditions crossing a to-many relation): "does *some* element satisfy the
rest of the path." A pub/sub payload is a dead data tree, not a
relational query, and a tree's natural path semantics are index-based,
the way any plain data-pointer syntax works: `bazLinks.0.baz.name` means
"the first element of `bazLinks`, then `baz`, then `name`" -- not "any
`bazLinks` element." Whether an existential "any element matches"
capability is ever needed is an open question, not part of the default
path semantics this design ships with.

## The precompiled FilterDSL evaluator

New package `com.dataciders.qlive.runtime.filter`, a peer to
`runtime.query.condition`, not a replacement for it or a dependent of it.
Plain property access throughout, through Svenson, with the
`DomainObject`/`FetcherContext` special case from above. Compiled once
per subscribe -- one operator-to-implementation table, mirroring
`FilterOperators`' whitelist shape but producing composed `Predicate`s
instead of jOOQ `Condition`s. Constant coercion happens once, at
transform time, through the existing `ConditionCoercing`. An operator
this backend cannot honour throws at transform time, naming itself, with
whoever is registering the subscription still looking at the result --
not a filter that silently matches nothing forever.

This, together with the message model above, is deliberately built
before any transport code exists. Both are pure: given a `CNode` and a
type, or a JSON string and a class, produce a value -- no socket, no
registry, no Spring context required to exercise either one in a test.
Building them first means the transport and registry work that follows
has two solid, already-tested pieces to sit on top of, rather than being
designed at the same time as they are.

## Automaton, and where this differs

Automaton (`/home/sven/ideaprojects/automaton`,
`/home/sven/ideaprojects/automaton-js`) has exactly this shape of
mechanism already, and most of it is worth mirroring closely rather than
redesigning:

**Transport.** Automaton's own config javadoc states the reasoning
plainly: *"We don't want to deal with all the socket.js/stomp stuff...
so we register our own... `TextWebSocketHandler`."* The same call, for
the same reason, here -- a raw `WebSocketHandler`, not STOMP.

**Named, typed channels, created lazily.** `PubSubService.publish(String
topic, Object payload)` is a call any framework or application bean can
make; `subscribe(recipient, topic, condition, subscriptionId)` covers
both a websocket connection and a server-side, socket-less listener --
Automaton's `TopicListener`, the pattern `DomainMonitorService` uses to
both publish and listen on its own topic in-process.

**Compile-once, evaluate-many condition evaluator.** Automaton's
`JavaFilterTransformer` (`runtime/filter/`, `runtime/filter/impl/`)
compiles a condition to a tree of `Filter` objects exactly once, at
subscribe time, and evaluates that tree fresh per message without ever
re-parsing it -- one class per operator, a name-to-implementation table.
QLive's evaluator has the same shape. Where it diverges is field-path
resolution, already covered above: Svenson property access with the
`DomainObject`/`FetcherContext` special case, never Automaton's generic
reflective walk.

**A per-connection subscription registry with batched fan-out.**
Automaton's `Topic` / `TopicRegistration` / `Recipient`: one connection
can hold several concurrent subscriptions, across different channels,
each with a client-supplied id; when several of a connection's
subscriptions match the same publish, they are batched into one outgoing
message rather than sent once per match. The concurrency shape is worth
mirroring too -- per-topic `synchronized` subscribe/unsubscribe, a
lock-free snapshot copy taken for publish-time iteration.

**Unsupported operators fail at transform time, not silently at evaluate
time.** Automaton's `IsDistinctFromFilter` throws from `configure()`
rather than waiting to be evaluated, because "is distinct from" has no
sane meaning against a bare Java object. QLive does the same for its own
SQL-only operators.

**Disconnect sweeps every one of that connection's subscriptions, across
every channel.** Automaton's `ConnectionListener.onClose` does this --
and Automaton's own history (`68f94cf "Fix pubsub connection
unscubing"`) shows this exact cleanup was once silently lost in a
refactor. Worth explicit test coverage here for exactly that reason, not
just an implementation that happens to do it.

**No subscription replay on reconnect.** Automaton doesn't attempt this
either; a dropped connection's subscriptions are simply gone, and the
client re-issues them.

Three places this design deliberately does not follow Automaton:

**Identity is resolved fresh at every handshake, not minted once by the
server and spent by the client.** Automaton's `AutomatonClientConnection`
identity is captured once at page-render time, embedded in the page as a
`connectionId`, and consumed exactly once when the socket first opens --
`preparedConnections.remove(cid)`. After that the cid is permanently
spent. The cost is real and documented in Automaton's own behaviour: the
client's reconnect loop keeps retrying with that now-dead cid, the server
closes the connection with code `4100`, and the client shows a hard
*"Server Restarted. Please Reload"* prompt for any drop that isn't
recovered on the very first attempt -- not only an actual restart. QLive's
websocket handshake is an ordinary same-origin HTTP GET, carrying the
same session cookie as any other request, and `AppAuthentication.current()`
already matches `EntityVersion.ownerId` to an `app_user.id`. A
`PushHandshakeInterceptor` reads that identity fresh on every handshake,
including every reconnect, and stashes it on the WS session. There is no
token to mint, expire, or run out.

That said: **whether Spring Security's filter chain actually runs against
a WebSocket upgrade request in this setup is not confirmed, and is
flagged directly as historically not the case in an earlier setup.** This
has to be checked against a real authenticated session before anything
else in the identity design leans on it -- it is the first thing the build
order verifies once transport work starts. If the filter chain does not
reach the handshake, the interceptor needs its own explicit
session-based authentication step rather than trusting
`SecurityContextHolder` to already be populated.

**No generic "current subscriber" context primitive.** Automaton has
`context()` / `FilterContextRegistry`, letting a condition reference a
named value like "the current user." But it resolves that value once per
`publish()` call and reuses it across every subscriber being evaluated
for that message -- so it reflects the *publisher's* identity uniformly,
not each subscriber's own, and cannot express a true per-connection "not
me." This is not necessarily a bug so much as a resolution-phase choice
nobody ever needed to revisit: a context value meant for per-subscriber
personalisation has to be looked up at evaluation time, per subscriber,
not baked in once at transform or publish time, and Automaton's real use
cases -- ownerId comparisons included -- mostly never needed that.
QLive sidesteps the question entirely for now: `ownerId ne me` is built
client-side, the client substituting its own known user id as a literal
constant before it ever sends `subscribe`. If a genuine per-subscriber
context need turns up later, the fix is evaluation-time, per-subscriber
resolution -- not Automaton's per-publish one.

**Subscribe gets an acknowledgement.** Automaton's `PubSubMessageHandler`
never acks SUBSCRIBE or UNSUBSCRIBE; a bad filter -- an unsupported
operator, for instance -- only ever produces a server-side log line the
subscriber never sees. QLive replies with a `Subscribed` or `Error`
message, so a rejected subscription is visible to whoever asked for it.

**Publishing to a channel nobody has ever subscribed to is a no-op, not
an exception.** Automaton's `DefaultPubSubService.publish` throws
`IllegalStateException` when the topic doesn't exist. That is awkward for
a framework-internal publisher -- the entity-version listener, for
instance -- which has no reason to know or care whether anyone has
subscribed yet.

**A client may publish, mirroring Automaton, for symmetry.** Which
channels a client is authorised to publish to, and how that gets
enforced against a channel's declared type, is an open item below, not
settled here.

**Presence stays out of scope**, the same way the merge design left it
out. `DomainMonitorService` / `useEntity.js` / `Monitor`
(automaton-js) are real prior art worth rereading once presence gets its
own design, but nothing here is shaped to anticipate it. And Automaton's
live-query model, `InteractiveQuery.js`, has zero pubsub wiring at all --
worth understanding why, since it looks like an omission and isn't one.
Automaton always works with fully-typed instances, `_type` naming the
schema entry point, held as MobX observables; something like a working
set edits objects that came from a query but are no longer "connected"
to it, and MobX's identity-based reactivity means any component still
rendering that same object instance sees the edit, with no document or
store needing to relay anything at all. QLive has no MobX and no shared
observable identity -- `QueryDocument`'s rows are a plain snapshot array,
and a working set's draft is a proxy over a separate change map, not a
mutation of a shared object graph. A push message updating a row
therefore has no free channel to any document currently displaying that
same entity. The `QueryDocument.applyPush()` seam below does, explicitly,
what MobX identity gives Automaton for nothing. This is genuinely new
territory for QLive, not a port of anything Automaton has.

## Server: the pub/sub core

New package `com.dataciders.qlive.runtime.pubsub`, mirroring Automaton's
own package name for the same thing. `PubSubService` /
`DefaultPubSubService`, `Topic`, `TopicRegistration`, `Recipient`.
Registering, or first publishing or subscribing to, a topic associates it
with a Java `Class` -- and, through `Class.getSimpleName()` and
`RelationModel`, its GraphQL domain type and relation list, when it has
one. A handwritten POJO with no DomainQL registration is equally legal
as a channel's type; it simply has no relations to traverse. This
registry is also what the message model's dynamic-payload conversion
consults, so it exists before the transport does, not alongside it.
In-memory, single-instance state, the same as `DefaultVersionHolder`'s
cache -- accepted given the framework's stated scale of tens to hundreds
of concurrent users doing internal line-of-business work, not an
internet audience. Automaton runs the same unindexed linear scan on
publish without this being a known problem at comparable scale, and
there is no reason to solve a clustering problem nobody has yet.

## Connection & identity

`PushWebSocketHandler`, `PushHandshakeInterceptor` reading
`AppAuthentication.current().getId()` at handshake time and stashing it
on the WS session. The open question that gates this whole section:
does the security filter chain actually run against a WebSocket upgrade
request here? Confirm this first, against a real authenticated session,
before the rest of the identity design -- and `qlive-test`'s catch-all
`hasRole("USER")` covering the push URI -- is assumed to hold.

## Entity-version push, the first consumer

A listener on `EntityVersionsEvent`, registered via
`@TransactionalEventListener(phase = AFTER_COMMIT)` -- deliberately not
the plain `@EventListener` `DefaultVersionHolder` uses, because
populating a read cache costs nothing if the surrounding transaction
later rolls back, and pushing a message to a client is a visible,
external, un-undoable side effect. It publishes each `EntityVersion` in
a merge's batch to a fixed `"EntityVersion"` topic. `EntityVersion` is
flat -- no relations -- so the `FetcherContext` machinery is simply inert
for this particular channel; the plain-property path handles it
entirely. `fieldMask` travels as a decimal string, since it is a 128-bit
value, past what a JavaScript `Number` can hold exactly, and the client
side needs `BigInt` for it.

A second, illustrative consumer worth spelling out here even though it
is not necessarily an early build step: publishing an actual
database-backed domain object, changed fields and all. This is where the
`FetcherContext` machinery is genuinely exercised rather than sitting
idle -- a publisher of, say, a changed `Bar` row with its `bazLinks`
attached materializes a `FetcherContext` covering exactly the relations
it wants filterable, the same way `QueryExecution` already does for a
query document. Whether that becomes a shared, reusable helper or ends
up as a second implementation of the same idea is a decision for
whenever it's actually built, not one to force now.

## Client

A new module, `qlive-ts/src/pubsub.ts`. A `PubSubConnection` store, the
same `subscribe`/`getSnapshot` shape `QueryDocument` and `WorkingSet`
already use. The connection URL is built the way `util/graphql.ts`
already builds its request URL -- origin and `config().contextPath`, so
an application pays nothing extra in configuration to get push once it's
on QLive. A generic `subscribeToTopic(topic, handler, condition)`
mirrors automaton-js's `Hub.js` / `subscribeToTopic.js`. The connection
auto-starts from `startup()`; reconnect uses backoff with jitter and
re-issues every currently-registered subscription from inside this
module, invisible to application code -- unlike Automaton, where app code
owns resubscription itself.

On top of that generic layer, an entity-version-specific adapter
subscribes to `"EntityVersion"` and routes matches into
`WorkingSet.storedState()` and a new `QueryDocument.applyPush(entityType,
entityId, fields?)` seam. The payload stays mask-only -- no field values
travel over the wire -- consistent with the merge design's own reasoning:
"a client can mark those fields precisely without re-querying the row to
find out." A value-carrying alternative is a real fork in the design,
worth naming as a considered rejection if it's revisited later, not
something to slide into by accident. Whether a push message should ever
add or remove a row from an already-live `QueryDocument` -- as opposed to
patching a field on a row already visible -- is explicitly deferred: it
needs a JS condition evaluator that does not exist yet (separately
flagged, for `DomainTables`' planned client-side search feature), and it
interacts with pagination and sort order in ways a field patch to an
already-visible row simply does not.

## Build order

Each step is useful on its own and testable where it lands, the same
convention `working-set-merge.md`'s build order follows. The first two
steps are pure Java and TypeScript respectively -- no socket, no Spring
context, nothing to stand up -- deliberately, so the trickiest and most
novel pieces are solid and tested before anything is built on top of
them.

1. **The general message model, and the dynamic-payload conversion.**
   The abstract base classes and concrete message POJOs (`Subscribe`,
   `Unsubscribe`, `Publish`, `Topic`, `Subscribed`, `Error`), the
   `ClassNameBasedTypeMapper` wiring for message-kind dispatch, and the
   two-phase parse for `Topic.payload`/`Publish.message` (parse untyped,
   then convert once the topic resolves to a class via a channel
   registry). Tested standalone, no transport: round-trip every fixed
   message kind through serialize/parse; round-trip a `Topic` message
   against a stubbed topic-to-class registry entry and assert the
   payload comes back as the bound concrete type, not a raw map; assert
   an unregistered topic fails clearly rather than silently handing back
   an untyped structure.
2. **The precompiled FilterDSL evaluator**, standalone, unit-tested with
   no WebSocket involved: flat payload fixtures first, mirroring
   `EntityVersion`'s own shape and exercising the operator table, then a
   relation-bearing fixture exercising the `FetcherContext` path -- a
   hand-built domain POJO with a manually attached context, asserting
   that a relation missing from it does not match rather than throwing
   or querying -- then an indexed-list-path fixture
   (`bazLinks.0.baz.name`-shaped) asserting index semantics rather than
   any-element matching.
3. **Pub/sub core plus transport skeleton, no filtering yet.**
   `PubSubService`/`DefaultPubSubService`/`Topic`/`TopicRegistration`/
   `Recipient`, `PushWebSocketHandler`, `PushHandshakeInterceptor`,
   subscribe/unsubscribe with `condition: null` meaning "everything on
   this topic" -- the evaluator from step 2 isn't exercised yet, but the
   message classes from step 1 already are. `QLiveConfiguration` wiring,
   `spring-websocket` added directly to `qlive/pom.xml` (not the
   starter, which drags in STOMP's `spring-messaging` for nothing this
   design uses). First thing to verify here, before anything else
   depends on it: does the security filter chain actually run against
   the WebSocket handshake in this setup? Test against a real
   authenticated session, not an assumption. Tested via
   `@SpringBootTest`: subscribe a test WS client, publish, assert
   delivery; publish to a never-subscribed topic is a no-op, not an
   error.
4. **The entity-version adapter, server side.** The `EntityVersionsEvent`
   listener publishing to `"EntityVersion"`. Tested by performing a
   merge and asserting the message arrives.
5. **The client connection module.** `pubsub.ts`: connect, reconnect
   with backoff, the `PubSubConnection` store, the generic
   `subscribeToTopic`, wired into `startup()`. No entity-version-specific
   routing yet -- this proves messages arrive and survive a reload.
6. **Client entity-version routing -- the smallest end-to-end slice's
   finish line.** An `(entityType, entityId)` index from mounted stores
   to the connection, `WorkingSet.storedState()` calls, and
   `QueryDocument.applyPush()` designed and wired, subscribed with
   `condition: null`. Unfiltered, end-to-end, and the point at which push
   is genuinely visible to a user for the first time.
7. **Real filtering wired in.** Subscriptions carry an actual condition,
   compiled once (by the evaluator from step 2) and evaluated per
   publish. Entity-version subscriptions are built client-side with
   `entityType`, `entityId`, `fieldMask bitAnd`, and `ownerId ne <literal
   my own id>` clauses. `Subscribed` and `Error` replies land here.
8. **Client subscription wiring, the full loop.** Documents and working
   sets derive their condition from what's actually on screen, send it
   on registration, unsubscribe on disposal, and resubscribe on
   reconnect.
9. **An example view in qlive-test** exercising live push across two
   sessions or tabs -- the template an application copies, per this
   project's role as the framework's structural template, and the first
   place multi-connection identity gets exercised for real. Manual check:
   one tab edits a row, the other observes the push arrive and the
   relevant field marked stale or updated; the writer's own tab is not
   echoed its own write back.

## Open items

- Whether the `CNode`/`ConditionParser` model already has, or should
  grow, a context-node concept -- relevant only if a genuine
  per-subscriber personalisation need ever shows up; not needed for
  `ownerId ne me`, which is handled client-side.
- Whether `ConditionParser` is wired into any live parse path today or is
  presently dormant code the message-model design revives.
- Whether Svenson exposes a way to convert an already-parsed generic
  structure (a `Map`) directly into a typed instance, so the
  dynamic-payload conversion in the message model doesn't need a second
  tokenize pass through a re-serialized string -- the round-trip is the
  correct fallback either way.
- Which channels a client may `publish` to, and how that gets authorised
  against a channel's declared type.
- Whether `FetcherContext` materialization for a pub/sub payload becomes
  a helper shared with `QueryExecution`'s own materialize step, or ends
  up duplicated.
- WebSocket Origin/CORS handling, and how the dev-mode Vite proxy handles
  a `ws://` upgrade -- unverified.
- Whether the Spring Security filter chain covers the WebSocket handshake
  at all in this setup -- flagged as historically not the case elsewhere,
  and gating step 3 of the build order above.
- Presence -- "somebody else has this row open" -- stays deferred to its
  own design, with Automaton's `DomainMonitorService` / `useEntity.js` /
  `Monitor` as prior art worth rereading when that gets written.
