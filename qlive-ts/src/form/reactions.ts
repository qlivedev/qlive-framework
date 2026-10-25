/*
 * Reactions: what a form does when a field changes or loses focus, or an error appears, as data in its layout.
 */
import config from "../config";
import {conditionPredicate, expressionValue} from "../evaluate";
import type {CNode, FilterExpression} from "../FilterDSL";
import {objectFields, unwrapAll} from "../type-utils";
import {scalarEqual} from "../util/scalar";
import type {FieldMode} from "./fieldMode";
import type {FormContext, FormState} from "./FormContext";
import {fieldState} from "./useField";

/**
 * What a reaction answers.
 *
 * - `{init: true}` -- the form shows a row, so that what the row opens with holds from the start
 * - `{change: path}` -- the form changed the field's value: the user, through a widget, or a reaction. A value equal
 *   to the one the field holds is no change. What somebody else saved, and the working set's undo(), are none either.
 * - `{blur: path}` -- focus left the field
 * - `{error: errorClass}` -- an error of the class appeared, `cleared: true` for one that cleared
 * - `{errorAt: path}` -- an error blaming the field or one below it appeared, `cleared: true` for one that cleared
 *
 * The errors are the invariants the row breaks and the refusals of the server, of a save and of the action's last
 * issue alike: what the form's fields show. An error present when the form shows the row appears then, after the
 * `init` reactions.
 */
export type ReactionEvent =
    { init: true } |
    { change: string } |
    { blur: string } |
    { error: string, cleared?: boolean } |
    { errorAt: string, cleared?: boolean }

/**
 * An effect computing a field's value, for what a FilterDSL expression can't say: a lookup, postal code to city. It
 * reads what it needs through the form's context, is given the path of the field it sets, so that one function can
 * serve several fields, and returns the value or a promise of it. Of several triggered for one field, the latest
 * wins, and the result of an earlier one still pending is dropped.
 */
export type ReactionFunction = (form: FormContext, path: string) => unknown | PromiseLike<unknown>

/**
 * What a reaction does to one field, by path as useField() takes it.
 *
 * - `mode` -- sets the field's mode
 * - `value` -- sets the field's value to a FilterDSL expression over the row: `field("net").add(field("tax"))`,
 *   `value(null, "String")`. A value is read as the field's type.
 * - `effect` -- sets the field's value to what a function returns, the function itself or the name it is registered
 *   under (registerEffect()), which is what a layout from the server names
 */
export type ReactionEffect =
    { field: string, mode: FieldMode } |
    { field: string, value: CNode } |
    { field: string, effect: ReactionFunction | string }

/**
 * An event wired to effects: what a form does to help the user bring the row into compliance with the rules. It is
 * enforced nowhere, and reaches the server only through the values it sets, which the merge checks like any edit.
 *
 *     // a continuous binding: gross follows net
 *     { on: [{init: true}, {change: "net"}], effects: [{field: "gross", value: field("net").mul(value(1.19))}] }
 *
 *     // a mode toggle
 *     {
 *         on: [{init: true}, {change: "otherAddress"}],
 *         when: field("otherAddress").eq(value(true)),
 *         effects: [{field: "street", mode: "normal"}],
 *         otherwise: [{field: "street", mode: "disabled"}]
 *     }
 */
export interface Reaction
{
    /** the events it answers, any of them */
    on: ReactionEvent[]
    /**
     * The condition on the form's row under which the reaction has its `effects`; where it doesn't hold, the reaction
     * has those `otherwise`. A reaction without a condition always has its `effects`.
     */
    when?: FilterExpression | null
    /** what it does, in order */
    effects: ReactionEffect[]
    /** what it does where `when` doesn't hold */
    otherwise?: ReactionEffect[]
}

/** how many reactions one may set off in a row before they are taken for a cycle */
const MAX_CHAIN = 16

const effectRegistry = new Map<string, ReactionFunction>()

/**
 * Gives a function the name a layout's effect refers to it by. A layout written in TSX holds the function itself and
 * needs no name.
 *
 * @param name      effect name
 * @param effect    the function, given the form's context and the field's path, returning the field's value or a
 *                  promise of it
 */
export function registerEffect(name: string, effect: ReactionFunction): void
{
    effectRegistry.set(name, effect)
}

function namedEffect(name: string): ReactionFunction
{
    const effect = effectRegistry.get(name)
    if (!effect)
    {
        throw new Error("No effect is registered as " + JSON.stringify(name) + ". Register it with registerEffect().")
    }
    return effect
}

