package io.github.qlivedev.qlive.model.merge;

/**
 * How a merge ended.
 *
 * Three outcomes: it landed, somebody else's write stood in the way, or the data broke an invariant of its
 * type. Everything else -- a type nobody exposes, a field name that matches no column, a foreign key pointing
 * at nothing, a change leaving out a field an invariant reads -- is a programming error rather than a state of
 * the data, and comes back as a GraphQL error instead of as a status a caller has to branch on.
 *
 * A unique constraint is the one refusal that is not in that list. A row refused for being a duplicate of
 * one already there is somebody else having got in first, which is the same thing every other conflict is,
 * and it comes back as one.
 */
public enum MergeStatus
{
    /**
     * Everything in the working set landed. Nothing else is written, and the transaction committed.
     */
    DONE,

    /**
     * At least one row could not be written as asked, and nothing was written at all. The result carries one
     * conflict per row that stood in the way.
     */
    CONFLICT,

    /**
     * At least one row broke an invariant of its type, and nothing was written at all. Checked before anything
     * is written, so a refused merge carries no conflicts; the result carries one refusal per broken rule and
     * row.
     */
    REFUSED
}
