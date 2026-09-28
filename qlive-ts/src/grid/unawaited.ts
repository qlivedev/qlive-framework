/**
 * Lets go of the promise of an update() nobody waits for, a click handler's or a timer's. A query document
 * keeps the failure as its `error`, where the view sees it, so catching here hides nothing and leaves no
 * unhandled rejection behind.
 *
 * @param promise   the update() or what a hook made of it
 */
export function unawaited(promise: Promise<unknown>): void
{
    promise.catch(() => {});
}
