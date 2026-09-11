# Push (design)

Status: the build order below is built, steps 1 to 4 -- the message
model, the FilterDSL evaluator, the pub/sub core and transport, and the
entity-version adapter. The whole server side is there and tested; steps
5 to 9, everything client-side plus the coercion seam step 7 closes, are
designed and not built. Written 2026-09-10, reordered 2026-09-11.

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

**DomainQL's relation-fetching model explains why a database-backed
payload takes real work to build, even though none of that machinery
ever reaches the wire.** A jOOQ-generated POJO has no Java getter for
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
leaning on. A pub/sub publisher gathering a database-backed payload can
reuse that same batched-materialize mechanism rather than invent its own
defence against accidental N+1 queries. Whether the `DomainObject` that
step produces still carries its `FetcherContext` when it reaches
`publish()` turns out not to matter either way -- "Field resolution"
below covers why.

**Nothing WebSocket- or pubsub-related exists yet**, client or server.
**`QueryDocument` has no push seam yet** -- `notify()` is private, `rows`
is a public mutable array nothing outside the document can safely
mutate and have React hear about it. This is an explicit open item in
the merge design, not an oversight here.

**Field access is plain property access, never GraphQL field resolution,
and never a special case.** No `DataFetcher` is ever invoked while
evaluating a filter or delivering a message, and no query runs either. A
published payload is a dead, fully-materialized data structure from the
instant it leaves the publisher -- there is no lazy path to fall back to,
by design, not as an optimisation. An earlier draft of this design gave
`DomainObject` instances a special case here, reading a relation with no
getter from an attached `FetcherContext` as a plain map lookup. That's
gone -- not because payloads stop being `DomainObject`s (they don't; see
"Field resolution" below), but because there was never anything for a
special case to switch on: `DomainObject.lookupFetcherContext()` isn't a
JavaBean getter, so Svenson's own introspection never lists a
`fetcherContext` property to begin with, special-cased or otherwise.
Property access is Svenson property access, full stop, the same library
DomainQL's own type analysis already runs on -- not a hand-rolled
reflection layer. Not Svenson's own `JSONPathUtil` either, the generic
multi-segment dotted-path walker Automaton's filter evaluator uses -- not
because of any `FetcherContext` gap, since there isn't one for Svenson to
fall into, but because it throws on a missing intermediate value instead
of letting that condition branch simply not match, and carries
write/grow semantics -- auto-creating missing maps and lists -- this
evaluator has no use for.

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

(`ConditionParser` is a bean nobody injects. The live GraphQL path never
reaches it: a condition arrives as a `Map` graphql-java has already
parsed, and `ConditionCoercing` reads it from there without a
`JSONParser` being involved at all. Push is its first real consumer, and
the setup itself now lives in `runtime.util.TypeMappers.byClassName`,
which both parsers call, so the two cannot drift apart.)

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

**This problem is real for `Publish.message` and only hypothetical for
`Topic.payload`.** In the common flow -- Java code calls
`PubSubService.publish(topic, payload)` with an already-live typed
object, an `EntityVersion` or a merged domain row -- the server never
parses `payload` at all. The filter evaluator reads it as the live bean
it already is (see the FilterDSL evaluator section below), and
`Topic.payload` only becomes JSON once, on the way out, when a matched
subscriber's `Topic` message gets serialized for the websocket. Nothing
server-side ever turns that JSON back into a Java value; the client is
the only consumer of it, and whatever type it gives that value is a
TypeScript concern this design doesn't reach. The dynamic-type problem
below is specifically about `Publish.message`: the one payload field a
*client*, not Java code, populates, and that the server therefore does
receive as raw JSON it has to make some sense of before filtering can
run. `Topic` still gets the same base-type declaration and the same
selective-recast helper as `Publish` below -- symmetry costs nothing, and
a future in-process `TopicListener` receiving its own published message
back might still want it -- but proving `Topic.payload` round-trips is a
test of the machinery's generality, not a path this design's production
code ever exercises.

`CompositeTypeMapper` (`org.svenson.CompositeTypeMapper`) looks like an
escape hatch: it composes a list of `TypeMapper`s and returns the first
non-null hint, so feeding it one `ClassNameBasedTypeMapper`/
`SubtypeMatcher` pair for the envelope and a second for a payload base
type, each firing at its own field, looks like it ought to resolve both
in one pass. Checked against the source and confirmed with a throwaway
test, it doesn't -- and not for the registry reason above, for a
narrower one. `AbstractPropertyValueBasedTypeMapper.getTypeHint` (the
class both `ClassNameBasedTypeMapper`s extend) returns the *incoming*
type hint unchanged when its own `SubtypeMatcher` doesn't match, not
`null`. `CompositeTypeMapper` treats the first non-null result as final,
so the first mapper in the list, asked about a field its own base type
doesn't cover, hands back that field's declared type as if it were the
answer -- an abstract payload base class -- and the second mapper never
runs, in either list order. This would be a dead end even setting the
registry problem aside.

