#!/usr/bin/env zsh
#
# Writes the development database to qlive-test/qlivetest.backup, the counterpart of
# reset-test-db.sh.
#
# The backup is binary, so it goes into one commit together with the change that made it
# and everything generated from that change.

set -e

cd "$(dirname "$0")/.."

PGPASSWORD=qlivetest pg_dump -Fc -h localhost -U qlivetest qlivetest > qlive-test/qlivetest.backup
