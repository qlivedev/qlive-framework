/*
 * Build-time half of the generated query result types.
 *
 * The dev backend derives the same types from the same input: QLive's
 * GraphQLQueryTypingService parses each tracked `new GraphQLQuery(...)` against the
 * live schema and patches the result type back into the module. That only runs in the
 * dev profile, so without this a production build has no way to bring a checked-in
 * result type back in line with its query.
 *
 * The two therefore have to agree byte for byte -- a build that rewrote what dev wrote
 * would leave a dirty working tree after every `pnpm generate`. That is why this is a
 * literal port down to the shape of the traversal: the two are meant to be read side by
 * side, and a change to either belongs in both.
 */

import fs from "node:fs"
import path from "node:path"

import {getNamedType, isListType, isNonNullType, isObjectType, parse} from "graphql"

/** npm package the generated result types import their QLive types from */
export const QLIVE_PACKAGE = "@quinscape/qlive-ts"

/** Interface mixed into the result type of a query selecting a query document */
export const DOCUMENT_METHODS = "QueryDocumentMethods"

/** The name the track-usage analysis files a `new GraphQLQuery(...)` under */
export const GRAPHQL_QUERY_CONSTRUCTOR_NAME = "GraphQLQuery"

/*
 * Finds the "export const <Name>" a tracked GraphQLQuery construction belongs to. The name is ASCII
 * rather than a full TypeScript identifier on purpose: it doubles as the query's name, and a GraphQL
 * Name is [_A-Za-z][_0-9A-Za-z]*. Upper case first, since query names are type-like.
 *
 * The "d" flag is what Java's Matcher.start(int) gives for free: the prologue ends where group 1 begins.
 */
const RE_VAR_NAME = /.*((export)\s+?const\s+([A-Z][A-Za-z0-9_$]*))\s*=.*/ds

const RE_TYPE_PARAM = /^new GraphQLQuery<(.*)>/

/** Finds the named imports of an existing import from {@link QLIVE_PACKAGE}, so we can join them */
const RE_QLIVE_IMPORT = new RegExp(
    "import\\s+(?:type\\s+)?\\{([^}]*)}\\s*from\\s*[\"']" + escapeRegExp(QLIVE_PACKAGE) + "[\"']",
    "d"
)

/** Finds {@link DOCUMENT_METHODS} among already imported names */
const RE_DOCUMENT_METHODS_IMPORTED = new RegExp("\\b" + DOCUMENT_METHODS + "\\b")


function escapeRegExp(s)
{
    return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
}


/**
 * Updates the TS query sources below the given source root from the given analysis.
 *
 * @param {Object} schema       GraphQL schema the queries are checked against
 * @param {Object} analysis     track-usage analysis, i.e. { usages: { "./app/Q_Foo": ... } }
 * @param {string} sourceRoot   directory the module ids in the analysis are relative to
 *
 * @returns {string[]} the module ids whose source was rewritten
 */
export function updateGraphQLQueryTypes(schema, analysis, sourceRoot)
{
    const updated = []

    for (const [modulePath, modFnRef] of Object.entries(analysis.usages ?? {}))
    {
        const {ctx, selectedOperations} = analyzeGraphQLQuery(schema, modFnRef, modulePath)

        if (!selectedOperations.length)
        {
            continue
        }

        const tsCode = renderResultType(schema, selectedOperations)

        const file = moduleFile(sourceRoot, ctx.modulePath)
        if (!fs.existsSync(file))
        {
            throw new Error(
                `Module '${ctx.modulePath}' declares a GraphQLQuery but ${file} does not exist. A query ` +
                `belongs in a ".ts" module of its own.`
            )
        }
        const source = fs.readFileSync(file, "utf8")
        const moduleInfo = analyzeModule(ctx, source)

        const updatedSource = moduleInfo && renderModule(
            moduleInfo,
            tsCode,
            isQueryDocumentResult(schema, selectedOperations[0])
        )

        if (!updatedSource)
        {
            console.warn(
                `[qlive] module '${ctx.modulePath}' seems to be invalid TypeScript at this point. Ignoring it.`
            )
        }
        else if (updatedSource !== source)
        {
            fs.writeFileSync(file, updatedSource, "utf8")
            updated.push(ctx.modulePath)
        }
    }

    return updated
}


/**
 * Resolves a module id from the analysis to the file it was read from. Query modules are ".ts" -- a
 * query lives in a module of its own and has no JSX in it, which is the rule the dev-side service
 * assumes as well.
 */
function moduleFile(sourceRoot, modulePath)
{
    return path.join(sourceRoot, modulePath + ".ts")
}


