# API doc fragments (design)

Status: deferred on purpose. Written 2026-09-14, out of the API
reference generator. Revisit when the framework and the documentation
are both fleshed out -- not before.

## Problem

Doc comments in `qlive-ts` are the reference text: `tooling/generateApiDocs.mjs`
writes the pages under `qlive-doc/src/content/docs/api/` from them. The
worry is that keeping them that way pushes material into a comment that
does not belong in one -- a worked example, a paragraph of rationale --
and that the comment stops being something you want to read at a call
site.

## Why not the obvious form

The obvious form is a tag in the comment naming a markdown file, so that
the comment stays a contract and the generator stitches the rest in.
That one is wrong here for two reasons.

The declarations ship. All 293 doc comments travel in `dist/index.d.ts`,
and `files: ["dist"]` means nothing beside it does. A tag pointing at a
markdown file under `src/` would reach the framework user as a reference
to something their `node_modules` does not contain -- and TypeScript
does not pull markdown into a hover anyway. IDE hover is where the
question is actually asked, so that trade buys a better website with a
worse tooltip.

There is also less pressure than it feels like. Measured over the
shipped declarations: median comment 4 lines, 90th percentile 12, max
28. Fourteen of 293 run past 15 lines, and three contain a fenced
example.

## The form to reach for, if any

Invert it. The generator looks for an optional
`tooling/apiExtra/<Symbol>.md` and appends it to that symbol's section.
Nothing in the shipped declarations references a file that is not in the
package, and `qlive-ts` gains no coupling to `qlive-doc` -- the fragment
is docs-side, where it belongs. `operatorTables` in `tooling/apiTopics.json`
is already a hook of that shape.

Its one weakness is that a fragment is invisible from the source, so
somebody changing an API does not see it. A fragment naming a symbol
that no longer exists therefore has to be an error, the way an unmapped
export already is.

## First, though

Most of what makes a comment feel too long is not reference. A worked
example is how-to; why the API is shaped a particular way is
explanation. Both have somewhere to go now, and the generated pages link
to the narrative and back. Move the text to the quadrant it belongs to
before adding a mechanism for keeping it attached to the symbol.

The trigger for picking this up is a second fragment that earns its
place after that triage -- not the first one.
