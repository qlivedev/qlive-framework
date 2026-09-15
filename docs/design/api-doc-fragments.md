# API doc fragments (design)

Status: built 2026-09-15. Written 2026-09-14 out of the API reference
generator, deferred once, then picked up for `FilterDSL.field`.

## Problem

Doc comments in `qlive-ts` are the reference text: `tooling/generateApiDocs.mjs`
writes the pages under `qlive-doc/src/content/docs/api/` from them. Some
symbols need a sentence on the page that has no business in a comment --
`field` takes a path whose meaning depends on where the condition is
evaluated, and that is worth restating where somebody looks the function
up, but it is already three sentences too many for a tooltip.

## The form

The generator looks for an optional
`qlive-doc/src/content/apiExtra/<symbol path>.md` and appends it to that
symbol's section, after the prose of the doc comment and before the
parameter tables. The path is the heading as the page spells it, minus a
function's parentheses: `startup.md`, `FilterDSL.field.md`,
`QueryDocument.type.md`. `operatorTables` in `tooling/apiTopics.json` was
already a hook of that shape.

They sit beside the pages they are appended to rather than with the
generator, because a fragment is documentation and not tooling. Astro
ignores a directory under `src/content` that no collection claims, so
the only thing the placement costs is that editing one counts as a
change to `qlive-doc` -- which it is.

Two rules keep the mechanism from eating the pages:

- A fragment carries no heading. The heading structure is the
  generator's, and Starlight builds the table of contents out of it.
- A fragment that lands on no section is an error, not a warning. It is
  invisible from the source it belongs to, so somebody renaming an
  export never sees it, and an orphan that only got a warning would stay
  wrong quietly. This is the same bargain the topic map makes with an
  export that ends up on no page.

The generated pages name the directory in their banner, but only on
a page that actually has a fragment, so the usual page still sends the
reader straight to the doc comment.

## Why it pulls rather than the comment pushing

The obvious form is a tag in the comment naming a markdown file, so that
the comment stays a contract and the generator stitches the rest in.
That one is wrong here.

The declarations ship. All 293 doc comments travel in `dist/index.d.ts`,
and `files: ["dist"]` means nothing beside it does. A tag pointing at a
markdown file under `src/` would reach the framework user as a reference
to something their `node_modules` does not contain -- and TypeScript
does not pull markdown into a hover anyway. IDE hover is where the
question is actually asked, so that trade buys a better website with a
worse tooltip.

Pulling from the docs side has neither problem: nothing in the shipped
declarations points at a file that is not in the package, and `qlive-ts`
gains no coupling to `qlive-doc`.

## When to reach for one

Rarely. There is less pressure on the comments than it feels like --
measured over the shipped declarations on 2026-09-14, median comment 4
lines, 90th percentile 12, max 28, with fourteen of 293 past 15 lines
and three carrying a fenced example.

Most of what makes a comment feel too long is not reference at all. A
worked example is how-to; why an API is shaped a particular way is
explanation. Both have somewhere to go, and the generated pages link to
the narrative and back. Move the text to the quadrant it belongs to
first, and use a fragment for what survives that triage: reference the
page wants at the symbol and the hover does not.
