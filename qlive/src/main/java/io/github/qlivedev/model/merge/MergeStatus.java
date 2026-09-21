package io.github.qlivedev.model.merge;

/**
 * How a merge ended.
 *
 * Two outcomes and no third one. Everything that is neither -- a type nobody exposes, a field name that
 * matches no column, a foreign key pointing at nothing -- is a programming error rather than a state of the
 * data, and comes back as a GraphQL error instead of as a status a caller has to branch on.
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
    CONFLICT
}
