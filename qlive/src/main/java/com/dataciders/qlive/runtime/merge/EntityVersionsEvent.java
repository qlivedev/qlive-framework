package com.dataciders.qlive.runtime.merge;

import org.springframework.context.ApplicationEvent;

import java.util.List;

/// The version records of one committed merge, published once the transaction has gone through.
///
/// Published rather than handed over. The in-memory {@link VersionHolder} is one listener and it is the only
/// one today; a websocket push module would be the second, and the record it needs is the one already in
/// here. That is the difference between adding push and rewriting the merge, and it costs one event class.
///
/// Nothing is published for a merge that conflicted: it wrote nothing, so no row is in a state anybody has
/// to hear about.
public class EntityVersionsEvent
    extends ApplicationEvent
{
    private static final long serialVersionUID = 1L;

    private final List<EntityVersion> versions;


    public EntityVersionsEvent(Object source, List<EntityVersion> versions)
    {
        super(source);

        this.versions = List.copyOf(versions);
    }


    /// The records written, in the order the merge wrote the rows. Never empty.
    public List<EntityVersion> getVersions()
    {
        return versions;
    }


    @Override
    public String toString()
    {
        return "EntityVersionsEvent: " + versions;
    }
}
