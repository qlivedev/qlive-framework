---
name: audit-qlive-ts-exports
description: Audit the public API of the qlive-ts package - find types and values that reach an application's code without being exported from src/index.ts, decide export vs. internal for each, and keep index.ts split into a values section and a types section. Use when asked to check, fix, or review qlive-ts exports, its public API surface, or its index barrel.
---

# Auditing the qlive-ts public API

`qlive-ts` is a framework: every gap in its barrel file is a gap an application
runs into. The failure mode is quiet. tsdown bundles the declarations, so a type
that `src/index.ts` never re-exported is still *inlined* into `dist/index.d.ts`
and still typechecks at the call site -- the application just cannot name it, so
it cannot declare a variable, write a helper signature, or annotate a prop.
Nothing errors; the user writes `any` and moves on.

## The check

```bash
cd qlive-ts && npx tsdown && pnpm check-exports
```

`tooling/checkExports.mjs` reads `dist/index.d.ts`, lists every top-level
declaration in it, and subtracts

- everything the file's own `export { ... }` lists name (including the ones
  inside a bundled namespace, e.g. `FilterDSL`), and
- every identifier mentioned in the `DELIBERATELY NOT EXPORTED` section at the
  end of `src/index.ts`, which is the allow list.

Whatever is left is unaccounted for. Exit code 1 means there is something to
decide. The build must run first -- the script reads the build output, not the
sources.

## Deciding on each finding

For every name the check reports, pick one and act on it. There is no third
option: leaving it undecided is what produced the finding.

**Export it** when an application legitimately holds a value of that type or
calls that function. Signature types are the common case -- a parameter type, a
return type, a member of an exported type, the base of an exported intersection.
If a public function takes it or hands it back, it is public.

**Keep it internal** when it is one of:

- *Lifecycle* -- `startup()` calls it, in an order that matters. Calling it by
  hand leaves the framework half-initialized.
- *Plumbing behind a public entry point* -- a raw `graphql()` fetch that skips
  value conversion, when `GraphQLQuery.execute()` is the supported path.
- *Machinery* -- constants and mapped-type helpers that generate an exported
  type. The generated type is what an application names.
- *A local alias* whose name would be too generic at package scope.
- *Not API yet* -- a stub, or a shape still free to change.

Then add it to the `DELIBERATELY NOT EXPORTED` section with the reason. That
section is the point of the whole exercise: it is what makes the next run of this
audit a short one, and it is what tells the next reader that an absence was a
decision rather than an oversight.

When a type genuinely belongs to the public API but its module never marked it
`export`, add the `export` there first, then re-export from `index.ts`.

## The shape of index.ts

Two sections, values first, types second, each with a banner comment:

```ts
// ---------------------------------------------------------------------------
// VALUES
// ---------------------------------------------------------------------------

export { startup } from "./startup";

// ---------------------------------------------------------------------------
// TYPESCRIPT TYPES
// ---------------------------------------------------------------------------

export type { StartupOptions } from "./startup";
```

Never mix a `type X` entry into a value export block -- a module that exports
both gets one line in each section. Group by source module, and keep the module
groups in a comparable order in both sections so the two halves read as one
listing. The `DELIBERATELY NOT EXPORTED` comment closes the file.

## Finishing

```bash
cd qlive-ts && npx tsc --noEmit && npx tsdown && pnpm check-exports
cd ../qlive-test/frontend && npx tsc --noEmit
pnpm -r test
```

`qlive-test/frontend` is the framework's own first consumer, so typechecking it
is part of the audit, not an extra. Note that it sets `skipLibCheck`, which means
a broken import inside a generated `.d.ts` will not surface there.