/**
 * Analyzes the GraphQLQuery constructions recorded for one module.
 *
 * @param {Object} schema       GraphQL schema
 * @param {Object} modFnRef     the module's entry in the analysis
 * @param {string} modulePath   module id, e.g. "./app/Q_Foo"
 *
 * @returns {{ctx: Object|null, result: Object|null, selectedOperations: Object[]}}
 */
export function analyzeGraphQLQuery(schema, modFnRef, modulePath)
{
    const calls = modFnRef?.calls?.[GRAPHQL_QUERY_CONSTRUCTOR_NAME] ?? []
    const indexes = modFnRef?.indexes?.[GRAPHQL_QUERY_CONSTRUCTOR_NAME] ?? []

    for (let i = 0; i < calls.length; i++)
    {
        const call = calls[i]

        if (!call.length || typeof call[0] !== "string")
        {
            console.warn(
                `[qlive] Ignoring GraphQLQuery construction #${i} in module '${modulePath}': the query was ` +
                `not recorded as a static string. Pass the query as a string or template literal without ` +
                `expressions.`
            )
            continue
        }

        // Without the source offsets of the constructor call we cannot patch the result type back into
        // the module, so there is nothing useful to do for this module.
        const index = indexes[i] ?? []
        if (index.length < 2)
        {
            console.warn(
                `[qlive] No source index recorded for GraphQLQuery construction #${i} in module ` +
                `'${modulePath}'. Enable the 'indexes' option of babel-plugin-track-usage to generate ` +
                `query types.`
            )
            continue
        }

        const definitions = parse(call[0]).definitions.filter(d => d.kind === "OperationDefinition")
        if (!definitions.length)
        {
            continue
        }

        const selectedOperations = []
        const ctx = {modulePath, start: index[0], end: index[1], definitions}

        // A document holds queries or mutations, not both: it is analyzed as queries first and
        // only read as mutations when it selects none.
        let result = analyzeQuery(schema, ctx, selectedOperations, true)
        if (!selectedOperations.length)
        {
            result = analyzeQuery(schema, ctx, selectedOperations, false)
        }

        if (selectedOperations.length)
        {
            return {ctx, result, selectedOperations}
        }
    }

    return {ctx: null, result: null, selectedOperations: []}
}


/**
 * Analyzes a root type for selected operations. Gets called twice, once for queries and once for
 * mutations. This is the first pass traversal that collects information about all selections.
 *
 * @param {Object} schema               GraphQL schema
 * @param {Object} ctx                  document context
 * @param {Object[]} selectedOperations selected operations, appended to
 * @param {boolean} processQueries      true if we should process queries, false for mutations
 *
 * @returns {{rootTypeName: string, allComplete: boolean}} query info
 */
function analyzeQuery(schema, ctx, selectedOperations, processQueries)
{
    const rootType = processQueries ? schema.getQueryType() : schema.getMutationType()
    if (!rootType)
    {
        // A schema without a mutation type is a schema whose queries are all this pass can find.
        return {rootTypeName: null, allComplete: true}
    }

    let allComplete = true
    for (const definition of ctx.definitions)
    {
        const where = `${ctx.modulePath}, ${definition.operation} ${definition.name?.value ?? "<unnamed>"}`

        for (const s of definition.selectionSet.selections)
        {
            refuseFragment(where, s, rootType.name)
        }

        const selection = definition.selectionSet.selections[0]

        if (selection.kind === "Field")
        {
            const fieldDef = rootType.getFields()[selection.name.value]
            if (!fieldDef)
            {
                continue
            }

            if (
                processQueries && definition.operation === "mutation" ||
                !processQueries && definition.operation === "query"
            )
            {
                continue
            }

            const result = follow(schema, where, selection, rootType.name)
            selectedOperations.push(selectionTypeNode(
                rootType.name, selection,
                fieldDef.type,
                result.selectedFields, result.complete && !selection.alias
            ))

            if (!result.complete)
            {
                allComplete = false
            }
        }
    }

    return {rootTypeName: rootType.name, allComplete}
}


/**
 * Recursively follows the GraphQL types selected by the current operation.
 *
 * @param {Object} schema     GraphQL schema
 * @param {string} where      module and operation the field belongs to, for error messages
 * @param {Object} field      selected GraphQL node
 * @param {string} typeName   current type name
 *
 * @returns {{selectedFields: Object[], complete: boolean}}
 */
