# Open questions

Questions left open by the designs in `done/`: what those designs built
works, and these are what they did not settle. Collected 2026-10-04,
when the executed designs moved to `done/`.

Each entry names where it was raised; the reasoning is there. A design
that is not built yet keeps its open items itself. When one of these is
settled, the entry goes, and the decision lands where it belongs: in
the code, in the design it changes, or in a new design.

## Designed, not built

Not questions: parts of executed designs that were worked out and never
built, because no build order listed them.

- **Selection in the grid.** `useSelection()` and `<RowSelector/>`, a set
  of ids the view owns. Select-all for the page, and whether a selection
  survives paging, are open on top of it.
  `done/datagrid.md`, "Rows" and step 4.
- **The unload guard.** A `beforeunload` listener while a working set is
  dirty and only then, `guardUnload: false` to opt out, `dispose()`.
  `done/working-set-merge.md`, "Leaving the page".
- **Parking a change set.** `park()`, `resume()`, `stashesFor(route)`,
  a pluggable `WorkingSetStore`, `clearStashes()` on logout. The stash
  keeps its base version.
  `done/working-set-merge.md`, "Parking a change set".

## In-app navigation

- **A `Link` component and client-side navigation.** Three designs lean
  on it and none designs it. The working set registers its dirty state
  as a navigation guard for `navigate()` to ask, with
  `config().confirmNavigation` as the prompt. Route-keyed injection ids
  let a new route's injections sit next to the old ones, replaced per
  route on navigation. `/api/update` and `initData()` are the data half
  and exist. Its own design, not written.
  `done/working-set-merge.md`, "Leaving the page";
  `done/fixture-scope.md`, "Direction".

## Writing

From `done/working-set-merge.md`, "Open items", unless noted.

- **Validation.** Nothing produces validation errors. The merge leaves
  it to the application's own mutations, and the grid's invalid cells
  (`done/datagrid.md`) wait on it.
- **Whether the accessor travels by prop or by context.** A
  `MergeScope` provider is either the first piece of a form library or
  a context with one consumer. The one edit view so far passes the
  accessor one level and wants nothing more.
- **Whether the framework ships the parked-work listing**, once parking
  exists. A list is a page, like `DomainTables`, so this is a weaker
  "no" than the one about a conflict dialog.
- **Auto-stashing on unload.** Rejected for now, ghost data on a shared
  machine against a safe exit. Parking stays explicit.
- **Group fields**, which change together, for composite and JSONB
  values. Waits on a JSONB column edited field-wise.

## Querying

From `done/query-document-service.md`.

- **Row-level security.** Policies attached through domain metadata are
  the intended direction, a separate design, not written. The query
  service deliberately has no hook that would pre-empt it.
- **`MULTISET` instead of follow-up queries** for to-many relations.
  Deferred, not rejected; the plan tree is the seam.
- **Same-row semantics** for several conditions on one to-many path.
  Today each comparison gets its own `EXISTS`. The other reading needs
  its own syntax if it is ever wanted.

## The condition model

Three evaluators exist: jOOQ for the database, `runtime/filter` for
push payloads, `evaluate.ts` in the browser. What
`done/working-set-merge.md`, "The condition model", wanted around them
is not there yet.

- **Per-scalar equality for custom scalars.** Equality and ordering per
  scalar type, `scalarEqual(type, a, b)`, so an application's own scalar
  compares the same in every evaluator. `Converter.compare` covers
  ordering in the browser. Registering a custom scalar at all may first
  need an api-level type, since the scalar machinery is internal to
  `qlive-graphql` (`done/dependency-consolidation.md`, "Three modules").
- **Two dialects of one DSL.** A payload condition reads a path through
  a to-many relation positionally (`bazLinks.0.baz.name`); the database
  and the browser read it as "some element". Deliberate, recorded only
  in `PropertyPath`'s javadoc, and nothing tells a framework user yet.
  `done/push.md`, "Open items"; carried in `client-types.md`.
- **A shared fixture suite**: `(condition, object, expected)` cases that
  all three evaluators run, to define the standard rather than describe
  it.
- **The operator vocabulary as data.** `FilterOperators` is a whitelist
  and `FilterDSL.ts` carries names and arities. A runtime filter editor
  for end users would need to enumerate operators by scalar type.

## Push

From `done/push.md`, "Open items" and step 9.

- **The manual two-tab check** of `/bar/edit` and `/bar/live`. It needs
  a real login, and nothing records that it was done.
- **Which channels a client may publish to.** Refused today. Any answer
  is a per-channel declaration at `register()`, closed by default, that
  validates the payload and stamps the sender from the connection.
- **Presence**, "somebody else has this row open". Its own design. The
  expectation is a `ClientMessage` kind and `PushMessageHandler` of its
  own, not a client publish.
- **Rows patched in place.** A document only displaying rows learns that
  they changed, not to what. Patching needs values on the wire and a
  generic "rows changed from outside" seam on `QueryDocument`.
- **A helper for database-backed payloads**, gathering a row's
  relations so a condition can filter on them, or one-off code per
  publisher.
- **Connection-scoped notices**, with no channel behind them: the
  session is about to expire, the server is draining. `Recipient.send`
  can carry one and nothing sends one yet.

## The grid

From `done/datagrid.md`, "Open" and step 6.

- **A picker for large target tables**: its own search document and a
  dialog. Waits until a view needs one.
- **Whether `useFilters()` should serve a search form** that isn't a
  grid. A form owns a condition component too, with fields instead of
  columns.
- **Export.** It belongs to the document or the server; the grid only
  triggers it.

## Fixtures

- **One fixture per route on a page.** Two views on one route clash,
  and two on one fixture share documents. A store per scope would lift
  that. `done/fixture-scope.md`, "Limits".
- **Dead links.** A view on a fixture that links to other views links to
  pages that aren't there. `done/fixture-mode.md`, "The view's route".
- **Recording a view's own `graphql()` calls**, so fixture mode answers
  them instead of rejecting. `testing-api.md` proposes a test server
  for the same need. `done/fixture-mode.md`, "Recorder".

## Build and namespace

- **The groupId.** `io.github.qlivedev` is set throughout the build.
  Whether a domain-based groupId follows is open, and whether the
  namespace is verified with Sonatype is not recorded in the
  repository. `done/dependency-consolidation.md`, "Step 3".
- **`registerInput()` has no collision check.** `register()` refuses two
  unrelated classes under one name; the input side is a known gap,
  since no clash reaching only it has been constructed.
  `done/domainql-facade.md`, "Two registered classes sharing a simple
  name".
- **Whether `QLiveDomain` moves to `qlive-api`**, so the runtime could
  depend on the API alone. It would take `TypeRegistry`, `DomainMeta`
  and `TableLookup` with it. `done/domainql-facade.md`, "Placement",
  which hands it to `module-distribution.md`.