**The fix is to stop asking one pass to do this at all.** Parse the
envelope with `payload` (and `Publish.message`) declared as plain
`Object`, which is Svenson's ordinary untyped result for a nested JSON
object -- a `Map` -- no custom type mapper involved for this field,
nothing dynamic about phase one. That gives immediate, cheap access to
`topic`.

And phase one is where most consumers stop, not just where they start.
`org.svenson.util.JSONBeanUtil.getProperty(bean, name)` -- the accessor
Svenson's own utilities build on, and the one the FilterDSL evaluator
below is built on too -- reads a `Map` entry and a bean property through
the same call, by the same JSON property name; a condition compiled
against `payload.bazLinks.0.baz.name` neither knows nor cares whether
`payload` is the parsed `Map` or a recast instance of whatever class
`"Bar"` is bound to. So the untyped result phase one already produces is
sufficient for filtering, on its own -- there is no general obligation to
ever produce a typed instance at all.

A typed instance is still worth having when a specific piece of Java code
handling an inbound `Publish` wants compile-time field access rather than
string-keyed lookups on its `message` -- there is no such consumer
designed yet (client-publish authorisation is an open item below), but
whatever eventually reads a client's own payload server-side is exactly
the shape of thing that would want one. For that, *selective* recasting:
now that `topic` is known, resolve it against the pub/sub core's channel
registry to get the bound `Class<?>`, and hand the already-parsed `Map`
and that class to `org.svenson.util.RecastUtil.recast()`, which walks
the map graph directly through `JSONBeanUtil.getProperty`/`setProperty`
and fills a new typed instance from it -- confirmed from source, no
re-serialize-and-reparse round trip involved, exactly the "cheaper
direct conversion" this design used to only hope Svenson had. The point
of doing this selectively rather than as a fixed second phase of the
pipeline: it is a step a handler takes because it wants typed access,
not a step the message model performs on a handler's behalf whether
it's wanted or not.

**This makes pub/sub messages the one kind, in a general facility, whose
full type isn't knowable from the discriminator alone.** Every other
message kind is fully described by its own class -- the discriminator
alone tells the parser everything it needs. `Topic` and `Publish` are the
only kinds whose payload's shape is delegated to whichever channel
they're on, and where a caller wanting more than string-keyed property
access has to ask for it, explicitly, by class. That's a property of
pub/sub specifically, not a limitation of the general message model --
exactly the "pub/sub is one of the message types, with a dynamic
payload" framing this design starts from.

## Field resolution: pure JSON semantics, no GraphQL profile

GraphQL's relation-fetching complexity -- `ReferenceFetcher` /
`BackReferenceFetcher`, `FetcherContext`, the live-query fallback -- earns
its keep for GraphQL because GraphQL's whole premise is that the caller
hand-selects a slice of the object graph and the server fulfils exactly
that slice, no more. Pub/sub channels have no selection. A payload's
Java class declares whatever fields and relations the publisher decided
that channel carries, and every instance of it carries all of them, every
time -- there is no per-subscriber shrinking or growing of the shape. A
mechanism built to serve a variable selection has nothing to do once the
selection is fixed at the class, so this design doesn't reach for it,
ever, for any payload -- field resolution has exactly one path, always:
an ordinary Svenson property read, real getter or nothing.

That includes payloads that *are* a `GeneratedDomainObject`/
`GenericDomainObject`, like `Bar` itself, published directly rather than
wrapped in something else -- a perfectly ordinary case, not one this
design routes around. What it publishes, though, is flat: exactly `Bar`'s
own real properties, the same scalar set a generated TypeScript type for
`Bar` already expects on the client side, nothing gained or lost by going
through pub/sub instead of a GraphQL query. If that `Bar` instance
happens to be carrying an attached `FetcherContext` -- because it came out
of a `QueryExecution` materialize step run for some other reason, say --
that is simply inert here, not because anything detects and strips it,
but because there was never anything to detect: `lookupFetcherContext()`
isn't a JavaBean getter, so it was never a Svenson property for a
`FetcherContext` to be attached *to*, from field resolution's point of
view. It genuinely does not exist in the JSON world; ignoring it costs
nothing because there is nothing being ignored, mechanically speaking.

