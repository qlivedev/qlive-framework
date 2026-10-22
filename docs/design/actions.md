# Actions (design)

Status: concept settled, details open. Written 2026-10-18, revised
2026-10-22 to define actions as Java methods. Not built. The companion
concept is `layouts.md`.

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
it blames, if any, a message key and, optionally, an error class a
layout can check on. Invariants and action rules are both rules in this
sense.

## An action is a method

**An action is a Java method on a Spring bean**, the way a GraphQL
query or mutation is a method of a `@GraphQLLogic` bean. The method
says most of what an action is, and the framework reads it from there:

```java
@QLiveAction
@Rules({ DescriptionRequired.class, CommentWhenNotifying.class })
public ApproveResult approveBar(Bar bar, String comment, boolean notify)
{
    ...
}
```

- **The name** is the method's name. It is what a request carries and
  what a layout refers to.
- **The parameters** are what the action works on, of two kinds told
  apart by their type (see "Subjects and input").
- **The result** is the return type, a POJO that serializes to JSON, or
  `void`.
- **The implementation** is the method body. There is nothing to match
  a definition to, so neither a missing implementation nor two claiming
  one action can occur.
- **The rules** are named by `@Rules` on the method (see "Rules").
- **A cleanup hook**, optional: application code for when the action
  refuses stored state (see "Failure").
- **Widget overrides**, the per-action level of widget customization
  (`layouts.md`, "Widgets").

Which fields a form shows is the layout's business, not the action's.

### Subjects and input

**A subject is a parameter of a table-backed domain type**: `Bar` for
exactly one row, `List<Bar>` for any number. Subjects are the stored
state the action is about. An action may have any number of them, of
the same or of unrelated types, and none at all is valid too: an action
of input alone, "send the weekly report", is a method without a
subject.

**Every other parameter is input**: not stored, not backed by the
database. An input parameter is a scalar (`String comment`,
`boolean notify`) or a POJO, a nested graph of single values and
arrays. Whether an array relates to the subjects, and how, is the
application's business.

**Input is still scalar-typed.** A form editing it picks widgets and
converts values by scalar, and a `long` sent as a JSON number is
rounded by the browser before any converter sees it. Each field has a
scalar, found from its Java type the way a domain field's is, though
the input itself is not in the schema, and both directions go through
that scalar's coercing. The endpoint parses the body into a
`Map`/`List` graph and recasts each input parameter into its declared
type, the way `PayloadRecast` does for pub/sub, converting each field
with its scalar's coercing on the way; the result is serialized the
same way. Enum fields have no scalar to find (see "Open items").

**The parameter names are API.** They key the request and the generated
client types, and every path starts with one (see "Rules"). QLive's own
build compiles with `-parameters`; how an application's build is held
to that is open.

### Rules

**`@Rules` names the rule classes**, a `Class[]` on the method. The
class literals tie the action to its rules with the compiler's help:
renaming or moving a rule class is a refactoring, and the IDE navigates
from the action to them. Bean Validation does the same with
`@Constraint(validatedBy = ...)`, and its cross-parameter constraints
are the closest prior art for rules over a method's parameters as a
whole.

**A rule class is a factory.** It implements a QLive interface, is
public, static nested or top-level, with a no-arg constructor, and the
framework instantiates it itself. Its one method gets a context and
returns a `Rule`: a FilterDSL condition, the blamed field, the message
key and the optional error class. The context hands out the references
the condition reads, and since it knows the method's parameters, a path
naming no parameter or no field fails right where the factory asks for
it, at startup, naming the action and the rule class.

**Every path starts with a parameter name.** `bar.description`,
`bar.owner.region`, `comment`. The method's parameters are one
namespace, subjects and input alike, so any rule can read any of them.
That is why `@Rules` sits on the method rather than on a parameter: a
rule over `bar` and `comment` belongs to neither.

**A rule class can serve several actions** whose parameters share the
names it uses, such as `approveBar` and a bulk `approveBars`. Its
paths are checked against each action it is named on.

**References are not declared.** The paths the rules read are the
references: they fix which stored rows take part and how deep, the way
a query document's paths do.

### Rules that mix stored state and input

