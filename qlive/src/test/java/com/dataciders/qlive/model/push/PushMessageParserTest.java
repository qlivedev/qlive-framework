package com.dataciders.qlive.model.push;

import com.dataciders.qlive.model.condition.Condition;
import com.dataciders.qlive.model.condition.Field;
import com.dataciders.qlive.model.condition.Value;
import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.util.JSONUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.dataciders.qlive.runtime.scalar.FilterDSL.field;
import static com.dataciders.qlive.runtime.scalar.FilterDSL.value;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Covers the message model on its own: no socket, no registry, no Spring context. Every kind is chosen by
/// the "type" it carries, and the one field that is not described by the kind -- a pub/sub payload -- is
/// deliberately left untyped until somebody asks for it by class.
class PushMessageParserTest
{
    private final PushMessageParser parser = new PushMessageParser();


    /// A Subscribe carries a FilterDSL condition, which is a second hierarchy with a discriminator of its
    /// own. Both are dispatched in the same pass, each mapper scoped to its own base type.
    @Test
    void readsASubscribeAndItsCondition()
    {
        final Subscribe subscribe = (Subscribe) parser.parseClientMessage(
            """
            {
                "type": "Subscribe",
                "topic": "EntityVersion",
                "id": "sub-1",
                "condition": {
                    "type": "Condition",
                    "name": "eq",
                    "operands": [
                        { "type": "Field", "name": "entityType" },
                        { "type": "Value", "scalarType": "String", "value": "Bar" }
                    ]
                }
            }
            """
        );

        assertThat(subscribe.getTopic(), is("EntityVersion"));
        assertThat(subscribe.getId(), is("sub-1"));
        assertThat(subscribe.getType(), is("Subscribe"));

        final Condition condition = (Condition) subscribe.getCondition();
        assertThat(condition.getName(), is("eq"));
        assertThat(((Field) condition.getOperands().get(0)).getName(), is("entityType"));
        assertThat(((Value) condition.getOperands().get(1)).getValue(), is("Bar"));
    }


    /// A subscription with no condition takes everything the channel carries. Nothing about that is a
    /// missing value to fill in later.
    @Test
    void readsASubscribeWithoutACondition()
    {
        final Subscribe subscribe = (Subscribe) parser.parseClientMessage(
            "{ \"type\": \"Subscribe\", \"topic\": \"EntityVersion\", \"id\": \"sub-1\" }"
        );

        assertThat(subscribe.getCondition(), is(nullValue()));
    }


    @Test
    void roundTripsEveryClientMessage()
    {
        final Subscribe subscribe = new Subscribe();
        subscribe.setTopic("EntityVersion");
        subscribe.setId("sub-1");
        subscribe.setCondition(field("fieldMask").bitAnd(value(6)).ne(value(0)));

        final Subscribe readSubscribe = (Subscribe) roundTrip(subscribe);
        assertThat(readSubscribe.getTopic(), is("EntityVersion"));
        assertThat(readSubscribe.getId(), is("sub-1"));
        assertThat(((Condition) readSubscribe.getCondition()).getName(), is("ne"));

        final Unsubscribe unsubscribe = new Unsubscribe();
        unsubscribe.setTopic("EntityVersion");
        unsubscribe.setId("sub-1");

        final Unsubscribe readUnsubscribe = (Unsubscribe) roundTrip(unsubscribe);
        assertThat(readUnsubscribe.getTopic(), is("EntityVersion"));
        assertThat(readUnsubscribe.getId(), is("sub-1"));

        final Publish publish = new Publish();
        publish.setTopic("Chat");
        publish.setMessage(Map.of("name", "Bar #1"));

        final Publish readPublish = (Publish) roundTrip(publish);
        assertThat(readPublish.getTopic(), is("Chat"));
        assertThat(readPublish.getMessage(), is(Map.of("name", "Bar #1")));
    }