function follow(schema, where, field, typeName)
{
    const type = schema.getType(typeName)
    if (!type)
    {
        throw new Error("Type not found: " + typeName)
    }
    const fieldDef = type.getFields()[field.name.value]
    if (!fieldDef)
    {
        throw new Error(`${type.name} has no Field '${field.name.value}'`)
    }
    const fieldType = getNamedType(fieldDef.type)

    const selectedFields = []
    let allComplete = true

    // Scalars and enums both end the traversal: they carry no selection set to follow.
    if (!isObjectType(fieldType))
    {
        selectedFields.push(selectionTypeNode(
            typeName, field,
            fieldDef.type,
            [], !field.alias
        ))
        return {selectedFields, complete: true}
    }

    const selections = field.selectionSet?.selections ?? []
    for (const selection of selections)
    {
        refuseFragment(where, selection, fieldType.name)

        if (selection.kind === "Field")
        {
            const result = follow(schema, where, selection, fieldType.name)

            if (!result.complete)
            {
                allComplete = false
            }

            selectedFields.push(selectionTypeNode(
                fieldType.name, selection,
                fieldType.getFields()[selection.name.value].type,
                result.selectedFields, allComplete && !selection.alias
            ))
        }
    }

    if (!fieldsMatch(fieldType, selectedFields))
    {
        allComplete = false
    }

    return {selectedFields, complete: allComplete}
}


/**
 * Refuses a selection that is a fragment spread or an inline fragment.
 *
 * Their fields would be dropped from the generated result type without a word, and the conversion map
 * the frontend builds from the same query refuses them as well. Supporting them means teaching both
 * walkers to resolve fields in the type a fragment is conditioned on, so until that happens, saying so
 * beats a result type that quietly misses half the selection.
 *
 * @param {string} where      module and operation, for the message
 * @param {Object} selection  selection to check
 * @param {string} typeName   type the selection sits in
 */
function refuseFragment(where, selection, typeName)
{
    if (selection.kind === "Field")
    {
        return
    }

    throw new Error(
        where + ": fragments are not supported, found " +
        (selection.kind === "InlineFragment" ? "an inline fragment" : "a fragment spread") +
        " in " + typeName
    )
}


/**
 * Renders the result type of a query, that is the type of the value one execution of it yields.
 *
 * That is the type of its single top-level selection, not an object keyed by that selection's result
 * key: GraphQLQuery<T> promises T for one execution, and both inject() and execute() hand the value of
 * the selection over unwrapped.
 *
 * @param {Object} schema                GraphQL schema
 * @param {Object[]} selectedOperations  selected operations, of which the first is the one the result
 *                                       type describes
 *
 * @returns {string} TS code expression
 */
export function renderResultType(schema, selectedOperations)
{
    const operation = selectedOperations[0]
    const typeName = getFieldTypeName(operation)
    const fieldType = getNamedType(operation.fieldType)

    const rendered = isObjectType(fieldType)
        ? renderType(
            schema,
            typeName,
            operation.selectedKids,
            fieldsMatch(fieldType, operation.selectedKids),
            false,
            0
        )
        // a scalar or enum valued method has no selection set describing it
        : typeName

    return isList(operation) ? "Array<" + rendered + ">" : rendered
}


/**
 * Renders the type expression for a selected, potentially aliased node
 *
 * @param {Object} schema           GraphQL schema
 * @param {string} typeName         containing type
 * @param {Object[]} selectedFields selected fields
 * @param {boolean} allComplete     true if the containing type is complete
 * @param {boolean} isListType      true to wrap the result in Array<>
 * @param {number} level            recursion level
 *
 * @returns {string} TS code expression
 */
function renderType(schema, typeName, selectedFields, allComplete, isListType, level)
{
    if (allComplete)
    {
        return typeName
    }

    if (!schema.getType(typeName))
    {
        throw new Error("GraphQL Type not found: " + typeName)
    }

    let out = ""

    const completed = selectedFields.filter(sf => sf.complete)
    if (completed.length)
    {
        out += "Pick<" + completed[0].type + "," + renderPickFields(selectedFields) + ">"
    }

    if (selectedFields.some(sf => !sf.complete))
    {
        // The intersection only has two sides when the picked half is there: everything selected
        // being aliased or incomplete leaves the redefinition standing on its own.
        out += (out.length ? " & " : "") + renderPickRest(schema, typeName, selectedFields, level)
    }

    return isListType ? "Array<" + out + ">" : out
}


/**
 * Handles the redefinition of un-picked fields within a type. That's the part that gets added with
 * <code>& { xxx : ... }</code>
 *
 * @param {Object} schema           GraphQL schema
 * @param {string} typeName         containing type
 * @param {Object[]} selectedFields selected fields
 * @param {number} level            recursion level
 *
 * @returns {string} TS code term
 */
