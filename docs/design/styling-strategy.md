# Styling strategy for QLive components

Status: decided; packaging implemented, components not started. Written
2026-08-31. The build and stylesheet wiring described under "Packaging"
is live in the repo; a placeholder stylesheet with a skeleton token set
ships at `qlive-ts/src/styles/qlive.css`. No components exist yet, so the
component-level decisions below remain untested against real use.

## Problem

QLive will eventually ship UI components. Before writing the first one we
have to pick how they are styled, and that choice leaks into every
consuming application: whatever the framework's components depend on
becomes a build-system requirement for everyone who installs them.

The obvious candidates are the ones we already know (Bootstrap, which we
used in the framework's previous incarnation) and the one everyone has
moved to since (Tailwind). Neither question is quite the right one.

## Two decisions, not one

They get conflated constantly, and they have different answers:

1. **How QLive's own components are styled.** We ship these. Consumers
   install them and cannot easily change how they were authored.
2. **What application developers use for their own markup**, and what
   `qlive-test` demonstrates as the template.

Nearly all public writing about Tailwind-vs-anything answers (2). (1) is
governed by a constraint that app-level advice never has to think about.

## Decision 1: framework components use plain CSS with token variables

Component styles are authored as plain CSS, using custom properties for
every themable value, namespaced class names, and wrapped in a cascade
layer:

```css
@layer qlive {
  .qlive-field {
    border: 1px solid var(--qlive-color-border);
    border-radius: var(--qlive-radius-md);
    padding: var(--qlive-space-2) var(--qlive-space-3);
  }
}
```

Three properties matter, in this order:

- **Zero build coupling.** A consumer imports one stylesheet. It works
  whether their app uses Tailwind, Bootstrap, vanilla CSS, or nothing at
  all. No plugin to register, no config to keep in sync, no scan path to
  declare.
- **Theming without a config format.** Consumers override `--qlive-*`
  properties, in whatever scope they like, at runtime if they want. We do
  not have to design, document, and version a theme API; CSS already has
  one.
- **Overridable without `!important`.** Anything outside `@layer qlive`
  beats everything inside it regardless of specificity. A consumer's own
  rule — including a Tailwind utility dropped straight onto one of our
  components — just wins. This is the single most valuable property in
  the list and the reason the layer is not optional.

### Why not Tailwind for the shipped components

If our components carry utility classes in their markup, then every
consuming app must install Tailwind, must run a theme config compatible
with ours, and must explicitly add our package to Tailwind's source scan
(under v4, `node_modules` is skipped by default, so it needs an
`@source "../node_modules/@quinscape/qlive-ts"` or equivalent). An app
that does not use Tailwind gets our components completely unstyled.

Precompiling a utility stylesheet instead does not rescue it: we would
ship a blob that collides with the consumer's own Tailwind output on
identical class names, and ordering and specificity become a support
burden we debug on other people's behalf.

This is why no widely used React component library ships Tailwind classes
— not Radix, not React Aria Components, not Base UI, not MUI — even
though most of their users write Tailwind in application code. We should
not be the exception.

### Why not Bootstrap

Not because it is unmaintained; 5.x is alive, dropped jQuery, and has
color modes. But adopting it means adopting its markup conventions, its
class vocabulary, and its component JS, which is not React-native (you
end up on a wrapper such as react-bootstrap). It buys us a look we would
then spend effort overriding, in exchange for a dependency our consumers
inherit. The plain-CSS plan gets us the same starting point with none of
the inheritance.

### Why not CSS-in-JS

Runtime CSS-in-JS (styled-components, emotion) adds a runtime cost to
every consumer, is hostile to server rendering, and its trajectory over
the past few years points away from it. Build-time variants
(vanilla-extract, Panda) are technically fine but add a required build
step in the consumer's toolchain, which is the exact coupling we are
trying to avoid.

### CSS Modules

An acceptable authoring detail, not a different strategy. If we author
with CSS Modules, we still ship a compiled stylesheet with stable,
namespaced class names — because generated hashed names cannot be
targeted by consumers, and consumers need to be able to target our
elements. If we adopt Modules we must configure a readable, prefixed
name pattern. Plain CSS files with `qlive-` prefixes achieve the same
thing with less machinery; slight preference for that.

## Behavior and accessibility: build on headless primitives

Styling is the small half of a component library. Focus management, focus
trapping, popover and dropdown positioning, combobox and listbox keyboard
semantics, and correct ARIA are roughly a person-year to get right and
never really finished.

We should build on an unstyled primitive layer rather than hand-rolling:

- **React Aria Components** (Adobe) — the strongest accessibility story
  available, actively developed, broad component coverage.
- **Base UI** (from people behind Radix and MUI) — reached 1.0 in 2025,
  smaller surface, very clean API.

Both are unstyled by design, so they compose with the plain-CSS decision
above rather than fighting it. Radix itself is the incumbent but its
maintenance has been uneven since it changed hands; not a first choice
for a new dependency we would carry for years.

Choosing between the two is deferred until the first real component.

## Decision 2: application code uses Tailwind, and the template shows it

For application-level markup, where a single team owns the whole surface
and there is no distribution boundary, Tailwind v4 is a good fit and we
should say so in the docs and demonstrate it in `qlive-test/frontend`.

v4 is materially different from the version we would remember: CSS-first
configuration through `@theme` instead of `tailwind.config.js`,
`@import "tailwindcss"`, a first-party `@tailwindcss/vite` plugin, and a
much faster engine.

The two decisions coexist precisely because of the cascade layer: our
components sit in `@layer qlive`, the app's utilities sit outside it and
therefore win whenever they are applied. A developer can take one of our
components and adjust its spacing with a utility class without learning
anything about how we authored it.

## Browser support policy

Baseline status below verified against MDN on 2026-08-31. Browser
versions are the first release of each engine to ship the feature.

| Feature | Chrome/Edge | Firefox | Safari | Baseline |
|---|---|---|---|---|
| Custom properties | 49 | 31 | 9.1 | Widely available |
| `:is()` / `:where()` | 88 | 78 | 14 | Widely available |
| `@layer` | 99 | 97 | 15.4 | Widely available, Mar 2022 |
| Container queries (size) | 106 | 110 | 16 | Widely available, Feb 2023 |
| `color-mix()` | 111 | 113 | 16.2 | Widely available, May 2023 |
| `:has()` | 105 | 121 | 15.4 | Widely available, Dec 2023 |
| CSS nesting | 120 | 117 | 17.2 | Widely available, Dec 2023 |
| `light-dark()` | 123 | 120 | 17.5 | **Newly available, May 2024** |
| `@property` | 85 | 128 | 16.4 | **Newly available, Jul 2024** |

**Policy: support the last two years of evergreen browsers.** The
practical floor for everything above is Chrome/Edge 123, Firefox 121,
Safari 17.5 — spring 2024. Excluding the two "newly available" rows drops
that to Chrome 120 / Firefox 121 / Safari 17.2, December 2023.

### What we use, and what we avoid

The distinction that matters is not support but whether a feature can be
compiled away for a consumer with a lower floor.

**Compilable at build time** (Lightning CSS, which Vite can use via
`css.transformer: 'lightningcss'`), therefore free to use:

- CSS nesting — pure syntax, flattens completely
- `color-mix()` with static arguments — computed to a literal color
- `oklch()` / `lab()` — falls back to sRGB

**Not compilable, ever** — runtime cascade semantics, present or absent:

- `@layer` — no polyfill is possible
- `:has()` — no polyfill is possible
- Container queries — a MutationObserver-based polyfill exists; we should
  not ship it in a framework

Given that split:

- `@layer`, custom properties, and nesting are used freely. Nesting
  compiles away, and the other two predate everything else on the list.
- `:has()` and container queries are used only where absence degrades
  gracefully. If a component visibly breaks without `:has()`, the
  component is wrong, not the browser.
- **`light-dark()` is avoided for now.** It is the most marginal item
  here, it requires `color-scheme` to be set correctly to do anything,
  and a `prefers-color-scheme` block redefining tokens gets the same
  result. That block is also what lets a consumer force a theme, which
  `light-dark()` alone does not.
- **`@property` is avoided** unless we specifically need animatable or
  type-checked custom properties. Firefox only shipped it in mid-2024.
- **Container style queries** (`style()`) are avoided. MDN still flags
  varying support across the sub-features; size queries are the safe
  part.

That leaves the token layer resting entirely on features stable since
2022.

## What this commits us to

- A published stylesheet as part of the `@quinscape/qlive-ts` package,
  and a documented import for it. See "Packaging" below — this is less
  entangled with the build question than it first appears.
- A documented, versioned set of `--qlive-*` token names. These become
  public API the moment a consumer overrides one, so they need the same
  care as a Java signature.
- Stable, namespaced class names on our elements — also public API.
- A headless primitive dependency (React Aria Components or Base UI) in
  the framework's peer dependencies.

## Packaging

The stylesheet does **not** force a build step, and the two questions
should not be argued together.

Vite processes CSS imports from a linked workspace dependency's source
directly, so `import "./Field.css"` inside `qlive-ts/src/components/`
works under the current no-build arrangement (`server.fs.allow` already
covers the repo root).

The build question is separate and prior: publishing raw `.ts`/`.tsx` to
npm requires every consumer to transpile `node_modules`, which Vite does
not do by default and which breaks non-Vite consumers entirely. The
previous incarnation solved this with tsdown (`qlive-js`), and that build
was lost in the move to this repo.

**Restored 2026-08-31**, on its own merits rather than for styling:

- `qlive-ts/tsdown.config.ts` — ESM, browser platform, dts, `clean`. Emits
  `dist/index.js` and `dist/index.d.ts` (not `.mjs`/`.d.mts`; the package
  is `"type": "module"`, so tsdown uses the plain extensions).
- The stylesheet is carried into `dist` by tsdown's `copy` option rather
  than by `@tsdown/css`. Plain CSS needs no compilation, and routing it
  through a JS entry would make Vite auto-inject it in dev while built
  consumers had to import it by hand — a dev/prod divergence.
- `qlive-test/frontend/vite.config.ts` aliases the package to its source
  in `serve` mode only, preserving the instant inner loop while
  `vite build` resolves the real `dist`. Verified by hiding `dist` and
  confirming the test suite still passes.
- `qlive-test/pom.xml` gained a `pnpm-build-qlive-ts` execution, declared
  before `pnpm-build`, running from the repo root so the reactor builds
  `qlive-ts` before the frontend resolves it.

Note that tsdown's dts generation does not full-typecheck. `tsc --noEmit`
on qlive-ts still reports 11 pre-existing errors that the build ignores
entirely (down from 39; `FilterDSL.ts` and `decompileFilter.ts` are now
clean, the rest sit in `QueryDocument.ts`, `graphql.ts`, `GraphQLQuery.ts`
and `startup.ts`). Do not read a green build as a green typecheck; there
is no typecheck in the build chain at all.

What CSS does add, once a build exists, is a small set of packaging
decisions:

- **Emit a real `.css` file; do not let the bundler inline styles into
  JS.** Runtime `<style>` injection costs us control over layer ordering,
  causes FOUC, and complicates CSP for consumers.
- **Declare `sideEffects: ["*.css"]` in `package.json`.** A blanket
  `sideEffects: false` makes bundlers silently drop `import "./Field.css"`
  side-effect imports. The array form keeps JS tree-shakeable while
  preserving CSS. Set this from the start whichever distribution shape we
  choose below; it costs nothing and keeps the option open.
- **Expose it through `exports`**, e.g.
  `"./styles.css": "./dist/qlive.css"`, so the import specifier stays
  stable whether or not the file's location changes later.

A preprocessor or CSS Modules would make a build genuinely mandatory,
since consumers cannot be expected to handle either. The plain-CSS
decision above means neither applies.

### One stylesheet or per-component CSS

These are not in tension with tree-shaking, which was an early misreading
of the `sideEffects` problem. With `sideEffects: ["*.css"]`, per-component
side-effect imports tree-shake correctly: a component the consumer never
imports takes its CSS with it. Both shapes are viable.

What actually argues against fragmented CSS here is the cascade layer:

- **Tokens must land before anything that consumes them.** Riding them
  along with each component duplicates them; putting them in one module
  requires that module to be guaranteed-first, which the module graph
  does not promise.
- **Sub-layer order is established by first occurrence.** If we ever want
  `@layer qlive.base, qlive.components`, that declaration has to be
  emitted before any fragment, or ordering ends up determined by whichever
  component the consumer imported first — a bug that appears and
  disappears with their import order.

Both are solvable the conventional way: an explicit `base.css` (tokens
plus layer declaration) imported once by the consumer, with per-component
CSS arriving via side-effect imports.

**Decision: aggregate stylesheet first; revisit with a measurement.**
Component CSS is small and compresses very well — lots of repeated
property names and token references. The fragmented shape buys an
unmeasured saving in exchange for a guaranteed-first import, ordering
subtleties, and a failure mode that only surfaces in consumers with
unusual bundler configuration. The aggregate gives one import line, one
predictable rule order, one file a consumer can read to see what is
overridable, and independent caching — which is the axis this project
optimises for.

Once the component count is real, measure the aggregate's brotli size and
split if it turns out to matter. Aggregate to per-component is a
documented import change, not an architectural rewrite.

## Open questions

- **Are QLive's components neutral primitives or an opinionated design
  system?** This design assumes primitives: a form field that binds to
  the GraphQL layer and looks like nothing in particular. If we later
  want a Quinscape house style, the architecture is unchanged — it
  becomes a second, optional theme stylesheet layered over the tokens,
  which consumers can decline to import.
- **Dark mode as a first-class concern or not.** Affects how the token
  set is structured, and is much cheaper to decide before the tokens
  exist than after.
- **shadcn-style copy-in as an alternative for some components.** Our
  `qlive-test`-as-template model fits it well: the framework would ship
  only the headless data-bound logic, and the template would carry
  presentational components as app source the developer owns outright.
  It removes the "how do I restyle this" question entirely, at the cost
  of apps not picking up component fixes by version bump. Worth keeping
  in reserve for components that are inherently app-specific (page
  shells, tables) rather than as the whole strategy.
