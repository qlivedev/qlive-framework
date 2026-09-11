# Styling

Status: describes the repo as of 2026-09-05.

How `@quinscape/qlive-ts` ships CSS, and what an application that
installs it gets. No components exist yet, so what follows is the
packaging path and the conventions the first component will be written
against, not a component library.

## What ships

One stylesheet, `qlive-ts/src/styles/qlive.css`, built into
`qlive-ts/dist/qlive.css`. It holds, in this order:

- the `@layer qlive` declaration
- the `--qlive-*` custom properties on `:root`
- a `prefers-color-scheme: dark` block redefining the color tokens
- rules for the classes QLive currently paints

Everything in the file is inside `@layer qlive`.

## How an application gets it

The stylesheet is not imported by `src/index.ts`. The application
imports it by hand, through a named export:

```ts
import "@quinscape/qlive-ts/styles.css";
```

`qlive-test/frontend/src/main.tsx` does this before its own
`./style.css`, which is the recommended order — see "Overriding" below.

Three pieces of packaging make that work:

- **`exports`** maps `"./styles.css"` to `"./dist/qlive.css"`, so the
  specifier an application writes stays stable if the file moves inside
  the package.
- **`sideEffects: ["*.css"]`** in `qlive-ts/package.json`. A blanket
  `sideEffects: false` lets bundlers drop side-effect-only CSS imports;
  the array form keeps the JS tree-shakeable while preserving them.
- **tsdown's `copy` option** carries the file into `dist` unchanged,
  rather than routing it through a JS entry point. Plain CSS needs no
  compilation, and importing it from `index.ts` would make Vite
  auto-inject it in dev while consumers of the built package still had
  to import it by hand — a dev/prod divergence.

In `qlive-test/frontend`, `vite.config.ts` aliases both
`@quinscape/qlive-ts` and `@quinscape/qlive-ts/styles.css` to their
sources in serve mode, so editing the stylesheet shows up without a
rebuild. Only `vite build` resolves `dist`.

## Plain CSS, no build coupling

The stylesheet is plain CSS: no preprocessor, no CSS Modules, no
utility framework. A consumer imports one file and it works whether
their application uses Tailwind, Bootstrap, plain CSS, or nothing —
there is no plugin to register, no config to keep in sync, and no scan
path to declare.

This is a constraint on what QLive itself ships, not advice for
application code. An application is free to use whatever it likes; the
cascade layer below is what keeps the two from fighting.

## The cascade layer

`@layer qlive;` is declared on its own line before anything is defined
in it. First occurrence fixes a layer's position in the cascade, so
declaring it up front keeps that position independent of the order in
which anything else gets imported later.

Unlayered rules beat layered ones regardless of specificity. So any
rule an application writes outside the layer wins over anything QLive
ships — including a single utility class dropped onto a QLive
component, with no `!important` and nothing to learn about how the
component was authored. That is the reason the layer is not optional.

The practical consequence for an application: import
`@quinscape/qlive-ts/styles.css` before your own CSS, and your own CSS
does not need to be layered at all.

## Tokens

The `--qlive-*` custom properties are the theming API. An application
overrides them in its own CSS, at whatever scope it likes, including at
runtime. There is no theme config format to learn.

Currently defined: `--qlive-color-{bg,fg,muted,border,accent}`,
`--qlive-space-{1,2,3}`, `--qlive-radius-md`, `--qlive-font-sans`, and
`--qlive-font-size{,-sm,-lg}`. The palette is a skeleton chosen to be
inoffensive, not a designed one.

These names are public API. Renaming one is a breaking change for every
application that overrides it, and so are the class names QLive paints.

Dark mode is a `prefers-color-scheme: dark` block redefining the color
tokens, rather than `light-dark()`. The media query is far older, and
redefining tokens under a selector is also what lets an application
force a theme instead of following the OS.

## What the stylesheet currently paints

Two groups, neither of them framework components:

- `.qlive-placeholder`, used by
  `qlive-test/frontend/src/component/TestComponent.tsx`. It exists so
  the whole path — source, build, `dist`, consumer import, rendered
  page — is visibly doing something end to end. It goes away with the
  first real component.
- `.domain-type`, `.search-bar`, `.btn`, `.arrow-path` and the
  `#domain-types-wrapper` / `#domain-types-container` ids, used by
  `qlive-test/frontend/src/component/ViteDevHome.tsx`, the schema
  browser served at `/app/`. These are not `qlive-`-prefixed and are
  not part of the package's public surface; they live here because the
  screen they style predates any decision about where application-side
  CSS belongs.

## Browser features in use

`@layer`, custom properties and `prefers-color-scheme` are the only
non-trivial features the stylesheet depends on, and all three have been
in every evergreen browser since early 2022.

The distinction that governs what may be added is not whether a feature
is supported but whether it can be compiled away for a consumer on an
older floor. Nesting, `color-mix()` with static arguments and `oklch()`
all flatten at build time and cost a consumer nothing. `@layer`,
`:has()` and container queries are runtime cascade semantics with no
possible polyfill — the layer is load-bearing and stays, so anything
else in that group should only be used where its absence degrades
gracefully.