/**
 * One error a form shows: the field it blames by the form's path, null for none, and its class.
 *
 * @internal
 */
export interface FormError
{
    field: string | null
    errorClass: string | null
}

function isThenable(value: unknown): value is PromiseLike<unknown>
{
    return value !== null && (typeof value === "object" || typeof value === "function") &&
        typeof (value as any).then === "function"
}

/** whether an error event's errors are present among the form's errors */
function present(event: ReactionEvent, errors: readonly FormError[]): boolean | null
{
    if ("error" in event)
    {
        return errors.some(e => e.errorClass === event.error)
    }
    if ("errorAt" in event)
    {
        const path = event.errorAt
        return errors.some(e => e.field === path || (e.field?.startsWith(path + ".") ?? false))
    }
    return null
}

/** every field path a reaction names */
function paths(reaction: Reaction): string[]
{
    const named: string[] = []
    for (const event of reaction.on)
    {
        if ("change" in event)
        {
            named.push(event.change)
        }
        else if ("blur" in event)
        {
            named.push(event.blur)
        }
        else if ("errorAt" in event)
        {
            named.push(event.errorAt)
        }
    }
    return [...named, ...reaction.effects.map(e => e.field), ...(reaction.otherwise ?? []).map(e => e.field)]
}

/**
 * The reactions of one form, and what they remember: which values they set, the latest effect started for each
 * field, and which errors were present. The form hands it its state on every render and tells it what happened.
 *
 * @internal
 */
export class FormReactions
{
    reactions: readonly Reaction[] = []

    /** the form's state as of its latest render */
    form!: FormState

    /** the values reactions set, by path, while the field holds them */
    private readonly reacted = new Map<string, unknown>()

    /** the latest effect started for each field, so that an earlier one's result is dropped */
    private readonly runs = new Map<string, number>()

    /**
     * Whether the errors of each error event were present, by the indexes of reaction and event: reactions given
     * inline are new objects on every render.
     */
    private errorState = new Map<string, boolean>()

    /** how far the write going on is down a chain, 0 for none: a write by the user */
    private depth = 0

    /**
     * The form shows a row: checks what the reactions name, then runs those answering `init`.
     */
    init(): void
    {
        for (const reaction of this.reactions)
        {
            for (const path of paths(reaction))
            {
                fieldState(this.form, path)
            }
        }
        this.reacted.clear()
        this.errorState = new Map()
        this.fire(event => "init" in event, 0)
    }

    /**
     * The form changed a field's value. A change the user made forgets the value a reaction set there.
     */
    changed = (path: string): void =>
    {
        const depth = this.depth
        if (depth === 0)
        {
            this.reacted.delete(path)
        }
        this.fire(event => "change" in event && event.change === path, depth)
    }

    /**
     * Focus left a field.
     */
    blurred = (path: string): void =>
    {
        this.fire(event => "blur" in event && event.blur === path, 0)
    }

    /**
     * The errors the form shows as of its latest render: runs the reactions to those that appeared or cleared since.
     */
    errors(errors: readonly FormError[]): void
    {
        const before = this.errorState
        const now = new Map<string, boolean>()
        this.reactions.forEach((reaction, r) => reaction.on.forEach((event, e) => {
            const holds = present(event, errors)
            if (holds !== null)
            {
                now.set(r + ":" + e, holds)
            }
        }))
        this.errorState = now
        this.fire((event, key) => {
            const holds = now.get(key)
            return holds !== undefined && holds !== (before.get(key) ?? false) &&
                holds === !("cleared" in event && event.cleared)
        }, 0)
    }

    /**
     * Whether the field holds the value a reaction set: until the user changes it, and while it is among the row's
     * changes, which the working set's undo() and a save end.
     */
    holds = (path: string): boolean => fieldState(this.form, path).reacted

    /**
     * Whether the field holds the value a reaction set, given the value it holds and, for a field of the row, the
     * merge's name for it.
     */
    holdsValue = (path: string, value: unknown, mergeName: string | null): boolean =>
    {
        if (!this.reacted.has(path))
        {
            return false
        }
        const changed = mergeName === null || this.form.merge.changedFields().includes(mergeName)
        if (!changed || !scalarEqual(value, this.reacted.get(path)))
        {
            this.reacted.delete(path)
            return false
        }
        return true
    }