Stored state and input are addressed differently. A path into stored
state is relational: a to-many path asks about some or all related rows,
and rows have no position unless a query sorts them. Input is an
in-memory graph addressed positionally: `bars.0.name`.
`client-types.md` already notes the same split between query conditions
and subscription payloads.

A rule may mix both. A rule reading the stored fields of one subject is
evaluated as a query of that subject's rows. Every reference to input,
or to another subject, is resolved to a value first, so the database
sees a bind parameter and never a positional path.

- A reference resolving to one value becomes that value, bound as
  Svenson produced it. There is no coercion to the column's type for
  now; whether one is needed is left to experience.
- An array, or a `List` subject's field, resolves to a list of values,
  which is valid only where the condition model already allows a list:
  as the operand of `in`. A variadic `concat` over a list is the second
  candidate, for building a value to compare against, not for display.
  It would change `concat` in every evaluator, which today all take
  exactly two operands.

A rule reading the stored fields of two subjects has no single anchor,
and binding one side fails where neither is a single row: "every
`bars.num` below some `bazs.limit`". How such a rule is evaluated is
open (see "Open items").

In the browser the distinction doesn't exist: everything, drafts
included, is evaluated in memory.

## Issuing an action

**An action is not a GraphQL operation.** Queries are GraphQL methods
because their annotations enumerate them in the domain and the framework
intercepts them. An action's method already tells the framework
everything, and the input is nested, so expressing it in GraphQL would
take an input type and a DomainObject per action, describing again what
the method describes. Actions go through **one REST endpoint**, and
the framework invokes the method itself.

The client calls an async function with the action's name, and, keyed
by parameter name:

- for each subject, the id and the version of each row, as the user saw
  it. The type comes from the parameter; an unversioned type gets the id
  alone.
- the versions of rows the paths reach beyond the subjects, as far as
  the view's queries hold them (see "Versions").
- the input. All input must be filled before issuing, whether a value
  came from an input, a button or the view's code.

On the server the framework opens **one transaction on committed data**
and, inside it:

1. Loads the subjects' rows, which is also their version check: a row
   whose stored version isn't the one sent fails the action with a
   conflict, so the action decides on the state the user saw, never on
   what somebody else wrote in between. This is needed with or without
   a preceding merge: approving without editing has the same race. A
   subject row sent without its version is refused as an error.
2. Checks the versions sent for reached rows.
3. Evaluates the rules against the database.
4. Calls the method with the loaded subjects and the recast input.

The method's result is what the client's async function resolves to.

**The action is the unit.** All rows of all subjects succeed or fail
together. **The method writes through the merge**, which joins the
action's transaction rather than opening one of its own (see "A Java
API for the merge"), so a conflict during the work rolls the whole
action back.

**Merge and action are separate requests.** A screen that edits and then
approves saves first and acts second. The edits are valid on their own,
since they satisfy the invariants, and stay stored when the action
fails. A row that was never saved can't be a subject, and the user's
drafts aren't sent: the action decides on stored state. Working on
committed state also leaves an action auditable, though auditing is not
part of this design.

### Versions

**Versions are per row.** A merge gives a new version to the rows it
writes and to nothing else: editing the `AppUser` a `Bar` points to
leaves `Bar.version` as it was, and so does associating a baz with the
bar. Propagating versions to some root row, as DDD aggregates or JPA's
`OPTIMISTIC_FORCE_INCREMENT` do, fits no graph without aggregate
boundaries: one edit to a user would move the version of every bar of
theirs, and the merge's chain walk would show every open bar form a
change that touched none of its fields.

**Reached rows are enforced if sent** (decided 2026-10-22). A rule
reading `bar.owner.region` depends on the owner row too, but not every
action cares whether it changed in between, and the rules hold against
committed data either way: a race that goes unnoticed lets the action
decide on newer state than the user saw, never on state its rules
refuse. So the client sends the `(type, id, version)` of every reached
row its queries hold with a version, and the server enforces those.
Which rows a path reaches is fixed by the subjects and the path, the
way `QueryDocumentService` resolves it, so the server finds the same
rows.

