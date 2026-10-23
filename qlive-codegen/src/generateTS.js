#!/usr/bin/env node

import fs from "fs"
import path from "path"

import { introspectionFromSchema } from "graphql"
import { GraphQLFileLoader } from "@graphql-tools/graphql-file-loader"
import { loadSchema as gqlLoadSchema } from "@graphql-tools/load"
import { isListType, unwrapAll, unwrapNonNull, LIST, NON_NULL } from "./type-utils.js"

/*
 * GraphQL scalar -> the TypeScript the generated file uses for it. Names that
 * have to be imported are spelled the way they are imported: the filter node
 * types live in qlive-ts's FilterDSL namespace, and Temporal is the polyfill
 * namespace qlive-ts re-exports.
 *
 * BigDecimal is the string it travels as: no decimal type is chosen for it yet.
 */
const GRAPHQL_TO_TYPESCRIPT = {
    "BigDecimal" : "string",
    "BigInteger" : "bigint",
    "Boolean" : "boolean",
    "Byte" : "number",
    "ComputedValue" : "FilterDSL.ComputedValue",
    "Condition" : "FilterDSL.ConditionNode",
    "Currency" : "number",
    "Date" : "Temporal.PlainDate",
    "DomainObject" : "DomainObject",
    "Float" : "number",
    "FieldExpression" : "string | FilterDSL.FieldNode",
    "GenericScalar" : "GenericScalar",
    "Int" : "number",
    "JSONB" : "any",
    "Long" : "bigint",
    "Number" : "number",
    "QueryConfig" : "QueryConfig",
    "String" : "string",
    "Timestamp" : "Temporal.Instant"
}

/*
 * The qlive-ts exports the mapping above can reach, in the order they are
 * imported. DomainObject is deliberately absent: the generated file declares
 * its own, as the union of the schema's object types.
 *
 * Only the ones a schema actually reaches are imported. An import the output
 * does not use is noise, and a wrong one goes unnoticed for a long time --
 * consuming apps set skipLibCheck, so nothing typechecks a generated .d.ts.
 */
const QLIVE_TS_IMPORTS = ["FilterDSL", "GenericScalar", "QueryConfig", "Subject", "Temporal"]

/*
 * The input type of an action's subject argument, which is what tells an action
 * apart from a plain mutation in the schema.
 */
const SUBJECT_REF_INPUT = "SubjectRefInput"


function loadSchema(path)
{
    if (!fs.existsSync(path))
    {
        throw new Error("Could not find schema " + path)
    }

    return gqlLoadSchema(path, {
        loaders: [new GraphQLFileLoader()],
    }).then(
        schema => {
            const introspection = introspectionFromSchema(schema)

            const input = introspection.__schema
            //console.log(JSON.stringify(input, null, 4))
            return input
        }
    )
        .catch(error => {
            console.error("Error loading schema", error)
            process.exit(2)
        })

}

/**
 * Expects a string starting with a return and a number of spaces. Removes the initial return and that many spaces
 * from each line start.
 * @param s
 * @return {string|*}   String unindented so that the second row starts at column 1
 */
export function trimIndent(s)
{
    const m = /^\n +/.exec(s)

    if (!m)
    {
        return s
    }
    return s.substring(1).replace(new RegExp("^" + m[0].substring(1), "mg"), "")
}


function mapGraphQLToTypeScript(name, imported)
{
    const typeScript = GRAPHQL_TO_TYPESCRIPT[name]
    if (!typeScript)
    {
        // a type of the schema's own, declared in the same file
        return name
    }

    for (const importable of QLIVE_TS_IMPORTS)
    {
        // matches the qualifier of "FilterDSL.ConditionNode" as well as a bare
        // "QueryConfig", and not the "GenericScalar" inside a domain type name
        if (new RegExp("\\b" + importable + "\\b").test(typeScript))
        {
            imported.add(importable)
        }
    }

    return typeScript
}


function typesUnionExpression(typeNames)
{
    let out = ""

    for (let i = 0; i < typeNames.length; i++)
    {
        const name = typeNames[i]

        if (i !== 0)
        {
            // Break before the space, not after it, or the wrap leaves the
            // separator dangling as trailing whitespace.
            out += (i & 7) === 0 ? " |\n    " : " | "
        }

        out += name
    }

    return out
}


function fieldDocs(fieldDef)
{
    return fieldDef.description ? `    /** ${ fieldDef.description } */\n` : ""
}


/*
 * An input object type, as the object a mutation's argument takes: a
 * non-null field is required, any other may be left out or null.
 */
function inputDefinition(typeDef, imported)
{
    const fields = typeDef.inputFields.map(fieldDef =>
        fieldDocs(fieldDef) + (
            fieldDef.type.kind === NON_NULL
                ? `    ${ fieldDef.name }: ${ valueExpression(unwrapNonNull(fieldDef.type), imported) }`
                : `    ${ fieldDef.name }?: ${ typeExpression(fieldDef.type, imported) }`
        )
    )

    return `${ typeDocs(typeDef) }export type ${ typeDef.name } = {\n${ fields.join("\n") }\n}\n\n`
}


function typeDocs(typeDef)
{
    return typeDef.description ? `/** ${ typeDef.description } */\n` : ""
}


/*
 * A GraphQL enum becomes a union of its values as string literals, which is
 * what the value actually is on the wire. A TypeScript enum would be a runtime
 * object, and a .d.ts declaring one promises code that is never generated.
 */
function enumDefinition(typeDef)
{
    const values = typeDef.enumValues.map(
        valueDef => (valueDef.description ? `    /** ${ valueDef.description } */\n` : "") +
            `    "${ valueDef.name }"`
    )

    return `${ typeDocs(typeDef) }export type ${ typeDef.name } =\n${ values.join(" |\n") }\n\n`
}