    /**
     * Runs the reactions one of whose events matches, in their order.
     *
     * @param matches   whether an event matches, given the event and its key, the indexes of reaction and event
     * @param depth     how far down a chain the reactions are
     */
    private fire(matches: (event: ReactionEvent, key: string) => boolean, depth: number): void
    {
        for (const [r, reaction] of this.reactions.entries())
        {
            if (!reaction.on.some((event, e) => matches(event, r + ":" + e)))
            {
                continue
            }
            if (depth >= MAX_CHAIN)
            {
                console.error(
                    "Reactions don't settle: " + MAX_CHAIN + " in a row, the last one setting " +
                    reaction.effects.map(e => e.field).join(", ") + ". Two reactions setting each other's fields only " +
                    "settle where a value comes back equal to the one there is."
                )
                return
            }
            const effects = this.holdsFor(reaction) ? reaction.effects : reaction.otherwise ?? []
            for (const effect of effects)
            {
                this.apply(effect, depth)
            }
        }
    }

    private holdsFor(reaction: Reaction): boolean
    {
        if (!reaction.when)
        {
            return true
        }
        const {row, domainType} = this.form
        if (!row || !domainType)
        {
            throw new Error("A reaction's condition reads the form's row, and the form has none.")
        }
        return conditionPredicate(domainType, reaction.when)(row)
    }

    private apply(effect: ReactionEffect, depth: number): void
    {
        const path = effect.field
        if ("mode" in effect)
        {
            this.form.setMode(path, effect.mode)
            return
        }

        let result: unknown
        if ("value" in effect)
        {
            const {row, domainType} = this.form
            if (!row || !domainType)
            {
                throw new Error("A reaction's value reads the form's row, and the form has none.")
            }
            result = expressionValue(domainType, effect.value, this.valueType(path))(row)
        }
        else
        {
            const fn = typeof effect.effect === "string" ? namedEffect(effect.effect) : effect.effect
            result = fn(this.form, path)
        }

        const run = (this.runs.get(path) ?? 0) + 1
        this.runs.set(path, run)
        if (isThenable(result))
        {
            result.then(
                value => {
                    if (this.runs.get(path) === run)
                    {
                        this.write(path, value, depth)
                    }
                },
                error => console.error("The effect on " + path + " failed:", error)
            )
        }
        else
        {
            this.write(path, result, depth)
        }
    }

    /** the scalar type of the value a field holds: its own, the foreign key's for a to-one relation */
    private valueType(path: string): string | null
    {
        const field = fieldState(this.form, path)
        if (field.kind === "scalar")
        {
            return field.fieldType
        }
        if (field.kind === "toOne")
        {
            const key = objectFields(field.domainType).find(f => f.name === field.merge.name)
            return key ? unwrapAll(key.type).name ?? null : null
        }
        return null
    }

    private write(path: string, value: unknown, depth: number): void
    {
        const field = fieldState(this.form, path)
        if (scalarEqual(field.value, value))
        {
            return
        }
        this.depth = depth + 1
        try
        {
            field.setValue(value)
        }
        finally
        {
            this.depth = 0
        }
        this.reacted.set(path, value)
    }
}

/**
 * The errors a form shows: the row's, as its merge state has them, and those of the action's last issue. A refusal
 * blaming the row's subject by name, `bar.description`, blames the field `description`.
 *
 * @internal
 */
export function formErrors(form: FormState): FormError[]
{
    const errors: FormError[] = []
    const {domainType, subject, action} = form
    for (const refusal of form.merge.refusals)
    {
        errors.push({field: refusal.field && domainType ? formPath(domainType, refusal.field) : null, errorClass: refusal.errorClass})
    }
    const outcome = action?.outcome
    if (outcome?.refused)
    {
        for (const refusal of outcome.refusals)
        {
            let field = refusal.field
            if (field !== null && subject !== null)
            {
                field = field === subject ? null : field.startsWith(subject + ".") ? field.substring(subject.length + 1) : field
            }
            errors.push({field, errorClass: refusal.errorClass})
        }
    }
    return errors
}

/** a field of the row as a form names it: a foreign key by its relation */
function formPath(type: string, field: string): string
{
    const relation = config().meta.relations.find(
        r => r.sourceType === type && r.leftSideObjectName && r.sourceFields.length === 1 && r.sourceFields[0] === field
    )
    return relation?.leftSideObjectName ?? field
}
