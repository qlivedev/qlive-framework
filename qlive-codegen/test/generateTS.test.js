import {execFileSync} from "node:child_process"
import fs from "node:fs"
import path from "node:path"
import {fileURLToPath} from "node:url"
import {afterAll, beforeAll, describe, expect, it} from "vitest"

const packageDir = path.dirname(fileURLToPath(new URL("../package.json", import.meta.url)))
const generator = path.join(packageDir, "tooling", "generateTS.js")
const schema = path.join(packageDir, "test", "schema.graphql")

/*
 * Generated inside the package rather than in the system temp directory: the
 * output imports @quinscape/qlive-ts, and only a path under qlive-codegen has
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
            'import { FilterDSL, GenericScalar, QueryConfig, Temporal } from "@quinscape/qlive-ts"'
        )
        expect(generated).toContain("export type DomainObject =")
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
