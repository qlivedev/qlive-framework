package io.github.qlivedev.runtime.merge;

import java.sql.Timestamp;
import java.util.List;

/// The version records: written by the merge, read back by the chain walk, pruned by the cleanup.
public interface VersionService
{
    /// The record under the given version id, or null where there is none -- pruned, never written, or
    /// written by something that is not this merge. Null is a state the chain walk has an answer for, not
    /// an error.
    EntityVersion get(String versionId);


    /// Writes the records of one merge and publishes them as an {@link EntityVersionsEvent}. Called inside
    /// the merge's transaction, so the records land with the rows they describe or not at all.
    void write(List<EntityVersion> versions);


    /// Drops the records made before the given point, and the field layouts no remaining record names.
    /// Answers how many records went.
    int prune(Timestamp before);
}
