package io.github.qlivedev.qlive.runtime.merge;

import io.github.qlivedev.qlive.model.merge.MergeConflict;
import io.github.qlivedev.qlive.runtime.QLiveException;

import java.util.List;

/// A write through the {@link EntityWriter} met somebody else's: the conflicts the merge reports, as an exception,
/// so that it rolls back the transaction the write joined.
///
/// Inside an action the framework turns it into a `CONFLICT` refusal. A job decides for itself whether to retry.
public class MergeConflictException
    extends QLiveException
{
    private static final long serialVersionUID = 1L;

    private final List<MergeConflict> conflicts;


    public MergeConflictException(List<MergeConflict> conflicts)
    {
        super("Merge conflicts: " + conflicts);
        this.conflicts = List.copyOf(conflicts);
    }


    public List<MergeConflict> getConflicts()
    {
        return conflicts;
    }
}