A publisher that wants relations in a payload's actual shape -- `Bar`
with its `bazLinks` -- gets them the same way any Java code builds an
object graph: a class with a real `getBazLinks()`, populated with
whatever `Baz` instances the publisher gathered (`QueryExecution`'s
materialize step is a reasonable way to fetch them, a live query works
just as well) and handed to `publish()`. That class can be `Bar` itself,
given an extra field, or a small container wrapping a `Bar` and a
`List<Baz>` -- ordinary composition, not a rule this design imposes.
Whatever a publisher chooses, each `DomainObject` anywhere in the graph
is still read exactly as flat as it would be alone; nesting doesn't
change the rule, because there was never a special rule to begin with,
only the one path field resolution ever has.

This is also where "the JSON cycle" section's selective `RecastUtil`
step lands, for the one direction it could actually have gone wrong:
`RecastUtil.recast()` drives off the *target* class's own declared
properties, calling `JSONBeanUtil.setProperty` for each one it finds --
there is no `bazLinks` property on `Bar` for it to set, so a client
sending relation-shaped content on a channel bound to plain `Bar` simply
has nowhere for that content to land. A design trying to preserve
relation fidelity through the wire would have needed exactly the
opposite: analyze the inbound JSON to find which keys name GraphQL-only
relations, cut them out before recasting the rest, and reassemble a
`FetcherContext` from the cut-out pieces afterward, by hand, since
`RecastUtil` has no notion of one. Not supporting GraphQL semantics is
what makes that surgery unnecessary -- relation-shaped input for a flat
channel is simply data nothing declares a place for, an ordinary shape
mismatch, not a case this design has to detect and route around.

The evaluator never falls through to a live query, or to any GraphQL
resolution. Whether a path segment resolves to `null` because a
publisher legitimately has nothing there yet, or because a relation
simply isn't populated on the particular `DomainObject` handed to
`publish()`, that condition branch does not match, full stop -- the same
no-fallback invariant `QueryExecution` already commits to for query
documents. (A path segment naming no real property on the payload class
*at all* is a different case, caught earlier, at subscribe time -- see
path validation below.) The obligation this puts on the publisher is
correspondingly plain: populate every relation any subscriber's
condition might reference, on whatever instance it hands to `publish()`,
before calling it.

**Path validation** -- is this field name real for this channel's type,
is it to-one or to-many, what type does it lead to -- is a separate,
schema-level question, answered at subscribe time rather than by walking
a live instance. With no `DomainObject`/`FetcherContext` case to account
for, it needs no DomainQL lookup either: the payload class's own declared
JSON properties, read through the same Svenson class introspection
`JSONBeanUtil`/`TypeAnalyzer` already do (`JSONClassInfo`,
`JSONPropertyInfo`), are the entire schema there is to validate against.
A hop resolves to "getter", full stop -- a `Collection`-typed property is
to-many, addressed by numeric index below; anything else is to-one. This
works identically whether the payload class happens to be DomainQL-
registered or hand-written, so unlike the SQL-side condition compiler
`FieldResolver`/`QueryPlanBuilder.PathResolver` implement dotted-path
walking for, there is no "channel not registered, skip this step" case
left to carve out -- validation was never really a DomainQL question here,
only ever a question about one payload class's own shape.

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
Plain property access throughout, through Svenson, exactly as "Field
resolution" above describes -- no relation special case anywhere. This is
exactly why the message model above never has to recast `Publish.message`
into
its bound class before evaluating a condition against it: `JSONBeanUtil`
reads a `Map` and a bean the same way, so the phase-one parse result is
already everything the evaluator needs. Compiled once
per subscribe -- one operator-to-implementation table, mirroring
`FilterOperators`' whitelist shape but producing composed `Predicate`s
instead of jOOQ `Condition`s. Values are already the Java objects they
claim to be by the time they reach the transformer -- the same contract
the SQL transformer works under, where `ConditionCoercing` has converted
every value in the hierarchy with the coercing of the scalar type that
value names. That leaves one seam still open, and it is step 7's, because the
two paths reach a condition differently: `ConditionCoercing.parseValue`
reads a `Map` graph, while a `Subscribe` arrives as a `CNode` whose
values are still whatever JSON made of them, so something has to run the
scalars over an already-parsed hierarchy -- once, at subscribe time,
between the parser and `FilterTransformer`, and on the inbound frame
rather than in `PubSubService`, so that an in-process subscriber
building its condition with `FilterDSL` is not re-parsing values it
already has right. Until then a constant is whatever JSON made of it,
which covers strings, numbers and booleans -- the whole of the
entity-version subscription -- and leaves a timestamp constant still a
string. An operator this backend
cannot honour throws at transform time, naming itself, with whoever is
registering the subscription still looking at the result -- not a filter
that silently matches nothing forever.

