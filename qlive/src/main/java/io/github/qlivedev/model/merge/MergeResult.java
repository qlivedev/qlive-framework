package io.github.qlivedev.model.merge;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * What came of one merge.
 *
 * All or nothing: either every change and every deletion landed, or none of them did and the conflicts say
 * which rows stood in the way. There is no partial success to reconcile, which is what lets a working set
 * keep holding exactly what the user has not saved yet.
 */
public class MergeResult
{
    private MergeStatus status;

    private List<MergeConflict> conflicts;


    /**
     * Whether the merge landed.
     */
    @NotNull
    public MergeStatus getStatus()
    {
        return status;
    }


    public void setStatus(MergeStatus status)
    {
        this.status = status;
    }


    /**
     * One entry per row that could not be written. Empty when the merge is done.
     */
    @NotNull
    public List<MergeConflict> getConflicts()
    {
        return conflicts;
    }


    public void setConflicts(List<MergeConflict> conflicts)
    {
        this.conflicts = conflicts;
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "status = " + status
            + ", conflicts = " + conflicts
            ;
    }
}
