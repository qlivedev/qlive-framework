# Actions (design)

Status: concept settled, details open. Written 2026-10-18, revised
2026-10-19. Not built. The companion concept is `layouts.md`.

What a user does to stored data beyond editing it: approve, close,
send. Editing is the merge. An action is something the application
offers on rows that are already stored, guarded by conditions, with
input of its own.

## Problem

The search started at auto-form. The schema can't produce a form, not
even with invariants added, because what a form needs is situational:
the same type is edited differently depending on what the user is doing.
Part of that situation is fields and order, which went to `layouts.md`.
The rest is what this document covers:

- **Situational rules.** "A description is required when approving",
  "the period must still be open". They hold for one thing a user does,
  not for the type.
- **Input that isn't stored.** An approval comment, a "notify the
  customer" flag, a rejection reason. If such a field were stored, it
  would be part of the query and the merge. That it isn't is what makes
  it additional.

Formalizing the rules buys two things: the client can run the same
check, and a definition could later come from somewhere other than Java
code. Only the first is needed now.

## Merge and invariants

**Merge is persistence.** Change sets of database rows, optimistic
versions, conflicts. It knows nothing about actions, and a plain edit
screen uses nothing else.

**Invariants belong to merge** and hold whoever writes. Storing data
that satisfies them is always acceptable. That sentence is what lets
merge and action stay apart: a merge commits valid data, and an action
afterwards may still refuse to act on it.

A rule is a condition from the condition model, which already runs in
the browser (`evaluate.ts`) and in the database (jOOQ), plus the field
it blames and a message key. Invariants and action rules are both
rules in this sense.

## What an action is

- **A name.** What a request carries, what a layout refers to.
- **Roots.** The action applies to one row or to several rows of the
  same shape, the way a query document holds rows. Both are offered
  because the shape check costs the same for one or many.
- **References** into the object graph from the root: the fields the
  action's rules read, and nothing else. They fix which entities take
  part and how deep. Which fields a form shows is the layout's business,
  not the action's.
- **Rules**: conditions over the references and the action's input.
- **Input**: fields that are not stored, as a nested graph of single
  values and arrays. Whether an array relates to the roots, and how, is
  the application's business. Its shape comes from the definition or
  from the implementation (see "Implementations").
- **An implementation** doing the action's work on the server.
- **A cleanup hook**, optional: application code for when the action
  refuses stored state (see "Failure").
- **Widget overrides**, the per-action level of widget customization
  (`layouts.md`, "Widgets").

### Rules that mix stored state and input

Stored state and input are addressed differently. A path into stored
state is relational: a to-many path asks about some or all related rows,
and rows have no position unless a query sorts them. Input is an
in-memory graph addressed positionally: `bars.0.name`.
`client-types.md` already notes the same split between query conditions
and subscription payloads.

A rule may mix both. When a rule is transformed to jOOQ, every reference
to input is resolved to a value first, so the database sees a bind
parameter and never a positional path.

- A reference resolving to one value becomes that value, bound as
  Svenson produced it. There is no coercion to the column's type for
  now; whether one is needed is left to experience.
- An array resolves to a list of values, which is valid only where the
  condition model already allows a list: as the operand of `in`. A
  variadic `concat` over a list is the second candidate, for building a
  value to compare against, not for display. It would change `concat`
  in every evaluator, which today all take exactly two operands.

In the browser the distinction doesn't exist: everything, drafts
included, is evaluated in memory.

## Issuing an action

**An action is not a GraphQL operation.** Queries are GraphQL methods
because their annotations enumerate them in the domain and the framework
intercepts them. An action's definition already tells the framework
everything, and the input is nested, so expressing it in GraphQL would
take an input type and a DomainObject per action, describing again what
the definition describes. Actions go through **one REST endpoint**, and
the framework invokes the implementation itself.

The client calls an async function with the action's name, the root ids,
the versions of the rows the references reach as the user saw them, and
the input. All input must be filled before issuing, whether a value came
from an input, a button or the view's code.

On the server the framework opens **one transaction on committed data**
and, inside it:

1. Checks the versions. A stale one fails the action with a conflict, so
   the action decides on the state the user saw, never on what somebody
   else wrote in between. This is needed with or without a preceding
   merge: approving without editing has the same race.
2. Evaluates the rules against the database.
3. Runs the implementation.

The implementation's result, a POJO that serializes to JSON, is what the
client's async function resolves to.

**The action is the unit.** All roots succeed or fail together.

**Merge and action are separate requests.** A screen that edits and then
approves saves first and acts second. The edits are valid on their own,
since they satisfy the invariants, and stay stored when the action
fails. Working on committed state also leaves an action auditable,
though auditing is not part of this design.

### Failure

- **The action fails during its work.** Its transaction rolls back and
  there is nothing to clean up.
- **The action refuses the stored state.** The refusal is an exception
  and rolls the transaction back as well. But the merge that produced
  that state committed earlier, so only the application can decide what
  to do with it. The cleanup hook runs after the rollback, in a
  transaction of its own, and does whatever this action needs. The
  framework provides the slot, nothing more.