**Required paths** could come later: an action naming paths whose rows
must be versioned, refusing a request that lacks them, with the
client's shape check warning the view ahead of time. That would put the
strictness in the action rather than in whatever the view's query
happens to select, and it would bring back what partial sending gives
up, that a row joining or leaving a relation since the user read it
counts as a conflict. Its default is the rule above, so adding it
breaks nothing.

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

**The browser evaluates an action's rules on the drafts** before
anything is saved (decided 2026-10-20). Usability needs it: a user
should see that an approval will be refused while still filling the
form, not after saving. Rules warn in the browser and are enforced only
on the server, and the shared condition model is what keeps them from
being written twice. The client therefore gets the action's rules with
its definition (see "The model").

**The browser can evaluate only what it holds.** A rule reading
references deeper than the form's query, or rows the form never
fetched, meets the same boundary field conditions meet on the
related-rows endpoint. Whether the browser then checks what is loaded
and leaves the rest to the server, or an action's references become
part of what a form over it loads, is open.

**Errors from either side have one shape**, GraphQL's (`layouts.md`,
"Layouts react to errors"): the rule's message, the blamed field's path
or none, and the rule's class if it has one. A layout can't tell a
warning from a refusal, and doesn't need to.

## A Java API for the merge

**The merge speaks the client's language.** A change is a type name,
an id, a version and field values as `GenericScalar`s, each naming its
scalar, because that is what a browser can send. Java code calling it
spells all of that by hand -- the tests' helpers read
`change("Bar", id, version, field("num", "Int", 99))` -- though it
already holds the Java values. An action method writing its subjects is
the first caller that isn't a test, and a scheduled job or an import
would be the next.

**A jOOQ `UpdatableRecord` already knows what the merge needs**: its
table, which the domain maps to a type; the fields that changed, which
is the field set the chain walk diffs; and the values it was read with,
the version among them. A record-level call, `store(barRecord)`, could
derive the whole change from it. A POJO can't serve: it doesn't know
which of its fields changed.

**The transaction is the caller's.** The client's merge is a unit of
its own and opens its own transaction. A Java caller -- an action, a
job, a service doing several things -- wants the merge to join its
transaction instead. That suggests two entry points over one engine, so
that version records, field sets and push events come out the same
whoever writes.

Scenarios: an action sets `status` on its subjects; an action creates a
row of another versioned type, such as an approval record; an action
deletes, or changes many-to-many links; a scheduled job writes rows with
no user and no request; tests.

Open: what a conflict means to Java code. Inside an action the
subjects' versions are checked and the transaction is repeatable read,
so a concurrent write surfaces as a serialization failure; a job might
want to retry, fail or overwrite. Also whether links are part of the
record or a call of their own.

## Client types

The method's parameter and return types are available at runtime,
which makes actions a second registry for `client-types.md`, next to
pub/sub's channels. The emitter turns the classes into TypeScript types,
mapping each field's scalar as for the domain types, and the generated
map types the async function by name:

```ts
export interface Actions {
    approveBar: {
        subjects: { bar: Subject },
        input: { comment: string, notify: boolean },
        result: ApproveResult
    }
    sendWeeklyReport: { subjects: {}, input: { week: number }, result: void }
}
```

A misspelled action or parameter name is a compile error, and the
result is typed without the caller naming it.

## Actions, queries and layouts

**An action names no query**, and no query names an action. They meet in
the view. The connection is the data: the client sends the versions it
holds of the rows the paths reach, and the early check needs the
referenced values, so the query documents a view passes should contain
what the paths reach. A runtime shape check says whether they do. The
server doesn't need the check; its rules read the database.

An action can serve several views, and several queries can satisfy it.

## The model

**The action model is a set of Java classes** in
`io.github.qlivedev.qlive.model.action`, built from the methods at startup:
name, parameters with their kinds and types, rules, result type. It is
data, which is what reaches the browser, and what another source, an
editor or files, would produce; none is planned, since the developers
who write the server code are the ones defining actions.

**A name defined twice fails at startup**, naming both methods.
Layouts went the other way: a later source replaces a layout of the
same name, as the application patches a server layout. That suits
presentation. For an action it would let the order beans load in decide
which rules the server enforces, so that importing one more
configuration could weaken an approval unnoticed. Replacing an action on
purpose, as an application tightening a module's action would, is
shelved (see "Open items").

