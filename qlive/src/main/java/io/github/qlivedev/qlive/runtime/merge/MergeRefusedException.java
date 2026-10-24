package io.github.qlivedev.qlive.runtime.merge;

import io.github.qlivedev.qlive.model.merge.MergeRefusal;
import io.github.qlivedev.qlive.runtime.QLiveException;

import java.util.List;

/// A write through the {@link EntityWriter} broke an invariant of its type: the refusals the merge reports, as an
/// exception, so that it rolls back the transaction the write joined.
///
/// Inside an action the framework turns it into `RULE` refusals.
public class MergeRefusedException
    extends QLiveException
{
    private static final long serialVersionUID = 1L;

    private final List<MergeRefusal> refusals;


    public MergeRefusedException(List<MergeRefusal> refusals)
    {
        super("Merge refused: " + refusals);
        this.refusals = List.copyOf(refusals);
    }


    public List<MergeRefusal> getRefusals()
    {
        return refusals;
    }
}