Since the second case follows a normal save, the client handles it as a
normal outcome, not as an exception.

### The early check on the client

The shared condition model makes it cheap to offer evaluating an
action's rules on the drafts before anything is saved. It is an offer,
not part of the design: everything above is correct without it.

## Implementations

**An implementation is a Spring bean implementing `Action<E, R>`**, with
`E` the input and `R` the result. The framework goes through the defined
actions and asks the implementations which one handles each.

**A missing implementation** is an error at startup in production. In
dev, the action gets a stand-in that logs that it was invoked, with the
data it was invoked with.

**The input reaches the implementation as `E`**, a plain POJO. The
input is not part of the GraphQL domain and not backed by the database,
but its fields are still scalar-typed: a form editing it picks widgets
and converts values by scalar, and a `long` sent as a JSON number is
rounded by the browser before any converter sees it. **Each field has
the scalar the schema says it has**, the same as a domain field, and
both directions go through that scalar's coercing. The endpoint parses
the body into a `Map`/`List` graph and recasts it into `E`, the way
`PayloadRecast` does for pub/sub, converting each field with its
scalar's coercing on the way; the result is serialized the same way. An
implementation that handles several actions declares `E` and `R` as
`Map` and gets the graph with the field values coerced.

**The input's shape** comes from the definition, or from `E` when the
definition leaves it out. When both describe it, they must match, and a
mismatch fails at startup. When neither does -- the definition is silent
and `E` is only a `Map` -- that is a startup error too, since there is no
shape to check or type.

## Client types

Both type parameters are available at runtime, which makes actions a
second registry for `client-types.md`, next to pub/sub's channels: a map
from action name to `E` and `R`. The emitter turns the classes into
TypeScript types, mapping each field's scalar as for the domain types,
and the generated map types the async function by name:

```ts
export interface Actions {
    approveBar: { input: ApproveBarInput, result: ApproveBarResult }
    closePeriod: { input: ClosePeriodInput, result: void }
}
```

A misspelled action name is a compile error, and the result is typed
without the caller naming it.

## Actions, queries and layouts

**An action names no query**, and no query names an action. They meet in
the view. The connection is the data: the client has to send the
versions the rules depend on, and the early check needs the referenced
values, so the query document a view passes must contain what the
references reach. A runtime shape check says whether it does. The
server doesn't need the check; its rules read the database.

An action can serve several views, and several queries can satisfy it.

## The model and its providers

**The action model is a set of Java classes**, rooted in `ActionModel`
in `io.github.qlivedev.model.action`, built by a fluent builder in Java,
type-safe through jOOQ references. The implementation interface is `Action<E, R>`: the most natural name
goes to what application code implements.

**Providers are Spring beans** implementing an `ActionProvider`
interface. The builder is the one implementation planned. Definitions
from elsewhere, an editor or files, would be another implementation of
the same interface; none is planned, since the developers who write the
server code are the ones defining actions. The framework injects all
providers as a list, merges what they provide, and fails at startup on a
name defined twice.

**The model holds no functions.** A code rule or a cleanup hook is
referenced by name and registered under it. The model reaches the
browser, which can only evaluate rules that are data, and any future
provider would produce the same classes.

**Everything is built and validated at Spring context startup**,
against the domain: an unknown type, field or relation fails.

**Actions are constant for an application's run** and reach the client
in the cached part of the bootstrap. They are not part of DomainMeta,
which describes the type model.

## Naming

**Action**, and "QLive actions" in prose where React's meaning could
interfere: `<form action={...}>`, `useActionState`. A hook named
`useAction` would sit right next to `useActionState` and is best
avoided.

## Considered and set aside

- **A use case bound to the working set**, carrying an entity shape,
  editability and rules, checked in the merge transaction. Merge stores
  rows and nothing else, so it can't carry input that isn't stored.
- **A command wrapping the merge**, checked up front, then merge and the
  command's work in one transaction. It tied two things together that
  each stand on their own: storing valid data, and acting on stored
  data. The versions give the action the guarantee the shared
  transaction was for.
- **A GraphQL mutation per action**, with the checks running in a
  service the mutation calls inside its own transaction. It works, but
  GraphQL only gets in the way: no enumeration or interception is
  needed, and nested input would need a type per action.

## Open items

- **Generic `E`**: a mechanism like `@GraphQLTypeParam`, so that an
  implementation handling several actions gets a typed `E` per action
  instead of `Map`. Not for the first implementation.
- **The endpoint's response**: how verdicts per field, conflicts and a
  refusal are shaped, and how they reach fields on the client.
- **The cleanup mechanism**: presumably a callback after the rollback,
  which the framework registers when the action refuses.
- **Required and optional input**: whether input fields are required
  unless marked optional, so that "filled" applies to the required ones.
- **Code rules in the browser**: a TypeScript twin, or asking the
  server.
- **Authorization**: who may issue an action. The framework evaluates
  an action's rules itself; whether permissions are rules or a separate
  check is undecided.
- **The variadic `concat`**, see "Rules that mix stored state and input".
- **The client API** for holding input, checking completeness and
  issuing.