Two places the evaluated semantics part company with the database's, and
both are worth knowing before a client builds a condition against them.
The logic is two-valued: a comparison against a missing value does not
match, and `not` around it therefore holds, where SQL's comparison
against NULL is itself NULL and stays NULL negated. And `likeRegex`
matches anywhere in the value rather than describing the whole of it,
which is what JOOQ's own `likeRegex` means once it reaches Postgres as
`~`.

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
resolution, already covered above: Svenson property access against each
object's own declared JSON properties, never Automaton's generic
reflective walk over arbitrary fields.

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

**The filter chain does run against the WebSocket upgrade here**, which
was the open question this whole section was gated on, and it was asked
out loud against a real server rather than assumed --
`PushWebSocketTest.refusesAHandshakeFromSomebodyNotLoggedIn`. The
upgrade is an ordinary GET, `FilterChainProxy` secures it, and
`qlive-test`'s catch-all `hasRole("USER")` refuses it with a 401 to
somebody not logged in without naming the URI at all. So the interceptor
needs no session-based authentication step of its own, and
`AppAuthentication.current()` there is the same identity the rest of the
request path sees. One incidental: the entry point that answers the
refused handshake is the GraphQL one, being the fallback for anything
not asking for HTML, so the 401 carries a GraphQL-shaped body. Harmless
-- a client sees a failed upgrade either way.

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
settled here -- so the handler parses, routes and *refuses* a client
`Publish` for now. Shipping it unauthorised would let any logged-in user
forge a message on `"EntityVersion"` that every other client would read
as the framework's own.

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
Registering, or first publishing to, a topic associates it with a Java
`Class` -- nothing more. Subscribing does not: a subscriber brings no
class with it, so a channel created there could validate nothing, and
the name a client got wrong would look like a channel that is simply
quiet. An application registers its channels at startup, which is also
what lets a client subscribe before the first message. Path validation (above) walks that
class's own declared JSON properties directly, so there is no separate
DomainQL lookup here and no distinction between a domain type and a
handwritten POJO at this layer: whatever class a channel is bound to, the
registry just remembers it. This registry is also what the message
model's dynamic-payload conversion
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
flat -- no relations -- a plain bean with plain properties, exactly like
every other channel; nothing about it is a special case. `fieldMask`
travels as a decimal string, since it is a 128-bit value, past what a
JavaScript `Number` can hold exactly, and the client side needs `BigInt`
for it.

A second, illustrative consumer worth spelling out here even though it
is not necessarily an early build step: publishing an actual
database-backed domain object, changed fields and all. This is where
"Field resolution"'s point about relations is genuinely exercised rather
than sitting idle -- a publisher of, say, a changed `Bar` row wanting its
`bazLinks` filterable populates a real `getBazLinks()` on whatever it
hands to `publish()` (`Bar` itself, given that field, or a small wrapper
around it), gathered however turns out to be practical -- `QueryExecution`'s
own materialize step is one legitimate way to fetch the data efficiently.
Whether that gathering-and-populating step becomes a shared, reusable
helper or ends up as a second implementation of the same idea is a
decision for whenever it's
actually built, not one to force now.

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

1. **The general message model, and the selective payload recast.**
   The abstract base classes and concrete message POJOs (`Subscribe`,
   `Unsubscribe`, `Publish`, `Topic`, `Subscribed`, `Error`), the
   `ClassNameBasedTypeMapper` wiring for message-kind dispatch, and a
   `RecastUtil`-based helper that turns a parsed `Publish.message`/
   `Topic.payload` `Map` plus a topic-resolved `Class<?>` into a typed
   instance on request -- exercised for `Publish.message`, the field the
   server actually receives as JSON; `Topic.payload` gets the same
   treatment for symmetry, not because production code parses it. Tested
   standalone, no transport: round-trip every fixed message kind through
   serialize/parse; assert `Publish.message` parses as a plain `Map` with
   no registry involved at all; recast that `Map` against a stubbed
   topic-to-class registry entry and assert the
   result is the bound concrete type with its fields filled in; assert
   recasting against an unregistered topic fails clearly.

   Built as `com.dataciders.qlive.model.push`: the message classes,
   `PushMessageParser`, and `PayloadRecast` against a `TopicTypes`
   lookup the channel registry implements in step 3.
