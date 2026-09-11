# Progressive disclosure of schema types (design sketch)

Status: sketched, not started. Written 2026-09-06.

## Problem

Every full page load ships the entire GraphQL schema plus DomainQL meta
data to the client, inlined into the `root-data` placeholder of the
rendered template. The client parses all of it before the first view
renders.

At qlive-test's size that is invisible: 25 types, 75 KB.

The scale that motivates this doc comes from the predecessor generation
of this design, where a single application's domain reached the high
hundreds of tables and views. A domain that size does not stay that size
on the wire: input types, generic wrappers and paged-result types roughly
double the count, and the introspection plus meta payload lands in the
low megabytes of raw JSON -- compressing to a couple of hundred KB, but
still parsed in full before the first view renders.

Two mitigations already keep that tolerable, and both carry over: the
payload is compressed on the way out, and it is paid once per application
start rather than per navigation, where it hides inside the general
startup ramp.

So this is not urgent, and a domain that big may well never recur. It is
written down because the cost is structural rather than incidental -- it
scales with the size of the customer's domain, which is the one number
the framework does not control -- and because the machinery that would
fix it already exists for other reasons. The decisions worth making now
are the ones that keep that option open.

## What the schema costs today

The path, end to end:

1. `DefaultBootstrapService` runs DomainQL's `IntrospectionUtil.introspect()`
   once, in the constructor, and keeps the result as a `JSONHolder` -- a
   pre-serialized JSON subgraph spliced into the surrounding document
   rather than re-generated per request. Generation is therefore
   once-per-server-start, not per request.
2. `provideConfig(csrfToken, path)` returns that same holder for every
   caller. It takes `path`, but only the injection data varies by it;
   the config half is identical for every page and every user.
3. `ViteIndexController` splices the bootstrap into the template. Because
   it is embedded in generated HTML rather than served as a static
   artifact, it goes through live compression on every boot, not through
   the compress-once-and-`sendFile` path that static build artifacts use.
4. On the client, `initializeDerivedConfig()` walks `schema.types` in full
   to build the `typesByName` map, and `meta.genericTypes` to build
   `queryDocumentTypes`. Both are O(types) at startup.

Nothing in that chain is wasteful for its own job. The waste is that step
1 answers a question nobody asked -- "what is the whole domain" -- when
every consumer downstream asks a much narrower one.

## What already exists

Most of the substrate is in place, built for codegen rather than for
payload size:

- **`noSchema()`** (`qlive-ts/src/index.ts`) -- an empty exported function
  whose only purpose is to be seen by static analysis. `login.tsx` calls
  it. Nothing consumes the signal yet.
- **track-usage** -- `qlive-test/frontend/vite.config.ts` tracks
  `noSchema` and `GraphQLQuery` (among others) per module, writing
  `track-usage.json` at `vite build` and POSTing live snapshots to
  `/_dev/track-usage` in dev. The build therefore already knows, per
  module, both "does this entry point want a schema at all" and "which
  queries does it declare".
- **`GraphQLQueryTypingService`** -- parses each tracked query and walks
  its selection set against the live `GraphQLSchema` (`follow()`,
  producing `SelectionTypeNode`s that name their field type). The set of
  types a query touches is exactly what this walk already visits; today
  it is used to render TypeScript result types and thrown away.
- **Production availability** -- `DevConfiguration` exposes a
  `@Profile("prod")` `ResourceHandle<TrackUsageData>` over the built
  `track-usage.json`, so the tracked data is not dev-only.

Two loose ends in the current code that this design has to tidy rather
than build around:

- `StartupOptions.reduced` is declared and documented but never read.
  `startup()` calls `fetchBootstrap(options.path, haveViews)`, so the
  `reduced` query parameter currently carries "this entry point has
  views" -- which is inverted with respect to the flag's apparent intent
  (the login page, the one entry that needs least, asks for
  `reduced=false`).
- No server code reads the `reduced` parameter at all, so neither the
  declared option nor the inverted value has any effect today.

## Approach

Three tiers, chosen per entry point, decided at build time and served
from a pre-rendered cache. Not a per-request computation.

### Tier 0 -- none

For entry points that render one fixed page and issue no queries. The
login page is the motivating case: it needs the CSRF token and nothing
else.

Constraint worth stating up front: this tier cannot be expressed as
`config: null`, even though `QLiveBoostrap.config` is nullable today.
`init()` assigns `theConfig.csrfToken = csrfToken` only when a config is
present, and `config()` throws when it is absent -- so a null config
takes the CSRF token with it, and `login.tsx` reads exactly that. Tier 0
is therefore a real `QLiveConfig` with `contextPath` and `csrfToken`, an
empty `schema.types`, and empty `meta`.

Signal: `noSchema()` present in the entry module's track-usage data.

### Tier 1 -- derived

The default we would like to reach. The payload carries the closure of
types reachable from the queries that entry point actually declares,
plus the fixed floor described below.

Signal: neither `noSchema()` nor an explicit opt-out; the entry point's
tracked `GraphQLQuery` declarations are the seed set.

### Tier 2 -- full

Today's behavior, kept as an explicit opt-out, because some consumers
legitimately want the whole domain. `DomainTables` is the canonical one:
it is a domain browser, so its working set *is* the schema. It also
scans linearly (`schema.types.find(...)` per lookup), which is another
reason it is a tier-2 citizen rather than something to make work
partially.

Signal: an explicit marker in the entry module, symmetric with
`noSchema()` -- `fullSchema()`, tracked the same way.

