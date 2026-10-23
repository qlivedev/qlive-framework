import {GraphQLQuery} from "./GraphQLQuery";
import {GraphQLResponseError} from "./util/graphql";
import {GraphQLField, GraphQLTypeRef} from "./GraphQLSchema";
import {findType, unwrapAll} from "./type-utils";
import i18n from "./i18n";

/**
 * The application's actions by name, each with its arguments and its result. Empty here: the types generated from
 * the schema fill it in by declaration merging, so that issueAction() knows every action and a misspelled name or
 * argument is a compile error.
 *
 *     declare module "@qlivedev/qlive-ts" {
 *         interface Actions {
 *             approveBar: { args: { bar: Subject, comment: string, notify: boolean }, result: ApproveResult }
 *         }
 *     }
 */
export interface Actions {
}

/**
 * The name of one of the application's actions.
 */
export type ActionName = keyof Actions & string

/**
 * The arguments of an action, keyed by the parameter names of its method: subjects and input alike.
 */
export type ActionArgs<N extends ActionName> = Actions[N] extends { args: infer A } ? A : never

/**
 * What an action answers with where it does not refuse.
 */
export type ActionResult<N extends ActionName> = Actions[N] extends { result: infer R } ? R : never

/**
 * A row an action is about, as a subject argument takes it: any row with its id and, for a versioned type, the
 * version the user saw it at. A row of a query document is one as it is, as long as the query selects both.
 */
export type Subject = {
    id: string
    version?: string | null
}

/**
 * One parameter of an action, as its mutation declares it.
 */
export type ActionParameter = {
    name: string
    /**
     * Whether the argument is non-null in the schema: a subject, or input marked `@NotNull` or of a primitive type
     */
    required: boolean
    /**
     * Whether it is a subject, which takes a row or a list of rows
     */
    subject: boolean
    /**
     * Whether it takes a list
     */
    list: boolean
    /**
     * Name of its scalar, enum or input type: "SubjectRefInput" for a subject
     */
    type: string
}

/**
 * One reason an action refused, as the server says it.
 */
export type Refusal = {
    /**
     * "RULE" for a rule that did not hold, "CONFLICT" for a subject row somebody else changed or deleted since the
     * user saw it, or a code of the application's own
     */
    code: string
    /**
     * i18n tag of the message, see refusalMessage()
     */
    messageKey: string
    /**
     * Path of the blamed field, starting with a parameter name: "bar.description", "bars.2.description",
     * "comment". For a conflict the subject, "bar" or "bars.2". Null where the refusal blames no field.
     */
    field: string | null
    /**
     * Class a layout can react to, null where the rule names none
     */
    errorClass: string | null
}

/**
 * How an action ended: with its result, or refused. A refusal is a normal outcome, not an error -- the request
 * was fine, and the stored state or the input did not allow the action -- so it resolves like the result does.
 */
export type ActionOutcome<R> =
    { refused: false, result: R } |
    { refused: true, refusals: Refusal[] }

/**
 * The classification of a refusal's errors on the server, in their extensions.
 */
export const REFUSED = "REFUSED"

/**
 * The input type of a subject argument.
 */
const SUBJECT_REF_INPUT = "SubjectRefInput"

const MUTATION_TYPE = "MutationType"

/**
 * The mutation of each action issued so far. Built from the schema on first use, like a query's conversion map.
 */
const mutations = new Map<string, Mutation>()

type Mutation = {
    query: GraphQLQuery<unknown>
    subjects: Set<string>
    parameters: ActionParameter[]
    /** the type of each input argument, by name */
    inputTypes: Map<string, GraphQLTypeRef>
}

/**
 * Issues an action: the mutation of an `@QLiveAction` method, with the given arguments.
 *
 *     const outcome = await issueAction("approveBar", { bar, comment, notify: true })
 *     if (outcome.refused)
 *     {
 *         // outcome.refusals, see refusalMessage()
 *     }
 *
 * The mutation is built from the schema, so there is nothing to declare. A subject argument takes the row itself,
 * or a list of rows, and only its id and version are sent; the server loads the stored row and refuses with a
 * conflict where its version moved. The result selects the scalar and enum fields of the action's result type.
 *
 * Resolves with the outcome, a refusal included, and rejects where the request failed: no GraphQL response, or
 * errors that are not a refusal.
 *
 * @param name  name of the action, which is the name of its method
 * @param args  arguments by parameter name
 */
export async function issueAction<N extends ActionName>(
    name: N,
    args: ActionArgs<N>
): Promise<ActionOutcome<ActionResult<N>>>
{
    const {query, subjects, inputTypes} = mutationOf(name)

    const params: Record<string, unknown> = {...(args as Record<string, unknown>)}
    for (const subject of subjects)
    {
        const value = params[subject]
        params[subject] = Array.isArray(value) ? value.map(subjectRef) : value == null ? value : subjectRef(value)
    }
    for (const [parameter, type] of inputTypes)
    {
        if (parameter in params)
        {
            params[parameter] = inputOnly(params[parameter], type)
        }
    }

    try
    {
        const result = await query.execute(params) as ActionResult<N>
        return {refused: false, result}
    }
    catch (e)
    {
        if (e instanceof GraphQLResponseError && e.refused)
        {
            return {refused: true, refusals: e.errors.map(error => refusalOf(error.extensions ?? {}))}
        }
        throw e
    }
}

