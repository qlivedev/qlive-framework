#!/usr/bin/env node

import fs from "fs"
import path from "path"

import { introspectionFromSchema } from "graphql"
import { GraphQLFileLoader } from "@graphql-tools/graphql-file-loader"
import { loadSchema as gqlLoadSchema } from "@graphql-tools/load"
import { isListType, unwrapAll } from "./type-utils.js"

const GRAPHQL_TO_TYPESCRIPT = {
    "BigDecimal" : "bigint",
    "Boolean" : "boolean",
    "Byte" : "number",
    "ComputedValue" : "ComputedValue",
    "Condition" : "ConditionNode",
    "Currency" : "number",
    "Date" : "Temporal.PlainDate",
    "DomainObject" : "DomainObject",
    "Float" : "number",
    "FieldExpression" : "string | FieldNode",
    "GenericScalar" : "GenericScalar",
    "Int" : "number",
    "JSONB" : "any",
    "Long" : "number",
    "QueryConfig" : "QueryConfig",
    "String" : "string",
    "Timestamp" : "Temporal.Instant"
}


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


function mapGraphQLToTypeScript(name)
{
    return GRAPHQL_TO_TYPESCRIPT[name] || name
}


function typesUnionExpression(typeNames)
{
    let out = ""

    for (let i = 0; i < typeNames.length; i++)
    {
        const name = typeNames[i]

        if (i !== 0)
        {
            out += " | "

            if ((i & 7) === 0)
            {
                out += "\n    ";
            }
        }

        out += name
    }

    return out
}


function fieldDocs(fieldDef)
{
    return fieldDef.description ? `    /** ${ fieldDef.description } */\n` : ""
}


function typeDocs(typeDef)
{
    return typeDef.description ? `/** ${ typeDef.description } */\n` : ""
}


function generateTypeDefinitions(schemaPath, output)
{
    loadSchema(schemaPath).then(schema => {


        const types = schema.types.filter(typeDef =>
                typeDef.kind === "OBJECT" &&
                typeDef.name[0] !== "_"
            )

        //fs.writeFileSync("schema.json", JSON.stringify(schema, null, 4), "utf8")
        //console.log("schema.json", JSON.stringify(schema, null, 4))

        let typeDefinitions = trimIndent(`
        /*
            Generated types. Do *not* edit. Run "pnpm generate" to update from schema.graphql
        */
        import { QueryDocumentAPI } from "@quinscape/qlive-ts"
        `)

        types.forEach(typeDef => {

            const fields = typeDef.fields.map((fieldDef) => {

                const type = unwrapAll(fieldDef.type)
                const isNonNull = fieldDef.type.kind === "NON_NULL"

                const typeName = mapGraphQLToTypeScript(type.name);

                return `${fieldDocs(fieldDef)}    ${ isNonNull ? fieldDef.name : fieldDef.name + "?" } : ${isListType(fieldDef.type) ?  typeName + "[]" : typeName}`
            }).join("\n")

            typeDefinitions += trimIndent(`
                ${typeDocs(typeDef)}export type ${typeDef.name} = {
                
                    _type: "${typeDef.name}",
                    
                ${fields}
                }
                
                `)
        })

        typeDefinitions += "export type DomainObject = " + typesUnionExpression(types.map(td => td.name))

        fs.writeFileSync(output, typeDefinitions, "utf8");
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