## Computing the slice

Seed set, per entry point: every type named as a root field result or
variable type across that entry's tracked queries. `follow()` already
computes this while generating result types; the change is to also
collect the visited type names rather than discarding them.

Closure over the seed set:

- field types of included OBJECT types, transitively, unwrapped through
  NON_NULL and LIST
- INPUT_OBJECT types reachable from included query variables,
  transitively
- ENUM and SCALAR types referenced by anything included
- generic type entries from `meta.genericTypes` whose `type` is included
  -- `queryDocumentTypes` is derived from these, and converter
  registration depends on it
- `meta.types` entries for included types, since `nameFields` and the
  per-field meta are keyed by type name
- relations from `meta.relations` where *both* `sourceType` and
  `targetType` are included

The relation rule is the one genuine judgment call. Including relations
that dangle out of the slice would drag their target types back in and
defeat the exercise; excluding them means a partial slice cannot answer
"what relations does this type have" completely. Restricting to
both-ends-included keeps the slice closed and makes the incompleteness
explicit rather than silent -- a tier-1 client that needs a relation it
was not given gets a missing-type error, not a wrong answer.

Fixed floor, always included regardless of seeds: the scalar set, the
`QueryConfig` / `QueryDocument` machinery types, and anything else the
runtime dereferences without an application query naming it. This list
should be derived from what qlive-ts itself looks up, not maintained by
hand -- otherwise it rots the first time the runtime grows a lookup.

## Where it runs

The slice is a pure function of (schema, tracked queries), and both
inputs are fixed at server start. So compute it at start, not per
request, and keep the existing `JSONHolder` shape:

- today: one holder, built in the constructor
- proposed: a small map of holders keyed by entry point, built lazily on
  first request for that entry point and cached for the process lifetime

That preserves the property the current code has and the user called out
as the main saving -- generate once, splice thereafter -- while making
the number of distinct payloads equal to the number of entry points
rather than one. For a typical application that is two or three.

`provideConfig()` already receives `path`; resolving path to entry point
is the same routing question `ViteIndexController` answers to pick a
template, so the input is available without a new mechanism.

In dev, track-usage arrives live over `/_dev/track-usage` and changes as
the developer edits. The cache therefore has to be invalidated on the
same signal `GraphQLQueryTypingService` already debounces on, or dev has
to stay pinned to tier 2. Pinning dev to tier 2 is the safer default and
should be the starting point: it removes a whole class of "works in dev,
missing type in prod" confusion, at the cost of not exercising the
slicing in the inner loop.

That cost is real and worth naming: a tier mismatch between dev and prod
means the failure mode of a too-narrow slice shows up only in a built
application. Whatever else this design does, it needs a way to run the
production tiering locally on demand.

## Failing well

A partial schema is only safe if every lookup that misses says so
loudly. `findType()` in `type-utils.ts` is the single funnel for type
lookups (`typesByName.get`), which is the right shape already -- it
needs to throw a message that names the tier, the entry point, and the
remedy, rather than returning undefined and letting a downstream
destructure fail somewhere unrelated:

    Type "InvoiceLine" is not in this page's schema slice (tier 1,
    entry point "app"). It is not reachable from any query declared
    in this entry point. Call fullSchema() in the entry module, or
    declare a query that selects it.

`DomainTables`' direct `schema.types.find(...)` bypasses that funnel and
would fail with a less useful error. Routing it through `findType()` is
worth doing regardless of whether tiering ever ships.

## Ergonomics

The framework user should not have to think about any of this until
their domain is large enough to care, and then should be able to fix a
problem from the error message alone.

- Doing nothing gets you a correct application. Whether the default is
  tier 1 or tier 2 is the open question below; either way, wrong is not
  an option the default can produce.
- Both markers are one call in the entry module, statically analysable,
  with no configuration file and no build wiring for the application to
  maintain. This matches what `noSchema()` already is.
- The failure mode is a named error with a named fix, not a missing
  field five frames away.

## Open items (not decided)

- **Whether tier 1 or tier 2 is the default.** Tier 2 is safe and is
  today's behavior; tier 1 is the whole point but can only be the
  default if the closure rules are trusted to be complete. Suggested
  path: build tier 1 as opt-in, run it against qlive-test and one large
  synthetic domain, and only flip the default once the fixed floor is
  derived rather than hand-maintained.
- **Whether the tier boundary is the entry point or the view.** Entry
  point is what track-usage cleanly identifies and what the bootstrap is
  keyed by. Per-view slicing would be finer, but views are loaded lazily
  as separate chunks after the bootstrap has already been parsed, so it
  would need an incremental schema-fetch protocol -- a much bigger
  design, and one that reintroduces per-request work.
- **Whether `meta` should be sliced at all in the first cut.** It is a
  sizable minority of the payload -- roughly a fifth, at the scale
  described above. Slicing the schema alone is simpler and captures most
  of the win; `meta.relations` is the part with the awkward closure rule
  above.
- **Fixing `StartupOptions.reduced`.** It is currently declared,
  undocumented in behavior, unread, and passed an inverted value. It
  should either become the client-side expression of these tiers or be
  removed; leaving a dead flag that looks like this feature is worse
  than either.
- **Whether tier 0 should skip the bootstrap fetch entirely in dev.**
  The login page currently retries `/api/bootstrap` for a payload it
  barely uses. Out of scope here, but it is the same question wearing a
  different hat.