/*
 * The TypeScript of a type reference as a whole, modifiers included: a list is
 * an array, and a nullable element is one that may be null.
 */
function typeExpression(type, imported, schema)
{
    if (type.kind === NON_NULL)
    {
        return valueExpression(type.ofType, imported, schema)
    }
    return valueExpression(type, imported, schema) + " | null"
}


function valueExpression(type, imported, schema)
{
    if (type.kind === LIST)
    {
        const element = typeExpression(type.ofType, imported, schema)
        return (element.includes("|") ? "(" + element + ")" : element) + "[]"
    }

    if (type.name === SUBJECT_REF_INPUT)
    {
        imported.add("Subject")
        return "Subject"
    }

    // declared in the same file, see inputDefinition()
    if (type.kind === "INPUT_OBJECT")
    {
        return type.name
    }

    // an action's result: issueAction() selects its scalar and enum fields, and the type says no more than that
    if (schema && type.kind === "OBJECT")
    {
        const objectType = schema.types.find(t => t.name === type.name)
        const selected = objectType.fields
            .filter(fieldDef => {
                const kind = unwrapAll(fieldDef.type).kind
                return kind === "SCALAR" || kind === "ENUM"
            })
            .map(fieldDef => JSON.stringify(fieldDef.name))

        return selected.length === objectType.fields.length
            ? type.name
            : `Pick<${ type.name }, ${ selected.join(" | ") }>`
    }

    return mapGraphQLToTypeScript(type.name, imported)
}


/*
 * The actions of the schema, merged into qlive-ts's Actions interface, which is
 * what types issueAction(). An action is a mutation with a subject argument: one
 * of input alone looks like any other mutation in the schema, and is left out.
 */
function actionsDefinition(schema, imported)
{
    const mutationTypeName = schema.mutationType && schema.mutationType.name
    const mutationType = mutationTypeName && schema.types.find(t => t.name === mutationTypeName)
    if (!mutationType)
    {
        return ""
    }

    const actions = mutationType.fields.filter(
        fieldDef => fieldDef.args.some(arg => unwrapAll(arg.type).name === SUBJECT_REF_INPUT)
    )
    if (!actions.length)
    {
        return ""
    }

    const entries = actions.map(fieldDef => {
        const args = fieldDef.args.map(arg =>
            arg.type.kind === NON_NULL
                ? `${ arg.name }: ${ valueExpression(unwrapNonNull(arg.type), imported) }`
                : `${ arg.name }?: ${ typeExpression(arg.type, imported) }`
        ).join(", ")

        const doc = fieldDef.description ? `        /** ${ fieldDef.description } */\n` : ""
        return `${ doc }        ${ fieldDef.name }: { args: { ${ args } }, result: ${ typeExpression(fieldDef.type, imported, schema) } }`
    })

    return "\n\ndeclare module \"@qlivedev/qlive-ts\" {\n    interface Actions {\n" + entries.join("\n") + "\n    }\n}"
}


function generateTypeDefinitions(schemaPath, output)
{
    loadSchema(schemaPath).then(schema => {


        const types = schema.types.filter(typeDef =>
                typeDef.kind === "OBJECT" &&
                typeDef.name[0] !== "_"
            )

        const enums = schema.types.filter(typeDef =>
                typeDef.kind === "ENUM" &&
                typeDef.name[0] !== "_"
            )

        //fs.writeFileSync("schema.json", JSON.stringify(schema, null, 4), "utf8")
        //console.log("schema.json", JSON.stringify(schema, null, 4))

        const inputs = schema.types.filter(typeDef =>
                typeDef.kind === "INPUT_OBJECT" &&
                typeDef.name !== SUBJECT_REF_INPUT &&
                typeDef.name[0] !== "_"
            )

        const imported = new Set()

        // before the object types, which is where the references to them are
        let typeDefinitions = enums.map(enumDefinition).join("")
        typeDefinitions += inputs.map(typeDef => inputDefinition(typeDef, imported)).join("")

        types.forEach(typeDef => {

            const fields = typeDef.fields.map((fieldDef) => {

                const type = unwrapAll(fieldDef.type)
                const isNonNull = fieldDef.type.kind === "NON_NULL"

                const typeName = mapGraphQLToTypeScript(type.name, imported);

                return `${fieldDocs(fieldDef)}    ${ isNonNull ? fieldDef.name : fieldDef.name + "?" }: ${isListType(fieldDef.type) ?  typeName + "[]" : typeName}`
            }).join("\n")

            typeDefinitions += trimIndent(`
                ${typeDocs(typeDef)}export type ${typeDef.name} = {
                ${fields}
                }
                
                `)
        })

        typeDefinitions += "export type DomainObject = " + typesUnionExpression(types.map(td => td.name))

        typeDefinitions += actionsDefinition(schema, imported)

        const names = QLIVE_TS_IMPORTS.filter(name => imported.has(name))

        const header = trimIndent(`
        /*
            Generated types. Do *not* edit. Run "pnpm generate" to update from schema.graphql
        */
        `) + (names.length ? `import { ${ names.join(", ") } } from "@qlivedev/qlive-ts"\n\n` : "")

        fs.writeFileSync(output, header + typeDefinitions.trimEnd() + "\n", "utf8");
    })
}
if (process.argv.length !== 4)
{
    console.error("Usage: generateTS <schema-file> <types.d.ts>");
    process.exit(1);
}
else
{
    generateTypeDefinitions(
        process.argv[2],
        process.argv[3]
    )
}
