---
title: Show a view in Storybook
description: A sketch of running views on fixtures in Storybook, untested.
sidebar:
  order: 15
---

:::caution[A sketch]
QLive doesn't use Storybook itself, and nothing on this page has been run.
The QLive parts are the ones [Test a view on a fixture](/qlive-framework/how-to/test-a-view-on-a-fixture/)
uses and qlive-doc's demos run on. The Storybook parts assume Storybook 8 or later with
the `@storybook/react-vite` framework.
:::

Storybook renders a story without `startup()` and without a server, so a
QLive view in a story has to run on a **fixture**: data recorded from the
running application. How to record one, and what it holds, is in
[Test a view on a fixture](/qlive-framework/how-to/test-a-view-on-a-fixture/#record-the-fixture).
On a fixture the view pages, sorts and filters in the browser. It can't
save anything, and it gets no push.

## Initialize QLive in the preview

The views need QLive initialized before their modules run, since a view
may call `i18n()` or read the config on import. A story file imports its
view at the top, so initialization has to happen before any story file is
loaded. The preview is the place for that: Storybook evaluates it first.

```tsx
// .storybook/preview.tsx
import type {Preview} from "@storybook/react-vite";
import {addFixture, FixtureScope, type QLiveFixture} from "@qlivedev/qlive-ts";
import "@qlivedev/qlive-ts/styles.css";
import "../src/style.css";

const fixtures = import.meta.glob<QLiveFixture>("../test/fixtures/**/*.json", {eager: true, import: "default"});
for (const fixture of Object.values(fixtures))
{
    await addFixture(fixture);
}

const preview: Preview = {
    decorators: [
        (Story, {parameters}) => parameters.fixture
            ? <FixtureScope fixture={ parameters.fixture }><Story/></FixtureScope>
            : <Story/>
    ],
};

export default preview;
```

`addFixture()` puts each fixture in next to the others. The first one
initializes QLive. Fixtures for different routes don't clash, because
injection ids carry the route of their view.

The decorator wraps every story that names a fixture in a `FixtureScope`.
Adding the fixture again changes nothing, but the scope also provides the
route the view reads its injections by.

## Write the story

```tsx
// src/app/grid/Sorting.stories.tsx
import type {Meta, StoryObj} from "@storybook/react-vite";
import Sorting from "./Sorting";
import fixture from "../../../test/fixtures/grid/Sorting.json";

export default {
    component: Sorting,
    parameters: {fixture},
} satisfies Meta<typeof Sorting>;

export const Default: StoryObj<typeof Sorting> = {};
```

The test and the story share one fixture file. A view has no props,
since everything it shows comes from its injections, so the story has no
args either.

## One fixture per view

QLive's config and injections are module state, and Storybook renders
every story in the same preview window. Anything one story adds is still
there for the next one. That's why the preview adds all fixtures up front,
and why each view gets exactly one: a second fixture for the same route
can't share the page with the first, and `addFixture()` throws.

## Things to adapt

- **Size.** Every recording carries the application's full config,
  mostly the schema. With many fixtures, store the config once and merge
  it in when loading them. qlive-doc does that, see
  `qlive-doc/tooling/addFixture.mjs` and `qlive-doc/src/components/QLiveIsland.tsx`.
- **Story placement.** The example keeps stories next to the view. The
  views glob in `main.tsx`, `./app/**/*.tsx`, also matches
  `*.stories.tsx`, so exclude them there (`!./app/**/*.stories.tsx`) or
  keep stories outside `src/app`.
- **The Vite config.** Storybook's Vite builder merges in the
  application's `vite.config.ts`, including QLive's track-usage plugin.
  Its dev server runs in development mode, the same as the application's,
  so the plugin tries to push usage to the backend and warns when none is
  running. Views don't need the plugin to run on a fixture, so you can
  leave it out:

  ```ts
  // .storybook/main.ts
  viteFinal: config => ({
      ...config,
      plugins: config.plugins?.flat(Infinity).filter(plugin => !(plugin && "name" in plugin && plugin.name === "track-usage")),
  }),
  ```

`addFixture()`, `FixtureScope` and the `QLiveFixture` type are in
[Startup and configuration in the API reference](/qlive-framework/api/startup-and-config/).
