#!/usr/bin/zsh
#
# Puts the development database back to what qlive-test/qlivetest.backup holds.
#
# --force disconnects whatever is attached, the running backend included; its pool
# reconnects on its own once the restore is through.

set -e

cd "$(dirname "$0")/.."

dropdb --force --if-exists qlivetest
createdb qlivetest

pg_restore -1 -f- qlive-test/qlivetest.backup | psql -v ON_ERROR_STOP=1 -d qlivetest