**The model holds no functions.** A rule class runs once, at startup,
and what it returns is data; the cleanup hook is referenced by name and
registered under it. The model reaches the browser, which can only
evaluate rules that are data.

**Everything is built and validated once, at Spring context startup**,
against the domain: an unknown type, field or relation fails, as does a
path naming no parameter. Layouts are built anew after `flushCache()`;
actions are not, for three reasons. They have no locale, since their
rules carry message keys the client translates. They are bound to code:
methods are scanned and parameter types analyzed at startup, and redoing
that would move failures from startup into a running application. And
they are enforced, so reloading one changes what the server permits,
where reloading a layout changes what a form shows. The layout check
runs against the finished actions, at startup and after every
`flushCache()` alike.

**Actions are constant for an application's run** and reach the client
in the run-cached part of the bootstrap. They are not part of
DomainMeta, which describes the type model. **On the client they are
read-only**: unlike layouts, they are not put where `init` could change
them, since a changed rule would warn about something other than what
the server enforces.

## Naming

**Action**, and "QLive actions" in prose where React's meaning could
interfere: `<form action={...}>`, `useActionState`. A hook named
`useAction` would sit right next to `useActionState` and is best
avoided.

**Subject** is the working term for a parameter of stored state. "Root"
was the first, and stopped fitting once paths start at any parameter;
"target" is a relation's target type, "operand" a condition's, and
"entity" is taken by the merge (`EntityChange`).

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
- **Definitions apart from their implementation**: an `ActionModel`
  built by a fluent builder in an `ActionProvider` bean, matched to
  `Action<E, R>` beans by name, with one input class `E`. It described
  in a second place what a method signature already says, and needed
  errors for a missing implementation and for two claiming one action.
- **Rules per parameter**, `@Rules` on each subject. Once any rule can
  read any parameter, a rule on `List<Bar>` could be about `bazs`.
- **Versions propagated to a root row**, see "Versions".

## Open items

- **Rules over two subjects' stored fields**: anchored at one subject
  with the other bound, which fails where neither is a single row, or
  evaluated in memory over the rows the server loads anyway, which meets
  the positional and relational readings of a to-many path.
- **Where action methods live**: a stereotype of their own, as
  `@GraphQLLogic` is for queries, or an annotation on a method of any
  bean. `LogicBeanAnalyzer` may be reusable for the scan. The
  annotations' names are open with it.
- **Parameter names in an application's build**: requiring
  `-parameters`, an annotation naming a parameter as a fallback, or
  both.
- **Replacing an action on purpose**, shelved (2026-10-22). A module
  may ship action beans through `@Import`, and an application could
  want to tighten one of its actions; that would take a declaration
  naming the action it replaces or modifies, never the load order. No
  module ships actions yet.
- **Enums in input.** Finding a field's scalar from its Java type
  leaves out enums, which in the domain are GraphQL enum types, not
  scalars. jOOQ generates none, so an input POJO is the first place
  QLive meets a hand-written enum; `layouts.md` holds its enum example
  until this is settled. To decide before the input recast is built:
  how an enum field is converted, and which widget picks its value.
- **Forms over an action**: `layouts.md` has a form edit its entity and
  the action's input, with input paths starting at `input.`. With
  parameters as the namespace, which subject a form's row is, and how
  its paths name the input parameters.
- **The endpoint's response**: a refusal's errors are GraphQL-shaped
  (see "The early check on the client"); how a conflict and the result
  sit next to them, and how errors reach fields on the client, is open.
- **What the early check reaches**: only what the form loaded, or the
  action's paths added to what a form over it loads.
- **The cleanup mechanism**: presumably a callback after the rollback,
  which the framework registers when the action refuses, and how the
  method names its hook.
- **Required and optional input**: whether input parameters and fields
  are required unless marked optional, so that "filled" applies to the
  required ones.
- **Authorization**: who may issue an action. The framework evaluates
  an action's rules itself; whether permissions are rules or a separate
  check is undecided.
- **The variadic `concat`**, see "Rules that mix stored state and input".
- **The client API** for holding input, checking completeness and
  issuing.
