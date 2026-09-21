package io.github.qlivedev.runtime.merge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

import java.sql.Timestamp;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// Default {@link VersionHolder}: a map, filled by the merge's own event.
///
/// Nothing here is transactional. The event is published inside the merge's transaction, and a listener that
/// wants to hear only about committed merges says so with `@TransactionalEventListener` -- this one does not,
/// because holding a record of a merge that then rolled back costs nothing: the version it names was never
/// written to any row, so no chain walk will ever ask for it.
public class DefaultVersionHolder
    implements VersionHolder
{
    private final static Logger log = LoggerFactory.getLogger(DefaultVersionHolder.class);

    private final Map<String, EntityVersion> byId = new ConcurrentHashMap<>();


    @EventListener
    public void onVersions(EntityVersionsEvent event)
    {
        for (EntityVersion version : event.getVersions())
        {
            byId.put(version.getId(), version);
        }

        log.debug("Holding {} version records", byId.size());
    }


    @Override
    public EntityVersion get(String versionId)
    {
        return versionId == null ? null : byId.get(versionId);
    }


    @Override
    public int dropOlderThan(Timestamp before)
    {
        int dropped = 0;

        for (Iterator<EntityVersion> it = byId.values().iterator(); it.hasNext(); )
        {
            if (it.next().getCreated().before(before))
            {
                it.remove();
                dropped++;
            }
        }

        return dropped;
    }


    @Override
    public int size()
    {
        return byId.size();
    }
}
