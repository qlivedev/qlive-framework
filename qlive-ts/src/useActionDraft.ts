import {useState, useSyncExternalStore} from "react";
import {
    actionParameters,
    ActionArgs,
    ActionName,
    ActionOutcome,
    ActionParameter,
    ActionResult,
    issueAction,
    Refusal,
    refusalsOf
} from "./action";

/**
 * An action as a view drafts it before issuing: the arguments gathered so far, and what issuing them came to.
 *
 * What a view renders from. It is replaced, never changed, so a part of the view handed it re-renders like for
 * any other prop.
 */
export interface ActionDraft<N extends ActionName>
{
    /**
     * name of the action
     */
    readonly name: N

    /**
     * the arguments set so far, by parameter name
     */
    readonly args: Readonly<Partial<ActionArgs<N>>>

    /**
     * Sets one argument. A subject takes the row, or the rows of a list subject, as issueAction() does.
     *
     * @param parameter     parameter name
     * @param value         argument
     */
    set<K extends keyof ActionArgs<N> & string>(parameter: K, value: ActionArgs<N>[K]): void

    /**
     * the required parameters that have no argument yet, in the method's order
     */
    readonly missing: readonly string[]

    /**
     * whether nothing is missing, so that issuing can go ahead
     */
    readonly complete: boolean

    /**
     * whether the action is being issued
     */
    readonly pending: boolean

    /**
     * how the last issue ended, null before the first
     */
    readonly outcome: ActionOutcome<ActionResult<N>> | null

    /**
     * The refusals of the last issue blaming the given field or one below it, see refusalsOf().
     *
     * @param path  field path starting with a parameter name
     */
    refusalsOf(path: string): Refusal[]

    /**
     * Issues the action with the arguments set. Resolves with the outcome, which is also the state's from then on,
     * and rejects without a request where something is missing. Issuing while the action is pending resolves with
     * the pending issue's outcome instead of sending it twice.
     */
    issue(): Promise<ActionOutcome<ActionResult<N>>>
}

/**
 * Drafts an action in a view: one of the application's `@QLiveAction` methods, with the arguments the view gathers
 * for it.
 *
 *     const approve = useActionDraft("approveBar")
 *
 *     approve.set("bar", row)
 *     <button disabled={ !approve.complete } onClick={ () => approve.issue() }>Approve</button>
 *     approve.refusalsOf("comment")
 *
 * Every part of the view that knows an argument sets it -- a form its row's subject, a picker another subject, a
 * grid's selection a list subject, a button an input value right before it issues -- and the action is complete
 * once every required parameter has one. A required Boolean starts out false, being one GraphQL can't leave out.
 *
 * The name is a string literal, and the call sits at the view's top level. The arguments are typed by the
 * `Actions` map, like issueAction()'s.
 *
 * @param name      name of the action
 * @param initial   arguments to start with, read on the first render only
 *
 * @returns the current state of the action
 */
export function useActionDraft<N extends ActionName>(name: N, initial?: Partial<ActionArgs<N>>): ActionDraft<N>
{
    const [store] = useState(() => new ActionDraftStore(name, initial))

    return useSyncExternalStore(store.subscribe, store.getSnapshot)
}


/**
 * The state behind useActionDraft(): the arguments, and whoever re-renders when they change.
 */
class ActionDraftStore<N extends ActionName>
{
    private readonly parameters: readonly ActionParameter[]

    private args: Record<string, unknown>

    private outcome: ActionOutcome<ActionResult<N>> | null = null

    private pending: Promise<ActionOutcome<ActionResult<N>>> | null = null

    private snapshot: ActionDraft<N> | null = null

    private subscribers: (() => void)[] = []


    constructor(private readonly name: N, initial: Partial<ActionArgs<N>> = {})
    {
        this.parameters = actionParameters(name)

        this.args = {}
        for (const parameter of this.parameters)
        {
            if (parameter.required && !parameter.list && parameter.type === "Boolean")
            {
                this.args[parameter.name] = false
            }
        }
        Object.assign(this.args, initial)
    }


    subscribe = (fn: () => void) =>
    {
        this.subscribers.push(fn)

        return () =>
        {
            this.subscribers = this.subscribers.filter(s => s !== fn)
        }
    }


    getSnapshot = (): ActionDraft<N> =>
    {
        if (!this.snapshot)
        {
            const missing = this.missing()
            const outcome = this.outcome

            this.snapshot = {
                name: this.name,
                args: this.args as Partial<ActionArgs<N>>,
                set: this.set,
                missing,
                complete: missing.length === 0,
                pending: this.pending !== null,
                outcome,
                refusalsOf: path => outcome?.refused ? refusalsOf(outcome.refusals, path) : [],
                issue: this.issue
            }
        }
        return this.snapshot
    }


    private set = (parameter: string, value: unknown): void =>
    {
        if (!this.parameters.some(p => p.name === parameter))
        {
            throw new Error("Action '" + this.name + "' has no parameter '" + parameter + "'")
        }
        this.args = {...this.args, [parameter]: value}
        this.notify()
    }


    private issue = (): Promise<ActionOutcome<ActionResult<N>>> =>
    {
        if (this.pending)
        {
            return this.pending
        }

        const missing = this.missing()
        if (missing.length)
        {
            return Promise.reject(
                new Error("Action '" + this.name + "' is missing " + missing.map(m => "'" + m + "'").join(", "))
            )
        }

        const pending = issueAction(this.name, this.args as ActionArgs<N>)
            .then(
                outcome => {
                    this.outcome = outcome
                    return outcome
                }
            )
            .finally(() => {
                this.pending = null
                this.notify()
            })

        this.pending = pending
        this.notify()

        return pending
    }


    /**
     * The required parameters without an argument. A list subject without rows is one: an action about no row at
     * all is never what a user asked for.
     */
    private missing(): string[]
    {
        return this.parameters
            .filter(parameter => {
                if (!parameter.required)
                {
                    return false
                }
                const value = this.args[parameter.name]
                return value == null || (parameter.subject && parameter.list && (value as unknown[]).length === 0)
            })
            .map(parameter => parameter.name)
    }


    private notify(): void
    {
        this.snapshot = null
        for (const subscriber of this.subscribers)
        {
            subscriber()
        }
    }
}
