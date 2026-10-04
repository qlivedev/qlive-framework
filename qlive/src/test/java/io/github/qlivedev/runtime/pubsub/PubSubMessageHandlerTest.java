package io.github.qlivedev.runtime.pubsub;

import io.github.qlivedev.model.push.PushMessageParser;
import io.github.qlivedev.model.push.ServerMessage;
import io.github.qlivedev.model.push.Subscribed;
import io.github.qlivedev.model.push.Topic;
import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.runtime.domain.TestDomainConfig;
import io.github.qlivedev.runtime.domain.TestLogic;
import io.github.qlivedev.runtime.pubsub.PubSubServiceTest.Note;
import io.github.qlivedev.runtime.push.Recipient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Pub/sub's side of the push protocol: frames as a client sends them, parsed the way the transport parses
/// them, and handled by the real service.
///
/// No socket and no Spring context. How a frame reaches a handler, and how a handler's refusal reaches the
/// client as an error, is {@link io.github.qlivedev.runtime.push.PushWebSocketHandlerTest}'s; that the whole
/// stack holds together behind a real login on a real socket is qlive-test's PushWebSocketTest.
class PubSubMessageHandlerTest
{
    private final static String TOPIC = "Note";

    private final PushMessageParser parser = new PushMessageParser();

    private final DefaultPubSubService pubSub = new DefaultPubSubService();

    private final PubSubMessageHandler handler = new PubSubMessageHandler(
        pubSub,
        TestDomainConfig.domain(new TestLogic())
    );

    private final Recording client = new Recording();


    PubSubMessageHandlerTest()
    {
        pubSub.register(TOPIC, Note.class);
    }


    /// A subscribe is acknowledged against the id it named, and from then on the channel's messages reach the
    /// connection that sent it, addressed to that id.
    @Test
    void acknowledgesASubscribeAndDeliversToIt()
    {
        receive(subscribe("s1", null));

        assertThat(client.sent.get(0), instanceOf(Subscribed.class));
        assertThat(((Subscribed) client.sent.get(0)).getId(), is("s1"));

        pubSub.publish(TOPIC, new Note("hello", "kim"));

        assertThat(client.topics().get(0).getIds(), contains("s1"));
        assertThat(((Note) client.topics().get(0).getPayload()).getText(), is("hello"));
    }


    /// A condition off the wire is JSON, and it filters each message the same as one built in process.
    @Test
    void filtersByTheConditionTheFrameCarried()
    {
        receive(
            subscribe(
                "mine",
                "{\"type\":\"Condition\",\"name\":\"eq\",\"operands\":[" +
                    "{\"type\":\"Field\",\"name\":\"author\"}," +
                    "{\"type\":\"Value\",\"scalarType\":\"String\",\"value\":\"kim\"}]}"
            )
        );

        pubSub.publish(TOPIC, new Note("not for you", "sam"));
        pubSub.publish(TOPIC, new Note("for you", "kim"));

        assertThat(
            client.topics().stream().map(message -> ((Note) message.getPayload()).getText()).toList(),
            contains("for you")
        );
    }


    @Test
    void stopsDeliveringAfterAnUnsubscribe()
    {
        receive(subscribe("s1", null));
        receive("{\"type\":\"Unsubscribe\",\"topic\":\"" + TOPIC + "\",\"id\":\"s1\"}");

        assertThat(pubSub.subscriptionCount(TOPIC), is(0));

        pubSub.publish(TOPIC, new Note("hello", "kim"));

        assertThat(client.topics(), is(empty()));
    }


    /// A closed connection takes every subscription it held with it. Automaton lost exactly this cleanup in a
    /// refactor once, and nothing about a subscription nobody is listening to makes a noise.
    @Test
    void sweepsAClosedConnectionsSubscriptions()
    {
        receive(subscribe("s1", null));
        receive(subscribe("s2", null));

        handler.closed(client);

        assertThat(pubSub.subscriptionCount(TOPIC), is(0));
    }


    /// Refused by throwing, which the transport turns into an error addressed to the subscription the frame
    /// named. Neither an acknowledgement nor a subscription is left behind.
    @Test
    void refusesASubscribeToAnUnknownChannel()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> receive("{\"type\":\"Subscribe\",\"topic\":\"NoSuchChannel\",\"id\":\"s1\"}")
        );

        assertThat(e.getMessage(), containsString("No such channel"));
        assertThat(client.sent, is(empty()));
    }


    /// What a client may publish is not settled, so for now nothing a client sends can look to another client
    /// like something the framework said.
    @Test
    void refusesAPublishFromAClient()
    {
        receive(subscribe("s1", null));

        assertThrows(
            QLiveException.class,
            () -> receive("{\"type\":\"Publish\",\"topic\":\"" + TOPIC + "\",\"message\":{\"text\":\"x\"}}")
        );

        assertThat(client.topics(), is(empty()));
    }


    // -----------------------------------------------------------------------------------------------------

    private void receive(String frame)
    {
        handler.handle(client, parser.parseClientMessage(frame));
    }


    private static String subscribe(String id, String condition)
    {
        return "{\"type\":\"Subscribe\",\"topic\":\"" + TOPIC + "\",\"id\":\"" + id + "\"" +
            (condition == null ? "" : ",\"condition\":" + condition) + "}";
    }


    private static final class Recording
        implements Recipient
    {
        private final List<ServerMessage> sent = new ArrayList<>();


        @Override
        public void send(ServerMessage message)
        {
            sent.add(message);
        }


        List<Topic> topics()
        {
            return sent.stream().filter(m -> m instanceof Topic).map(m -> (Topic) m).toList();
        }
    }
}