function renderPickRest(schema, typeName, selectedFields, level)
{
    if (!schema.getType(typeName))
    {
        throw new Error("GraphQL Type not found: " + typeName)
    }

    const fields = selectedFields
        .filter(selectedField => !selectedField.complete)
        .map(selectedField => {

            const fieldType = schema.getType(getFieldTypeName(selectedField))
            const name = getAliasedName(selectedField) + (isNonNull(selectedField) ? "" : "?")

            if (isObjectType(fieldType))
            {
                return indent(level + 1) + name + " : " + renderType(
                    schema,
                    fieldType.name,
                    selectedField.selectedKids,
                    fieldsMatch(fieldType, selectedField.selectedKids),
                    isList(selectedField),
                    level + 1
                )
            }

            return indent(level + 1) + name + " : " + getFieldTypeName(selectedField)
        })
        .join(",\n")

    return "{\n" + fields + "\n" + indent(level) + "}"
}


/**
 * Renders the pick fields (second <code>Pick&lt;name,fields&gt;</code> type parameter)
 *
 * @param {Object[]} selectedFields selected fields
 *
 * @returns {string} TS code expression
 */
function renderPickFields(selectedFields)
{
    return selectedFields
        .filter(selectedField => selectedField.complete)
        .map(selectedField => JSON.stringify(selectedField.field.name.value))
        .join(" | ")
}


/**
 * Returns true if the node selection matches the original fields of the type and none of the fields
 * are aliased.
 *
 * @param {Object} type           GraphQL object type
 * @param {Object[]} selectedKids selected fields
 *
 * @returns {boolean} true if the given type is fully selected
 */
function fieldsMatch(type, selectedKids)
{
    const fields = Object.values(type.getFields())
    if (fields.length !== selectedKids.length)
    {
        return false
    }

    return fields.every(
        f => f.name.startsWith("__") ||
            selectedKids.some(sf => getAliasedName(sf) === f.name && !sf.field.alias)
    ) && selectedKids.every(sf => sf.complete)
}


/**
 * Returns true if the given operation selects a query document, that is one of the GraphQL types
 * derived from QueryDocument&lt;T&gt;, e.g. FooDocument.
 *
 * Those arrive in the application as QueryDocument instances, not as the plain JSON objects they are
 * on the wire, which is what earns their result type the {@link DOCUMENT_METHODS} mix-in.
 *
 * @param {Object} schema     GraphQL schema
 * @param {Object} operation  selected operation
 *
 * @returns {boolean} true if the operation yields a query document
 */
function isQueryDocumentResult(schema, operation)
{
    // A list of documents is not a document, and nothing produces one -- so we stay on the
    // safe side of a type that would promise methods the values do not have.
    return !isList(operation) && isQueryDocumentType(schema.getType(getFieldTypeName(operation)))
}


/**
 * Returns true if the given type is one DomainQL derived from QueryDocument&lt;T&gt;.
 *
 * The backend answers this from DomainQL's generic type registry, which a schema file does not carry.
 * What it does carry is the shape that registry produces, and QueryDocument has exactly one: the four
 * properties of com.dataciders.qlive.model.QueryDocument, never more and never fewer. So the shape is
 * what gets matched -- a naming convention would call an application's own "...Document" type a query
 * document and give it methods its values do not have.
 *
 * @param {Object} type  named GraphQL type, or undefined
 *
 * @returns {boolean} true for a query document type
 */
export function isQueryDocumentType(type)
{
    if (!isObjectType(type))
    {
        return false
    }

    const fields = type.getFields()
    const names = Object.keys(fields)

    return names.length === 4 &&
        names.every(name => ["config", "rowCount", "rows", "type"].includes(name)) &&
        getNamedType(fields.config.type).name === "QueryConfig" &&
        getNamedType(fields.type.type).name === "String" &&
        getNamedType(fields.rowCount.type).name === "Int" &&
        isListType(isNonNullType(fields.rows.type) ? fields.rows.type.ofType : fields.rows.type)
}


/**
 * Renders the updated module source with the given result type.
 *
 * @param {Object} moduleInfo       analyzed module
 * @param {string} tsCode           result type expression as rendered by {@link renderResultType}
 * @param {boolean} isQueryDocument true if the query selects a query document
 *
 * @returns {string} new TS source of the module
 */
