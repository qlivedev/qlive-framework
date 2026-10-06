import {describe, expect, test} from "vitest";
import {and, component, field, value} from "../../src/FilterDSL";
import {decompileFilter} from "../../src/util/decompileFilter";

describe("decompileFilter", () => {
    test("closes a method without arguments on the same line", () => {
        const condition = and(
            component("search",
                and(
                    field("flag").isTrue(),
                    field("num").gt(value(1000))
                )
            ),
            component("grid",
                field("description").containsIgnoreCase(value("ana"))
            )
        )

        expect(decompileFilter(condition)).toBe(
            "and(\n" +
            "    component(\"search\", \n" +
            "        and(\n" +
            "            field(\"flag\").isTrue(),\n" +
            "            field(\"num\").gt(\n" +
            "                value(1000)\n" +
            "            )\n" +
            "        )\n" +
            "    ),\n" +
            "    component(\"grid\", \n" +
            "        field(\"description\").containsIgnoreCase(\n" +
            "            value(\"ana\")\n" +
            "        )\n" +
            "    )\n" +
            ")"
        )
    })

    test("prints on one line for a negative level", () => {
        expect(decompileFilter(field("flag").isTrue(), -1)).toBe("field(\"flag\").isTrue()")
        expect(decompileFilter(field("num").gt(value(1)), -1)).toBe("field(\"num\").gt(value(1))")
    })
})