/**
 * The parameters of an action, in the order of its method's.
 *
 * @param name  name of the action
 */
export function actionParameters(name: ActionName): readonly ActionParameter[]
{
    return mutationOf(name).parameters
}

/**
 * The text of a refusal, in the page's locale: the translation of its message key. The keys of QLive's own
 * conflicts are translated like any other QLive text, and the server offers those of the application's rules
 * among its translations.
 *
 * @param refusal   refusal
 */
export function refusalMessage(refusal: Refusal): string
{
    // written out, so that the analysis finds QLive's own tags
    switch (refusal.messageKey)
    {
        case "Action.subjectChanged":
            return i18n("Action.subjectChanged")
        case "Action.subjectGone":
            return i18n("Action.subjectGone")
        default:
            return i18n(refusal.messageKey)
    }
}

/**
 * The refusals blaming the given field, or a field below it: "bar" finds those of "bar.description" too.
 *
 * @param refusals  refusals of an outcome
 * @param path      field path starting with a parameter name
 */
export function refusalsOf(refusals: readonly Refusal[], path: string): Refusal[]
{
    return refusals.filter(r => r.field === path || (r.field?.startsWith(path + ".") ?? false))
}


function refusalOf(extensions: Record<string, unknown>): Refusal
{
    const {code, messageKey, field, errorClass} = extensions as Refusal
    return {code, messageKey, field: field ?? null, errorClass: errorClass ?? null}
}


/**
 * A value with only the fields its input type declares, at every level. A working set's draft handed to an `@Input`
 * parameter carries more than its input type -- relation objects, computed fields -- which the server would refuse.
 */
function inputOnly(value: unknown, type: GraphQLTypeRef): unknown
{
    if (value == null)
    {
        return value
    }
    switch (type.kind)
    {
        case "NON_NULL":
            return inputOnly(value, type.ofType!)
        case "LIST":
            return Array.isArray(value) ? value.map(v => inputOnly(v, type.ofType!)) : inputOnly(value, type.ofType!)
        case "INPUT_OBJECT":
        {
            const inputType = findType(type.name)
            const source = value as Record<string, unknown>
            const out: Record<string, unknown> = {}
            for (const field of inputType.inputFields ?? [])
            {
                if (field.name in source)
                {
                    out[field.name] = inputOnly(source[field.name], field.type)
                }
            }
            return out
        }
        default:
            return value
    }
}


function subjectRef(row: unknown): Subject
{
    const {id, version} = row as Subject
    return {id, version}
}


function mutationOf(name: string): Mutation
{
    let mutation = mutations.get(name)
    if (!mutation)
    {
        const type = findType(MUTATION_TYPE)
        const field = type.kind === "OBJECT" ? type.fields.find(f => f.name === name) : undefined
        if (!field)
        {
            throw new Error("No action '" + name + "': the schema has no mutation of that name")
        }

        const parameters = field.args.map(arg => ({
            name: arg.name,
            required: arg.type.kind === "NON_NULL",
            subject: unwrapAll(arg.type).name === SUBJECT_REF_INPUT,
            list: (arg.type.kind === "NON_NULL" ? arg.type.ofType! : arg.type).kind === "LIST",
            type: unwrapAll(arg.type).name
        }))
        const subjects = new Set(parameters.filter(p => p.subject).map(p => p.name))
        const inputTypes = new Map(field.args.filter(arg => !subjects.has(arg.name)).map(arg => [arg.name, arg.type]))

        const variables = field.args.map(arg => "$" + arg.name + ": " + typeSource(arg.type)).join(", ")
        const passed = field.args.map(arg => arg.name + ": $" + arg.name).join(", ")

        const source = "mutation " + name + (variables ? "(" + variables + ")" : "") + " { " +
            name + (passed ? "(" + passed + ")" : "") + selection(field) + " }"

        mutation = {query: new GraphQLQuery<unknown>(source), subjects, parameters, inputTypes}
        mutations.set(name, mutation)
    }
    return mutation
}


/**
 * The selection of a result: its scalar and enum fields, or nothing for a result that is a scalar itself.
 */
function selection(field: GraphQLField): string
{
    const result = findType(unwrapAll(field.type).name)
    if (result.kind !== "OBJECT")
    {
        return ""
    }

    const names = result.fields
        .filter(f => {
            const kind = findType(unwrapAll(f.type).name).kind
            return kind === "SCALAR" || kind === "ENUM"
        })
        .map(f => f.name)

    return " { " + names.join(" ") + " }"
}


/**
 * A type reference as GraphQL source, "[SubjectRefInput!]!".
 */
function typeSource(type: GraphQLTypeRef): string
{
    switch (type.kind)
    {
        case "NON_NULL":
            return typeSource(type.ofType!) + "!"
        case "LIST":
            return "[" + typeSource(type.ofType!) + "]"
        default:
            return type.name
    }
}
