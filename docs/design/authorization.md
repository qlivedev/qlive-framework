# Authorization (design sketch)

Status: sketched, unsettled, parked as a separate issue. Written
2026-10-17, revised 2026-10-19. Not built.

How QLive handles authorization when every application has a different
scheme -- org charts, deputies, regions, project membership -- without
modeling any of them.

## The framework asks, the application answers

QLive doesn't model authorization schemes. It asks questions whose
answers it doesn't need to understand, and the application answers them
by implementing **QLive's own permission evaluator interface** as a
Spring bean. The security context the client gets comes from a provider
interface, implemented the same way, like the layout providers
(`layouts.md`).

Spring Security stays responsible for what it does anyway:
authentication and sessions. Its `PermissionEvaluator` is not the seam:
enforcement happens inside QLive's services, which call QLive's own
interface.

## Enforcement inside QLive's services

The server enforces where data leaves or enters, inside QLive's own
services:

- **Reads, in `QueryDocumentService`**: the application's answer as a
  condition, added to the query.
- **Writes, in `MergeService`**: a decision per row and operation.
- **Actions, where the framework stands between GraphQL and the action
  method**, which loads the subjects and checks the rules before calling
  it.

Every path goes through these services, which makes "no path forgets to
check" testable at a few known places.

## The security context on the client

**The client gets data, not answers.** A security context, defined by
the application, reaches the client in the cached part of the bootstrap
and holds what client code needs to answer questions up front: show an
edit button, make a field editable. The simplest context is what the
bootstrap already delivers in `authentication`: login, roles and id. An
application with a richer scheme adds to it, such as a region or a cost
center.

The client acts on the context and never decides. Whatever it concludes
is advisory; the server enforces regardless. The context holds only what
the user may know about themselves, never the org chart behind it.

## Forced re-login

**Any change to authentication or authorization requires a new login.**
A security context is valid for exactly one session, and the client
never deals with it going stale.

On the server, `invalidateSessionsOf(user, { revokeRememberMe })`
covers:

- HTTP sessions, found by principal through Spring's `SessionRegistry`
  and ended with `expireNow()`
- remember-me tokens in `app_login`, optionally: a changed role is fine
  with a fresh remember-me login, a revoked user is not
- push connections: one connection-scoped notice, then the socket
  closes

On the client, the server's decision makes the user's unsaved data
irrelevant to the server, not to the user, who may disagree. Rescuing it
stays possible, and is safe, because rescued data goes through the
merge or an action and is authorized there like any other.

- **An authentication failure is an event before it is a redirect.** An
  application handler can park dirty working sets, tell the user their
  changes are kept, or offer an export. Without a handler it is a plain
  redirect, after the event.
- **Forced invalidation is not logout.** `clearStashes()` on a voluntary
  logout protects a shared machine; after a forced one the stash stays.
  The push notice lets the rescue happen before the user tries to save.
- **A stash records its user.** `resume()` offers only the current
  user's stashes and leaves others untouched.


## Before building it

**Building authorization starts with a serious analysis of every
aspect of it**, not with the enforcement points above. Other designs
keep adding paths that data takes out of the server, or ways a client
can learn about data it may not see, and each of them has to be
checked against this sketch before any of it is built. Collected so
far:

- **The related-rows endpoint** (`layouts.md`, "Related rows"): a REST
  endpoint serving any non-catalog type a foreign key references,
  outside GraphQL, with a condition from the client. The condition can
  only narrow which rows come back, but it can test fields the user may
  not see -- `salary > 100000` reveals salaries through which rows
  return.
- **Catalog types in the bootstrap**: their rows reach every client that
  loads the bootstrap and are filtered in the browser, so a catalog is
  visible to everyone who can see a form using it.
- **The cached part of the bootstrap** in general: catalogs, layouts and
  action definitions are the same for every user.
- **Conditions evaluated in the browser** over rows it already holds,
  which assume the rows were filtered before they arrived.
- **Action mutations** (`actions.md`): a subject argument names rows by
  id and the framework loads them, so what an action reads is decided
  by ids the client sends, not by a query the read path filters.
- **Push payloads**, see the open item below.
- **Locked field modes** (`layouts.md`, "Field modes and editability"):
  an authorization rule locks a form field's mode so no other rule
  changes it. How it locks and in which mode is open, and a mode on the
  client is presentation, not enforcement.

## Open items

- **Push**: whether filtering which subscribers may see a published row
  is a fourth enforcement point. Pub/sub sends rows outside
  `QueryDocumentService`.
- **The permission evaluator's shape**: one interface with a condition
  form for reads and a decision form for writes and actions, or one per
  enforcement point.
- **One source for both sides**: whether the server's enforcement reads
  the same application-defined object the client gets serialized, so
  that a condition like "region = the user's region" reads the same on
  both sides, possibly in the condition model with the context as a
  source of values.
- **Field-level authorization**: "may see the row, not the salary" is a
  field mask, not a row condition. `fieldMask.ts` exists for merges.
- **The client type of the context**, through `client-types.md`.
