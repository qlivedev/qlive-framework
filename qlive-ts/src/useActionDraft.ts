import {useLayoutEffect, useState, useSyncExternalStore} from "react";
import {WorkingSet, workingSetOf} from "./merge/WorkingSet";
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
     * @param value         argument, or null for none
     */
    set<K extends keyof ActionArgs<N> & string>(parameter: K, value: ActionArgs<N>[K] | null): void

    /**
     * the required parameters that have no argument yet, in the method's order
     */
    readonly missing: readonly string[]

    /**
     * the subject parameters whose argument is a draft with changes that aren't saved: an action is about rows as
     * they are stored, so those have to be saved first
     */
    readonly unsaved: readonly string[]

    /**
     * whether nothing is missing and nothing unsaved, so that issuing can go ahead
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
     * The refusals of the last issue that no part of the view shows, see claim(): those blaming no field, and those
     * blaming one nothing claimed. What ActionRefusals shows.
     */
    readonly unclaimed: readonly Refusal[]

    /**
     * Tells the action that a part of the view shows the refusals of the given path and those below it, as a Field
     * does for its own. Returns what tells it the part is gone. useRefusals() claims for a component.
     *
     * @param path  field path starting with a parameter name
     */
    claim(path: string): () => void

    /**
     * Issues the action with the arguments set. Resolves with the outcome, which is also the state's from then on,
     * and rejects without a request where something is missing or a subject's changes aren't saved. Issuing while the action is pending resolves with
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
 * A subject is a row as it is stored. One given as a working set's draft holds the action back while it has changes
 * that aren't saved (`unsaved`). Setting a subject to other rows drops the outcome of the last issue, whose refusals
 * were about the rows it was issued for; the same rows read again keep it.
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
 * The refusals of the last issue blaming the given field or one below it, claimed for the calling component: they
 * are shown where it shows them, and not among the action's unclaimed ones (ActionRefusals).
 *
 *     const reasons = useRefusals(approveAll, "bars." + index)
 *
 * Rules of hooks apply.
 *
 * @param draft     the action draft
 * @param path      field path starting with a parameter name
 */
export function useRefusals(draft: ActionDraft<any>, path: string): Refusal[]
{
    const {claim} = draft
    useLayoutEffect(() => claim(path), [claim, path])
    return draft.refusalsOf(path)
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

    /** how many parts claim each path */
    private readonly claims = new Map<string, number>()

    /** the working sets of the drafts among the subjects, watched while anybody watches this */
    private readonly watched = new Map<WorkingSet, () => void>()


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
        this.watch()

        return () =>
        {
            this.subscribers = this.subscribers.filter(s => s !== fn)
            this.watch()
        }
    }


    getSnapshot = (): ActionDraft<N> =>
    {
        if (!this.snapshot)
        {
            const missing = this.missing()
            const unsaved = this.unsaved()
            const outcome = this.outcome

            this.snapshot = {
                name: this.name,
                args: this.args as Partial<ActionArgs<N>>,
                set: this.set,
                missing,
                unsaved,
                complete: missing.length === 0 && unsaved.length === 0,
                pending: this.pending !== null,
                outcome,
                refusalsOf: path => outcome?.refused ? refusalsOf(outcome.refusals, path) : [],
                unclaimed: outcome?.refused ? outcome.refusals.filter(refusal => !this.claimed(refusal.field)) : [],
                claim: this.claim,
                issue: this.issue
            }
        }
        return this.snapshot
    }


    private set = (parameter: string, value: unknown): void =>
    {
        const definition = this.parameters.find(p => p.name === parameter)
        if (!definition)
        {
            throw new Error("Action '" + this.name + "' has no parameter '" + parameter + "'")
        }
        if (this.args[parameter] === value)
        {
            return
        }
        const before = this.args[parameter]
        this.args = {...this.args, [parameter]: value}
        if (definition.subject)
        {
            if (idsOf(before) !== idsOf(value))
            {
                this.outcome = null
            }
            this.watch()
        }
        this.notify()
    }


    private claim = (path: string): (() => void) =>
    {
        this.claims.set(path, (this.claims.get(path) ?? 0) + 1)
        this.claimsChanged()

        return () => {
            const count = this.claims.get(path)! - 1
            count ? this.claims.set(path, count) : this.claims.delete(path)
            this.claimsChanged()
        }
    }


    /** what changes with a claim is which refusals are unclaimed, and there are none without a refusal */
    private claimsChanged(): void
    {
        if (this.outcome?.refused)
        {
            this.notify()
        }
    }


    private claimed(field: string | null): boolean
    {
        if (field === null)
        {
            return false
        }
        for (const path of this.claims.keys())
        {
            if (field === path || field.startsWith(path + "."))
            {
                return true
            }
        }
        return false
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
        const unsaved = this.unsaved()
        if (unsaved.length)
        {
            return Promise.reject(
                new Error(
                    "Action '" + this.name + "': save " + unsaved.map(u => "'" + u + "'").join(", ") + " first. An " +
                    "action is about rows as they are stored."
                )
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


    /**
     * The subject parameters with a draft among their rows that has changes not saved yet.
     */
    private unsaved(): string[]
    {
        return this.parameters
            .filter(parameter => parameter.subject && this.drafts(parameter.name).some(draft => {
                const merge = workingSetOf(draft).accessor(draft)
                return merge.isNew || merge.deleted || merge.changedFields().length > 0
            }))
            .map(parameter => parameter.name)
    }


    /** the rows of a subject's argument that are drafts of a working set */
    private drafts(parameter: string): object[]
    {
        const value = this.args[parameter]
        const rows = Array.isArray(value) ? value : value == null ? [] : [value]
        return rows.filter(row => {
            try
            {
                workingSetOf(row)
                return true
            }
            catch
            {
                return false
            }
        })
    }


    /**
     * Follows the working sets of the subjects' drafts while anybody watches the action, since a draft becoming
     * unsaved or saved changes whether the action is complete.
     */
    private watch(): void
    {
        const wanted = new Set<WorkingSet>()
        if (this.subscribers.length)
        {
            for (const parameter of this.parameters.filter(p => p.subject))
            {
                this.drafts(parameter.name).forEach(draft => wanted.add(workingSetOf(draft)))
            }
        }

        for (const [workingSet, unsubscribe] of this.watched)
        {
            if (!wanted.has(workingSet))
            {
                unsubscribe()
                this.watched.delete(workingSet)
            }
        }
        for (const workingSet of wanted)
        {
            if (!this.watched.has(workingSet))
            {
                this.watched.set(workingSet, workingSet.subscribe(() => this.notify()))
            }
        }
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


/**
 * The ids of a subject's rows, in their order, which is what its refusals' paths count by.
 */
function idsOf(value: unknown): string
{
    const rows = Array.isArray(value) ? value : value == null ? [] : [value]
    return JSON.stringify(rows.map(row => (row as { id?: unknown }).id ?? null))
}
