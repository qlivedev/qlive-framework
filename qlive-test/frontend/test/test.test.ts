import {test} from "vitest"
import {FilterDSL} from "@quinscape/qlive-ts"

const {field, value, or} = FilterDSL

// No assertions: this logs a FilterDSL.or(...) expression so the composed node
// can be eyeballed. The test() wrapper is what makes vitest run it at all.
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
