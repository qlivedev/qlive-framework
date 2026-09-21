package io.github.qlivedev.runtime.pubsub;

import io.github.qlivedev.runtime.merge.EntityVersion;
import io.github.qlivedev.runtime.merge.EntityVersionsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/// Pub/sub's first consumer: what a merge wrote, on a channel.
///
/// The merge already publishes the record a subscriber needs -- which type, which row, the new version and
/// the mask of what changed -- so this listens for the event the version cache already listens for and needs
/// nothing new from the merge at all.
///
/// It listens after commit, deliberately, where {@link io.github.qlivedev.runtime.merge.VersionHolder}'s
/// listener does not. Holding a record of a merge that then rolled back costs nothing, because no row ever
/// named that version; telling somebody's browser a row changed when it did not is a side effect nothing
/// can take back.
///
/// The channel carries {@link EntityVersion} itself. It is flat -- no relations -- a plain bean with plain
/// properties, like every other channel's class, and what a subscriber filters on is exactly the JSON it
/// receives.
public class EntityVersionPublisher
{
    private final static Logger log = LoggerFactory.getLogger(EntityVersionPublisher.class);

    /// The channel every merge's records go to. A fixed name, because there is one of it and a client has
    /// to be able to name it without asking.
    public final static String TOPIC = "EntityVersion";

    private final PubSubService pubSub;


    public EntityVersionPublisher(PubSubService pubSub)
    {
        this.pubSub = pubSub;

        // Registered here rather than left to the first publish, so that a client can subscribe before
        // anybody has merged anything -- which is exactly when a client subscribes.
        pubSub.register(TOPIC, EntityVersion.class);
    }


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVersions(EntityVersionsEvent event)
    {
        for (EntityVersion version : event.getVersions())
        {
            log.debug("[DEBUG push] publishing {}", version);
            pubSub.publish(TOPIC, version);
        }

        log.debug("Published {} version record(s) on '{}'", event.getVersions().size(), TOPIC);
    }
}
