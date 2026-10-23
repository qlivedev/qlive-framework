import {execFileSync} from "node:child_process"
import fs from "node:fs"
import path from "node:path"
import {fileURLToPath} from "node:url"
import {afterAll, beforeAll, describe, expect, it} from "vitest"

const packageDir = path.dirname(fileURLToPath(new URL("../package.json", import.meta.url)))
const generator = path.join(packageDir, "src", "generateTS.js")
const schema = path.join(packageDir, "test", "schema.graphql")

/*
 * Generated inside the package rather than in the system temp directory: the
 * output imports @qlivedev/qlive-ts, and only a path under qlive-codegen has
 * the node_modules chain that resolves it.
 */
const outDir = path.join(packageDir, "test", ".tmp")
const types = path.join(outDir, "types.d.ts")

/*
 * skipLibCheck off is the whole point. An application turns it on -- it has no
 * business typechecking its dependencies' declarations -- which means nothing
 * in a normal build ever looks at a generated .d.ts, and a broken import in one
 * can sit there indefinitely. This is the one place that looks.
 *
 * customConditions resolves qlive-ts to its TypeScript source, the same way the
 * test app does, so the check needs no build of qlive-ts to have run first.
 *
 * An empty "types" keeps the ambient @types packages hoisted to the workspace
 * root out of the program. With skipLibCheck off their own declarations get
 * checked too, and an error in one of those says nothing about the generator.
 */
const tsconfig = {
    compilerOptions: {
        target: "ES2022",
        lib: ["ES2022", "DOM"],
        module: "ESNext",
        moduleResolution: "Bundler",
        customConditions: ["qlive-source"],
        // qlive-ts exports components, so following its imports reaches .tsx
        // sources. Without this they are an error before anything about the
        // generated types has been looked at.
        jsx: "react-jsx",
        strict: true,
        noEmit: true,
        skipLibCheck: false,
        types: []
    },
    include: ["types.d.ts"]
}

let generated

beforeAll(() => {
    fs.rmSync(outDir, {recursive: true, force: true})
    fs.mkdirSync(outDir, {recursive: true})

    execFileSync(process.execPath, [generator, schema, types], {stdio: "pipe"})
    generated = fs.readFileSync(types, "utf8")
})

afterAll(() => {
    fs.rmSync(outDir, {recursive: true, force: true})
})

describe("generated type definitions", () => {

    it("imports exactly the qlive-ts names it uses", () => {
        // DomainObject is absent on purpose: the file declares its own, as the
        // union of the schema's object types.
        expect(generated).toContain(
            'import { FilterDSL, GenericScalar, QueryConfig, Subject, Temporal } from "@qlivedev/qlive-ts"'
        )
        expect(generated).toContain("export type DomainObject =")
    })

    it("declares an enum as the strings it travels as", () => {
        expect(generated).toContain(
            'export type Colour =\n' +
            '    /** Documented so the value doc comment path is exercised too */\n' +
            '    "RED" |\n' +
            '    "GREEN"'
        )

        // an enum is a value, not a row, so it has no place in the row union
        expect(generated).not.toContain("Colour |")
    })

    it("types the integers by how they travel", () => {
        // Long arrives as a string and becomes a bigint, Number stays a number,
        // and BigDecimal is the string it travels as until a decimal type is chosen
        expect(generated).toMatch(/big\?: bigint/)
        expect(generated).toMatch(/size\?: number/)
        expect(generated).toMatch(/amount\?: string/)
    })

    it("merges the actions into qlive-ts's Actions, typed by their arguments", () => {
        expect(generated).toContain(
            'declare module "@qlivedev/qlive-ts" {\n' +
            '    interface Actions {\n' +
            '        /** Documented so the action doc comment path is exercised too */\n' +
            '        approveWidget: { args: { widget: Subject, comment: string, notify: boolean, due?: Temporal.Instant | null }, result: '
        )
        expect(generated).toContain(
            'approveWidgets: { args: { widgets: Subject[], filter?: WidgetFilterInput | null, tags?: (string | null)[] | null }, result: ('
        )

        // a mutation without a subject argument looks like any other in the schema
        expect(generated).not.toContain("renameWidget:")
    })

    it("declares the input types an argument takes", () => {
        expect(generated).toContain(
            '/** A widget as an action takes it as input */\n' +
            'export type WidgetInput = {\n' +
            '    id: string\n' +
            '    /** Documented so the input field doc comment path is exercised too */\n' +
            '    name: string\n' +
            '    size?: number | null\n' +
            '    filter?: WidgetFilterInput | null\n' +
            '}'
        )

        // a subject reference is qlive-ts's Subject, not a type of the schema's own
        expect(generated).not.toContain("export type SubjectRefInput")
    })

    it("types an action's result by what issueAction() selects of it", () => {
        // the scalar and enum fields, and not the owner, which is an object
        const result = /approveWidget: .*result: (Pick<Widget, [^>]*>) }/.exec(generated)?.[1]
        expect(result).toMatch(/^Pick<Widget, "id" \| "colour" \| /)
        expect(result).toContain('"tags"')
        expect(result).not.toContain('"owner"')

        expect(generated).toMatch(/approveWidgets: .*result: \(Pick<Widget, [^>]*> \| null\)\[\] }/)
    })

    it("typechecks against qlive-ts", () => {
        fs.writeFileSync(path.join(outDir, "tsconfig.json"), JSON.stringify(tsconfig, null, 4), "utf8")

        let result
        try
        {
            execFileSync(
                path.join(packageDir, "node_modules", ".bin", "tsc"),
                ["--project", outDir],
                {stdio: "pipe", encoding: "utf8"}
            )
            result = ""
        }
        catch (e)
        {
            // tsc reports to stdout and exits non-zero
            result = e.stdout || e.message
        }

        expect(result).toBe("")
    })
})
