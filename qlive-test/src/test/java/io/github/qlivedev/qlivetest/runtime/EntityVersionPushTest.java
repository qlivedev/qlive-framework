package io.github.qlivedev.qlivetest.runtime;

import io.github.qlivedev.model.merge.EntityChange;
import io.github.qlivedev.model.merge.FieldChange;
import io.github.qlivedev.model.merge.MergeConfig;
import io.github.qlivedev.runtime.merge.EntityVersion;
import io.github.qlivedev.runtime.merge.EntityVersionsEvent;
import io.github.qlivedev.runtime.merge.MergeService;
import io.github.qlivedev.runtime.merge.VersionHolder;
import io.github.qlivedev.runtime.auth.AppAuthentication;
import io.github.qlivedev.runtime.pubsub.EntityVersionPublisher;
import io.github.qlivedev.runtime.pubsub.PubSubService;
import io.github.qlivedev.runtime.push.Recipient;
import io.github.qlivedev.graphql.generic.GenericScalar;
import io.github.qlivedev.util.JSONUtil;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.qlivedev.runtime.scalar.FilterDSL.and;
import static io.github.qlivedev.runtime.scalar.FilterDSL.field;
import static io.github.qlivedev.runtime.scalar.FilterDSL.value;
import static io.github.qlivedev.qlivetest.domain.Tables.APP_VERSION;
import static io.github.qlivedev.qlivetest.domain.Tables.BAR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/// Push's first consumer, end to end from a merge: the records a merge wrote, on the "EntityVersion"
/// channel.
///
/// Subscribed in-process rather than over a websocket. What the transport does with a message is
/// {@link PushWebSocketTest}'s subject; what this is about is that a committed merge produces one, that it
/// carries what a subscriber needs, and that an uncommitted one produces nothing. How a record is spelled
/// on the wire and what a subscription can filter it by is qlive's EntityVersionPublisherTest, with no
/// merge involved.
@SpringBootTest
class EntityVersionPushTest
{
    @Autowired
    private MergeService mergeService;

    @Autowired
    private VersionHolder versionHolder;

    @Autowired
    private PubSubService pubSub;

    @Autowired
    private DSLContext dslContext;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private final Collecting subscriber = new Collecting();

    private final List<String> bars = new ArrayList<>();


    @BeforeEach
    void listen()
    {
        pubSub.subscribe(subscriber, EntityVersionPublisher.TOPIC, null, "test");
    }


    @AfterEach
    void removeWhatWasMade()
    {
        pubSub.unsubscribeAll(subscriber);

        dslContext.deleteFrom(BAR).where(BAR.ID.in(bars)).execute();
        dslContext.deleteFrom(APP_VERSION).where(APP_VERSION.ENTITY_ID.in(bars)).execute();
    }


    /// The whole of what a subscriber is told: which type, which row, the version it is now on, and the
    /// mask of the fields that moved.
    @Test
    void publishesWhatTheMergeWrote()
    {
        final String id = newId();
        merge(newBar(id, "Pushed #1", 1));

        assertThat(subscriber.received.size(), is(1));

        final Map<String, Object> record = subscriber.record(0);

        assertThat(record.get("entityType"), is("Bar"));
        assertThat(record.get("entityId"), is(id));
        assertThat(record.get("id"), is(dslContext.select(BAR.VERSION).from(BAR).where(BAR.ID.eq(id)).fetchOne(BAR.VERSION)));
        assertThat(record.get("ownerId"), is(notNullValue()));
        assertThat(record.get("prev"), is((Object) null));
    }


    /// The clause the client adds to all of that, and the case it does not cover: "not made by me" is
    /// per user, and two tabs of one login are one user. So a second tab of the same person is told
    /// nothing -- which is exactly how the two-tab check is run, and exactly what it must not do. What
    /// the feature wants is "not made by this tab", and nothing in the record says which tab.
    @Test
    void doesNotReachAnotherTabOfTheSameUser()
    {
        final String watched = newId();

        pubSub.subscribe(
            subscriber,
            EntityVersionPublisher.TOPIC,
            and(
                field("entityType").eq(value("Bar")),
                field("entityId").eq(value(watched)),
                field("ownerId").ne(value(AppAuthentication.current().getId()))
            ),
            "otherTab"
        );

        merge(newBar(watched, "Watched", 1));

        assertThat(
            subscriber.received.stream().filter(m -> m.getIds().contains("otherTab")).toList(),
            is(empty())
        );
    }


    /// Why this listener is a transactional one and the version cache's is not.
    ///
    /// The event is published here rather than merged, because a merge runs in a transaction of its own
    /// and commits whatever its caller does -- so the only way to show the difference the phase makes is
    /// to be the thing that publishes it. The cache hears the event either way, which is the point: a
    /// record of a version no row will ever name costs nothing, and a browser told a row changed when it
    /// did not cannot be told otherwise.
    @Test
    void publishesNothingForATransactionThatRolledBack()
    {
        final EntityVersion version = new EntityVersion(
            UUID.randomUUID().toString(),
            "Bar",
            UUID.randomUUID().toString(),
            null,
            BigInteger.ONE,
            "no layout of this deployment's",
            "nobody",
            // in the past, so that the version cleanup takes this back out again like any other record
            Timestamp.valueOf("2020-01-01 00:00:00")
        );

        new TransactionTemplate(transactionManager).executeWithoutResult(
            status ->
            {
                eventPublisher.publishEvent(new EntityVersionsEvent(this, List.of(version)));
                status.setRollbackOnly();
            }
        );

        assertThat(versionHolder.get(version.getId()), is(notNullValue()));
        assertThat(subscriber.received, is(empty()));
    }


    // -----------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> record(io.github.qlivedev.model.push.Topic message)
    {
        // read back as JSON, which is the form the payload actually reaches a client in
        return (Map<String, Object>) JSONUtil.DEFAULT_PARSER.parse(
            Map.class,
            JSONUtil.DEFAULT_GENERATOR.forValue(message.getPayload())
        );
    }


    private void merge(EntityChange... changes)
    {
        mergeService.merge(List.of(changes), List.of(), new MergeConfig());
    }


    private String newId()
    {
        final String id = UUID.randomUUID().toString();
        bars.add(id);

        return id;
    }


    private static EntityChange newBar(String id, String name, int num)
    {
        final EntityChange change = change(
            id,
            null,
            fieldChange("name", "String", name),
            fieldChange("num", "Int", num),
            fieldChange("created", "Timestamp", Timestamp.valueOf("2026-09-10 12:00:00"))
        );
        change.setNew(true);

        return change;
    }


    private static EntityChange change(String id, String version, FieldChange... fields)
    {
        final EntityChange change = new EntityChange();
        change.setType("Bar");
        change.setId(id);
        change.setVersion(version);
        change.setChanges(List.of(fields));

        return change;
    }


    private static FieldChange fieldChange(String name, String scalarType, Object value)
    {
        final FieldChange change = new FieldChange();
        change.setField(name);
        change.setValue(new GenericScalar(scalarType, value));

        return change;
    }


    private static final class Collecting
        implements Recipient
    {
        private final List<io.github.qlivedev.model.push.Topic> received = new ArrayList<>();


        @Override
        public void send(io.github.qlivedev.model.push.ServerMessage message)
        {
            received.add((io.github.qlivedev.model.push.Topic) message);
        }


        Map<String, Object> record(int index)
        {
            return EntityVersionPushTest.record(received.get(index));
        }
    }
}
