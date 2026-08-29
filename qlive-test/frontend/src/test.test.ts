import { test } from "vitest"
import { FilterDSL } from "@quinscape/qlive-ts"

const { field, value, or } = FilterDSL

// Ported verbatim from the old repo's test.test.ts, which has no
// assertions -- just logs a FilterDSL.or(...) expression. The old
// rstest runner didn't require a test()/it() wrapper to run a .test.ts
// file's top-level code; vitest does, so this wrapper is added purely
// for that reason, not as an added assertion.
test("logs a FilterDSL.or(...) expression", () => {
    console.log(
        or(
            field("name").eq(
                value("Foo #1"
                )
            ),
            field("owner.login").eq(
                value("admin")
            )
        )
    )
})
