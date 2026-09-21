package io.github.qlivedev.runtime.merge;

import java.sql.Timestamp;

/// The version records this node has seen, in memory.
///
/// A cache and never the truth. What it saves is the read the chain walk would otherwise do per hop, in the
/// case that is by far the most common -- the other write was made here, minutes ago, by somebody on the
/// same node. A record it does not have is read from `app_version`, which is also why it is free to hold
/// less than the table does.
///
/// It fills itself by listening for {@link EntityVersionsEvent} rather than by being written to, which is
/// what makes it one listener among however many a later push module adds rather than a step of the merge.
public interface VersionHolder
{
    /// The record under the given version id, or null where this node has not got it. Null is not "no such
    /// version" -- ask the table before concluding that.
    EntityVersion get(String versionId);


    /// Drops every record made before the given point, and answers how many went. Called by the same task
    /// that prunes the table.
    int dropOlderThan(Timestamp before);


    /// How many records are held. For the cleanup to log and for a test to look at.
    int size();
}
