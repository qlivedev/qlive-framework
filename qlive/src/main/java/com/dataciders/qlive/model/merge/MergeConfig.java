package com.dataciders.qlive.model.merge;

/**
 * What the caller of a merge says about itself.
 *
 * Everything a type decides about merging is type meta data and is declared once, in the
 * application's MergeMetadataProvider. What is in here is the other half: the same working set can be
 * submitted by a form with a user in front of it and by a service with nobody there, and the two want
 * different things back from a conflict.
 */
public class MergeConfig
{
    private boolean resolveConflicts;


    /**
     * true if the caller can put a conflict in front of a user and take a decision back. Only then does a
     * conflict come back carrying both values per field; otherwise it names the fields that clashed and
     * nothing else.
     *
     * The type has to agree: a conflict carries values where this is set and the type declared that
     * it resolves conflicts in the view. A caller that cannot show two values has no use for the stored row
     * and every reason not to be handed a copy of it.
     */
    public boolean isResolveConflicts()
    {
        return resolveConflicts;
    }


    public void setResolveConflicts(boolean resolveConflicts)
    {
        this.resolveConflicts = resolveConflicts;
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "resolveConflicts = " + resolveConflicts
            ;
    }
}