export function renderModule(moduleInfo, tsCode, isQueryDocument)
{
    const typeName = moduleInfo.variableName + "Result"

    // The document methods are parameterized with the result type itself, so an updated
    // document is typed exactly like the one it came from -- selection and all.
    const resultType = isQueryDocument ? tsCode + " & " + DOCUMENT_METHODS + "<" + typeName + ">" : tsCode

    const prologue =
        (isQueryDocument ? withDocumentMethodsImport(moduleInfo.prologue) : moduleInfo.prologue) +
        "export type " + typeName + " = " + resultType + "\n\n"

    return prologue + moduleInfo.leftSideOfDefinition + " = " +
        moduleInfo.graphQLQueryDefinition + moduleInfo.epilogue
}


/**
 * Returns the given module prologue with {@link DOCUMENT_METHODS} imported from {@link QLIVE_PACKAGE}.
 *
 * The generated result type is the only place referring to that name, so the user should not have to
 * keep an import for it around. An existing import from the QLive package takes the name in, otherwise
 * a new import is prepended.
 *
 * @param {string} prologue  module source in front of the generated result type
 *
 * @returns {string} prologue importing the document methods
 */
export function withDocumentMethodsImport(prologue)
{
    const match = RE_QLIVE_IMPORT.exec(prologue)
    if (!match)
    {
        return "import { " + DOCUMENT_METHODS + " } from \"" + QLIVE_PACKAGE + "\";\n" + prologue
    }

    const names = match[1]
    if (RE_DOCUMENT_METHODS_IMPORTED.test(names))
    {
        return prologue
    }

    const imported = names.replace(/\s+$/, "")
    // whatever separated the last name from the closing brace stays, so a multi-line import stays multi-line
    const trailing = names.substring(imported.length)
    const separator = !imported.length ? "" : (imported.endsWith(",") ? " " : ", ")

    const [start, end] = match.indices[1]
    return prologue.substring(0, start) +
        imported + separator + DOCUMENT_METHODS + trailing +
        prologue.substring(end)
}


/**
 * Analyzes the existing module.
 *
 * Regrettably, the track-usage analysis only provides the "new GraphQLQuery<...>(...)" definition
 * itself, so we have to use more clumsy means to get the rest of the information.
 *
 * @param {Object} ctx     document context
 * @param {string} source  TS source
 *
 * @returns {Object|null} module info, null if the recorded offsets do not fit the source
 */
export function analyzeModule(ctx, source)
{
    const {start, end} = ctx

    if (start > end || end > source.length)
    {
        return null
    }

    const beforeGraphQLDef = source.substring(0, start)
    const epilogue = source.substring(end)

    const match = RE_VAR_NAME.exec(beforeGraphQLDef)
    if (!match)
    {
        throw new Error("Cannot extract variable name: " + beforeGraphQLDef)
    }

    let prologue = source.substring(0, match.indices[1][0])

    const index = prologue.lastIndexOf("export")
    if (index >= 0)
    {
        prologue = prologue.substring(0, index)
    }

    const graphQlDef = source.substring(start, end)
    const variableName = match[3]

    if (!RE_TYPE_PARAM.test(graphQlDef))
    {
        throw new Error(
            `${ctx.modulePath}: '${variableName}' is constructed without a type parameter. Write ` +
            `new GraphQLQuery<any>(...) once -- the generated result type takes that slot.`
        )
    }

    return {
        variableName,
        prologue,
        leftSideOfDefinition: match[1],
        graphQLQueryDefinition: graphQlDef.replace(
            RE_TYPE_PARAM,
            () => "new GraphQLQuery<" + variableName + "Result>"
        ),
        epilogue
    }
}


/**
 * Encapsulates our tree traversal knowledge for one node
 *
 * @param {string} type          containing type
 * @param {Object} field         selection node
 * @param {Object} fieldType     modified GraphQL node type
 * @param {Object[]} selectedKids sub selection within the node
 * @param {boolean} complete     true if the node is selected completely and unaliased
 */
function selectionTypeNode(type, field, fieldType, selectedKids, complete)
{
    return {type, field, fieldType, selectedKids, complete}
}


function getAliasedName(node)
{
    return node.field.alias?.value ?? node.field.name.value
}


function getFieldTypeName(node)
{
    return getNamedType(node.fieldType).name
}


function isList(node)
{
    return isListType(isNonNullType(node.fieldType) ? node.fieldType.ofType : node.fieldType)
}


function isNonNull(node)
{
    return isNonNullType(node.fieldType)
}


/**
 * Returns a string that is n times one indentation level.
 */
function indent(level)
{
    return "    ".repeat(level)
}
