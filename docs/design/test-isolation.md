# Isolating qlive-test's tests from the database's state

Status: started. Written 2026-09-29, after UI checks left rows in the
dev database that made `QueryDocumentServiceTest` fail. Its Foo tests
follow the direction below; its Bar, Baz and Qux tests don't yet.

## Problem

qlive-test's Spring tests run against the development database, the one
the running application writes to. Whatever a developer saves in the
browser is there when the tests run next.

Two concerns look alike and aren't:

- **Tests that don't clean up after themselves** leave rows behind. That
  was judged unimportant, and still is: the database is disposable, and
  `tooling/reset-test-db.sh` puts it back to `qlivetest.backup`.
- **Tests that depend on the state they find** are brittle. They pass
  only on a freshly reset database. A rename saved through the UI
  ("Foo #1 edited") fails three of them, and the failure says nothing
  about the code under test. This is the one that matters.

`QueryDocumentServiceTest`'s Bar, Baz and Qux tests are the case
today: they read the backup's rows and assert on their names and
values. Its Foo tests show the other way, as does `MergeServiceTest`:
they create their own rows, assert only on those, and remove them
afterwards.

## Direction

A test that needs rows makes them, and looks only at them: its own ids,
or a marker in a field no one else writes, added to the condition it
queries with. Then what else the table holds can't change the outcome,
whether that's seed rows, rows saved in the browser, or a previous
run's leftovers.

Not chosen as the answer on its own:

- **Rolling back a transaction per test.** It can't reach what runs in
  transactions of its own, the merge and the push path among them.
- **A separate test database restored before each run.** That keeps the
  browser's writes out, but the tests still depend on the backup's
  content, so every change to the seed data would break them.

## When to do it

The Bar, Baz and Qux tests with their next change, or earlier if they
keep costing a reset. New tests follow the direction above from the
start.
