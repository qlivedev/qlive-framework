package io.github.qlivedev.runtime.pubsub;

import io.github.qlivedev.graphql.scalar.TimestampScalar;
import io.github.qlivedev.model.push.ServerMessage;
import io.github.qlivedev.runtime.merge.EntityVersion;
import io.github.qlivedev.runtime.merge.EntityVersionsEvent;
import io.github.qlivedev.runtime.push.Recipient;
import io.github.qlivedev.util.JSONUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.qlivedev.runtime.scalar.FilterDSL.and;
import static io.github.qlivedev.runtime.scalar.FilterDSL.field;
import static io.github.qlivedev.runtime.scalar.FilterDSL.value;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

/// What a subscriber to the "EntityVersion" channel is told about a merge, and what it can filter on.
///
/// The records are made here rather than by a merge, because what is under test is what becomes of one on the
/// way out. That a committed merge produces them, and an uncommitted one does not, is qlive-test's
/// EntityVersionPushTest, against a real database and a real transaction.
class EntityVersionPublisherTest
{
    /// Past the 53 bits a JavaScript number holds exactly, which is the whole reason for the string.
    private final static BigInteger WIDE_MASK = BigInteger.ONE.shiftLeft(100).or(BigInteger.valueOf(5));

    private final DefaultPubSubService pubSub = new DefaultPubSubService();

    private final EntityVersionPublisher publisher = new EntityVersionPublisher(pubSub);


    /// The whole of what a subscriber is told: which type, which row, the version it is now on, the one before
    /// it and who made the change.
    @Test
    void publishesEachRecordOfTheEvent()
    {
        final Collecting subscriber = subscribed(null);

        publish(version("v-1", "row-1", null, WIDE_MASK), version("v-2", "row-2", "v-0", WIDE_MASK));

        assertThat(subscriber.records().stream().map(r -> r.get("id")).toList(), contains("v-1", "v-2"));

        final Map<String, Object> record = subscriber.records().get(1);
        assertThat(record.get("entityType"), is("TestFoo"));
        assertThat(record.get("entityId"), is("row-2"));
        assertThat(record.get("prev"), is("v-0"));
        assertThat(record.get("ownerId"), is("owner-1"));
    }


    /// The mask is a decimal string on the wire, not a number: it is 128 bits wide and a JavaScript number
    /// holds 53 of them exactly. The client reads it with `BigInt`. The layout the positions were assigned by
    /// travels with it, because a client decoding the mask has to know which one it was written against.
    @Test
    void sendsTheFieldMaskAsADecimalString()
    {
        final Collecting subscriber = subscribed(null);

        publish(version("v-1", "row-1", null, WIDE_MASK));

        final Map<String, Object> record = subscriber.records().get(0);

        assertThat(record.get("fieldMask"), is(WIDE_MASK.toString()));
        assertThat(record.get("fieldLayout"), is("layout-1"));
    }


    /// The timestamp is the string the GraphQL schema's own Timestamp scalar produces, rather than a dump of
    /// `java.util.Date`'s getters, which is what Svenson makes of a `java.sql.Timestamp` left alone.
    @Test
    void sendsTheTimestampTheWayEveryOtherTimestampIsSent()
    {
        final Collecting subscriber = subscribed(null);

        final EntityVersion version = version("v-1", "row-1", null, WIDE_MASK);
        publish(version);

        assertThat(subscriber.records().get(0).get("created"), is(TimestampScalar.toISO8601(version.getCreated())));
    }


    /// The subscription the whole design is shaped around: this type, this row, these fields. Built entirely
    /// out of what the payload carries, with no coercion needed on the way in -- the ids are strings and the
    /// mask is a decimal string on both sides.
    @Test
    void filtersTheWayAFormOnScreenWould()
    {
        // bit 2 is the field on screen; bit 0 is one the form does not show
        final BigInteger onScreen = BigInteger.valueOf(4);

        final Collecting subscriber = subscribed(
            and(
                field("entityType").eq(value("TestFoo")),
                field("entityId").eq(value("watched")),
                field("fieldMask").bitAnd(value(onScreen.toString())).ne(value(0))
            )
        );

        publish(
            version("v-1", "watched", null, WIDE_MASK),
            version("v-2", "other", null, WIDE_MASK),
            version("v-3", "watched", "v-1", BigInteger.ONE)
        );

        // the row being watched, where a field on screen moved, and not the other row or the change that
        // left the screen alone
        assertThat(subscriber.records().stream().map(r -> r.get("id")).toList(), contains("v-1"));
    }


    // -----------------------------------------------------------------------------------------------------

    private Collecting subscribed(io.github.qlivedev.model.condition.CNode condition)
    {
        final Collecting subscriber = new Collecting();
        pubSub.subscribe(subscriber, EntityVersionPublisher.TOPIC, condition, "s1");

        return subscriber;
    }


    private void publish(EntityVersion... versions)
    {
        publisher.onVersions(new EntityVersionsEvent(this, List.of(versions)));
    }


    private static EntityVersion version(String id, String entityId, String prev, BigInteger mask)
    {
        return new EntityVersion(
            id,
            "TestFoo",
            entityId,
            prev,
            mask,
            "layout-1",
            "owner-1",
            Timestamp.valueOf("2026-09-10 12:00:00")
        );
    }


    private static final class Collecting
        implements Recipient
    {
        private final List<io.github.qlivedev.model.push.Topic> received = new ArrayList<>();


        @Override
        public void send(ServerMessage message)
        {
            received.add((io.github.qlivedev.model.push.Topic) message);
        }


        /// Each payload read back as JSON, which is the form it actually reaches a client in.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records()
        {
            return received.stream()
                .map(
                    message -> (Map<String, Object>) JSONUtil.DEFAULT_PARSER.parse(
                        Map.class,
                        JSONUtil.DEFAULT_GENERATOR.forValue(message.getPayload())
                    )
                )
                .toList();
        }
    }
}
