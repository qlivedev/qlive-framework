This project is a framework. Consider the impact on the framework user and their ease of use, not just ease of initial implementation.
qlive-test tests framework features but also acts as structural template for future applications using the framework

## Running qlive-test

The backend belongs to IntelliJ. When the IDE is up, start it through the `idea`
MCP server: execute_run_configuration with "qlive-test dev" (or "qlive-test
prod"). Prefer that over the qlive-test-full-stack entry in launch.json, which
starts a second backend and takes port 8080 out from under the IDE's. launch.json
covers the frontend: qlive-test for Vite alone, qlive-test-attach to attach to a
stack that is already up.

Pass waitForExit false for a server. The call returns an empty output snapshot
and a fullOutputPath pointing at a log that is still growing; startup progress is
there. "Tomcat started on port 8080" and "Started QLiveTestApplication" mean it
is serving.

The IDE MCP server starts runs but cannot stop them. Stop one with SIGTERM to its
PID, which every log line carries after the level; the shutdown hook closes
Tomcat and the Hikari pool gracefully.

## Sibling projects

Some projects next to this one are readable (the paths are granted in
`.claude/settings.local.json`, which is personal and gitignored). What they are
for differs, and the difference matters:

- `domainql` -- former dependency, now vendored into `qlive-api` and
  `qlive-graphql` and diverging. Reference only: read it to see what an
  upstream fix looks like, never as binding. What QLive compiles against is
  in this repository.
- `babel-plugin-track-usage` -- likewise vendored, into
  `qlive-ts/src/vite/babel`. Reference only.
- `automaton`, `automaton-js`, `automaton-test` -- reference only. Read them to
  see how a problem was solved before -- `equalsScalar`, `evaluateMemoryQuery`,
  `createMockedQuery`, `filterTransformer` are the interesting ones -- and cite
  them as prior art, never as current or binding. Do not import from them and
  do not assume any of it is reachable; a fork would sever Automaton entirely.

Nothing else under `~/ideaprojects` is in scope. Do not read the parent folder
or search across it: it holds unrelated projects and the off-limits
`automaton-*-lisa-web.json` customer artifacts.