    /// The outbound kinds get the same treatment. Nothing server-side parses one -- a Topic message is
    /// serialized on the way out and never read back -- but both directions are the same facility, and a
    /// server-side listener receiving its own published message is the shape of thing that would.
    @Test
    void roundTripsEveryServerMessage()
    {
        final Topic topic = new Topic();
        topic.setTopic("EntityVersion");
        topic.setIds(List.of("sub-1", "sub-2"));
        topic.setPayload(Map.of("entityId", "42"));

        final Topic readTopic = (Topic) roundTripServer(topic);
        assertThat(readTopic.getTopic(), is("EntityVersion"));
        assertThat(readTopic.getIds(), contains("sub-1", "sub-2"));
        assertThat(readTopic.getPayload(), is(Map.of("entityId", "42")));

        final Subscribed subscribed = new Subscribed();
        subscribed.setTopic("EntityVersion");
        subscribed.setId("sub-1");

        final Subscribed readSubscribed = (Subscribed) roundTripServer(subscribed);
        assertThat(readSubscribed.getTopic(), is("EntityVersion"));
        assertThat(readSubscribed.getId(), is("sub-1"));

        final Error error = new Error();
        error.setTopic("EntityVersion");
        error.setId("sub-1");
        error.setMessage("Filter operator 'isDistinctFrom' has no meaning off the database");

        final Error readError = (Error) roundTripServer(error);
        assertThat(readError.getTopic(), is("EntityVersion"));
        assertThat(readError.getId(), is("sub-1"));
        assertThat(readError.getMessage(), is("Filter operator 'isDistinctFrom' has no meaning off the database"));
    }


    /// Each kind travels one way, and asking for it the wrong way round says so rather than failing with a
    /// cast somewhere downstream.
    @Test
    void refusesAMessageTravellingTheOtherWay()
    {
        final String json = "{ \"type\": \"Subscribed\", \"topic\": \"EntityVersion\", \"id\": \"sub-1\" }";

        assertThat(parser.parseServerMessage(json), is(instanceOf(Subscribed.class)));
        assertThrows(QLiveException.class, () -> parser.parseClientMessage(json));
    }


    /// The payload is the one field the message kind does not describe, so the parse leaves it as the map
    /// it arrived as -- no registry consulted, nothing to look anything up in yet.
    @Test
    void leavesAPayloadUntyped()
    {
        final Publish publish = (Publish) parser.parseClientMessage(
            """
            {
                "type": "Publish",
                "topic": "TestPayload",
                "message": { "name": "Bar #1", "count": 3, "tags": [ "a", "b" ] }
            }
            """
        );

        assertThat(publish.getMessage(), is(instanceOf(Map.class)));

        final Map<?, ?> message = (Map<?, ?>) publish.getMessage();
        assertThat(message.get("name"), is("Bar #1"));
        assertThat(message.get("count"), is(3L));
        assertThat(message.get("tags"), is(List.of("a", "b")));
    }


    /// And a caller that wants compile-time field access asks for it by channel, which is the first point
    /// at which the topic-to-class binding is needed at all.
    @Test
    void typesAPayloadAgainstItsChannel()
    {
        final Publish publish = (Publish) parser.parseClientMessage(
            """
            {
                "type": "Publish",
                "topic": "TestPayload",
                "message": { "name": "Bar #1", "count": 3, "tags": [ "a", "b" ] }
            }
            """
        );

        final Object typed = PayloadRecast.typed(publish, topic -> TestPayload.class);

        assertThat(typed, is(instanceOf(TestPayload.class)));

        final TestPayload payload = (TestPayload) typed;
        assertThat(payload.getName(), is("Bar #1"));
        assertThat(payload.getCount(), is(3L));
        assertThat(payload.getTags(), contains("a", "b"));
    }


    @Test
    void refusesToTypeAPayloadOnAnUnregisteredChannel()
    {
        final Publish publish = new Publish();
        publish.setTopic("NoSuchChannel");
        publish.setMessage(Map.of("name", "Bar #1"));

        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> PayloadRecast.typed(publish, topic -> null)
        );

        assertThat(e.getMessage().contains("NoSuchChannel"), is(true));
    }


    private ClientMessage roundTrip(ClientMessage message)
    {
        return parser.parseClientMessage(JSONUtil.DEFAULT_GENERATOR.forValue(message));
    }


    private ServerMessage roundTripServer(ServerMessage message)
    {
        return parser.parseServerMessage(JSONUtil.DEFAULT_GENERATOR.forValue(message));
    }
}