2. **The precompiled FilterDSL evaluator**, standalone, unit-tested with
   no WebSocket involved: flat payload fixtures first, mirroring
   `EntityVersion`'s own shape and exercising the operator table, then a
   relation-bearing fixture -- a hand-built payload POJO with a real
   `bazLinks` getter, asserting that a `null` relation does not match
   rather than throwing or querying -- then an indexed-list-path fixture
   (`bazLinks.0.baz.name`-shaped) asserting index semantics rather than
   any-element matching.

   Built as `com.dataciders.qlive.runtime.filter`: `FilterTransformer`,
   the `PayloadOperators` table, and `PropertyPath`, which is where a
   path is checked against the channel's class and where a to-many hop
   without an index is refused.
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

   Built as `com.dataciders.qlive.runtime.pubsub`: `PubSubService`/
   `DefaultPubSubService`, the package-private `Topic`/
   `TopicRegistration`, `Recipient`, `PushWebSocketHandler`,
   `WebSocketRecipient`, `PushHandshakeInterceptor`, and `PUSH_URI` in
   `QLivePaths` beside the two prefixes the frontend already has to
   agree on. Three things came out differently than the step describes,
   each noted where it belongs above: filtering is wired in already,
   because leaving it out meant a subscription that silently matched
   everything; subscribing to an unregistered channel is refused rather
   than creating one; and a client `Publish` is refused until the
   authorisation question below is settled. `Topic` is package-private
   because the message class of the same name is the one a framework
   user imports, and nothing outside the core needs the other.
4. **The entity-version adapter, server side.** The `EntityVersionsEvent`
   listener publishing to `"EntityVersion"`. Tested by performing a
   merge and asserting the message arrives.

   Built as `EntityVersionPublisher`. Two things the record needed
   before it could travel, neither of them push-specific: the mask goes
   out as a decimal string, and the timestamp in the ISO-8601 form the
   GraphQL `Timestamp` scalar already produces -- left alone, Svenson
   makes a `java.sql.Timestamp` into a dump of `java.util.Date`'s
   getters. A bit operation therefore takes a decimal string on either
   side, which it had to anyway: a 128-bit constant has no exact JSON
   number to arrive as, so a client could not have written the
   motivating subscription at all.
5. **The client connection module.** `pubsub.ts`: connect, reconnect
   with backoff, the `PubSubConnection` store, the generic
   `subscribeToTopic`, wired into `startup()`. No entity-version-specific
   routing yet -- this proves messages arrive and survive a reload.

   What a payload is typed as on arrival, and what a condition's field
   paths are checked against, are not settled here.
   `docs/design/client-types.md` has them: a channel's payload class is
   a Svenson-described Java class, and generating TypeScript for those
   is a facility pub/sub is only the first user of. Until that is built
   `subscribeToTopic` is generic in its payload with the caller
   supplying the parameter.
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
   my own id>` clauses.

   The server half of this landed with step 3 -- compiling the
   condition, and the `Subscribed`/`Error` replies, which a refused
   subscribe needs whatever else is or is not wired. What is left here
   is the client half, and the one genuinely unbuilt server piece: the
   `CNode` -> `CNode` coercion pass beside `ConditionCoercing.parseCNode`
   that re-reads each `Value`/`Values` through `parseScalar`. Until that
   exists a timestamp constant off the wire is still a string, which the
   entity-version subscription does not care about and something with a
   date range in it would.
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
- Which channels a client may `publish` to, and how that gets authorised
  against a channel's declared type.
- That the FilterDSL a subscription writes reads a to-many positionally
  while the same DSL against the database reads it as "some element
  satisfies" -- deliberate, and recorded in `PropertyPath`'s javadoc,
  but two dialects of one DSL is a thing a framework user has to be
  told rather than discover. Carried in `docs/design/client-types.md`.
- Whether restructuring a fetched result into a payload POJO (the
  database-backed consumer in "Entity-version push" above) becomes a
  helper shared across channels, or stays one-off per publisher.
- WebSocket Origin/CORS handling, and how the dev-mode Vite proxy handles
  a `ws://` upgrade -- unverified.
- Presence -- "somebody else has this row open" -- stays deferred to its
  own design, with Automaton's `DomainMonitorService` / `useEntity.js` /
  `Monitor` as prior art worth rereading when that gets written.
