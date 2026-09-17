package com.dataciders.qlive.runtime.pubsub;

import com.dataciders.qlive.model.push.ServerMessage;
import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.push.Recipient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.dataciders.qlive.runtime.scalar.FilterDSL.field;
import static com.dataciders.qlive.runtime.scalar.FilterDSL.value;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Covers the channel registry and the fan-out without a socket anywhere near it: a {@link Recipient} is
/// whatever collects messages, which here is a list.
class PubSubServiceTest
{
    private final DefaultPubSubService pubSub = new DefaultPubSubService();


    @Test
    void deliversToASubscriber()
    {
        pubSub.register("Note", Note.class);

        final Collecting sam = new Collecting();
        pubSub.subscribe(sam, "Note", null, "s1");

        pubSub.publish("Note", new Note("hello", "sam"));

        assertThat(sam.topics(), contains("Note"));
        assertThat(sam.ids(), contains(List.of("s1")));
        assertThat(((Note) sam.payloads().get(0)).getText(), is("hello"));
    }


    /// The channel is the class its payloads have, and a channel carrying two shapes would be a channel no
    /// subscriber's field path could be checked against.
    @Test
    void refusesAPayloadOfTheWrongClass()
    {
        pubSub.register("Note", Note.class);

        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> pubSub.publish("Note", "not a note")
        );

        assertThat(e.getMessage(), is(
            "Channel 'Note' carries " + Note.class.getName() + ", cannot publish a java.lang.String"
        ));
    }


    /// What a framework-internal publisher needs: it has no reason to know whether anybody is listening,
    /// and nothing it could do if it found out.
    @Test
    void publishingWithNobodyListeningIsNotAnError()
    {
        pubSub.register("Note", Note.class);

        pubSub.publish("Note", new Note("into the void", "sam"));
    }


    /// A channel nobody registered is created from what was published, which is what "channels are created
    /// lazily" amounts to. Nobody can have subscribed to it yet, so there is nothing to deliver.
    @Test
    void publishingCreatesAnUnregisteredChannel()
    {
        pubSub.publish("Note", new Note("first", "sam"));

        assertThat(pubSub.topicType("Note"), is((Object) Note.class));
    }


    /// Subscribing does not create one, though. A subscriber brings no class with it, so a channel created
    /// here could validate nothing -- and a misspelled channel name would look like one that is simply
    /// quiet.
    @Test
    void refusesToSubscribeToAChannelThatDoesNotExist()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> pubSub.subscribe(new Collecting(), "Notes", null, "s1")
        );

        assertThat(e.getMessage(), is("No such channel: 'Notes'"));
    }


    @Test
    void evaluatesEachSubscriptionsOwnCondition()
    {
        pubSub.register("Note", Note.class);

        final Collecting sam = new Collecting();
        final Collecting kim = new Collecting();

        pubSub.subscribe(sam, "Note", field("author").eq(value("sam")), "s1");
        pubSub.subscribe(kim, "Note", field("author").eq(value("kim")), "k1");

        pubSub.publish("Note", new Note("hello", "sam"));

        assertThat(sam.ids(), contains(List.of("s1")));
        assertThat(kim.ids(), is(empty()));
    }


    /// Everything the condition alone can get wrong is refused here, while whoever is registering the
    /// subscription is still listening.
    @Test
    void refusesAConditionThatNamesNoFieldOfTheChannelsClass()
    {
        pubSub.register("Note", Note.class);

        assertThrows(
            QLiveException.class,
            () -> pubSub.subscribe(new Collecting(), "Note", field("nope").eq(value("x")), "s1")
        );
    }


    /// One connection, several subscriptions on one channel: the payload goes out once, naming every id it
    /// matched, rather than once per match.
    @Test
    void batchesOneConnectionsMatchingSubscriptions()
    {
        pubSub.register("Note", Note.class);

        final Collecting sam = new Collecting();
        pubSub.subscribe(sam, "Note", field("author").eq(value("sam")), "byAuthor");
        pubSub.subscribe(sam, "Note", field("text").startsWith(value("hel")), "byText");
        pubSub.subscribe(sam, "Note", field("text").startsWith(value("bye")), "noMatch");

        pubSub.publish("Note", new Note("hello", "sam"));

        assertThat(sam.ids(), contains(List.of("byAuthor", "byText")));
    }


    /// A client whose screen changed re-subscribes under the id it already holds. That replaces the
    /// condition rather than adding a second subscription, so nothing arrives twice and nothing is missed
    /// in between.
    @Test
    void replacesASubscriptionRegisteredAgainUnderTheSameId()
    {
        pubSub.register("Note", Note.class);

        final Collecting sam = new Collecting();
        pubSub.subscribe(sam, "Note", field("author").eq(value("sam")), "s1");
        pubSub.subscribe(sam, "Note", field("author").eq(value("kim")), "s1");

        pubSub.publish("Note", new Note("hello", "sam"));
        assertThat(sam.ids(), is(empty()));

        pubSub.publish("Note", new Note("hello", "kim"));
        assertThat(sam.ids(), contains(List.of("s1")));
    }


    @Test
    void stopsDeliveringAfterAnUnsubscribe()
    {
        pubSub.register("Note", Note.class);

        final Collecting sam = new Collecting();
        pubSub.subscribe(sam, "Note", null, "s1");
        pubSub.unsubscribe(sam, "Note", "s1");

        pubSub.publish("Note", new Note("hello", "sam"));

        assertThat(sam.ids(), is(empty()));
    }


    @Test
    void unsubscribingSomethingThatIsNotThereIsNotAnError()
    {
        pubSub.register("Note", Note.class);

        pubSub.unsubscribe(new Collecting(), "Note", "never");
        pubSub.unsubscribe(new Collecting(), "NoSuchChannel", "never");
    }


    /// A closing connection has to lose every subscription it holds, on every channel -- not the ones
    /// somebody remembered to write down. Automaton lost exactly this cleanup in a refactor once, which is
    /// why it is a test and not a line of code somebody trusts.
    @Test
    void aClosedConnectionLosesEverySubscriptionItHeld()
    {
        pubSub.register("Note", Note.class);
        pubSub.register("Memo", Note.class);

        final Collecting sam = new Collecting();
        final Collecting kim = new Collecting();

        pubSub.subscribe(sam, "Note", null, "s1");
        pubSub.subscribe(sam, "Note", null, "s2");
        pubSub.subscribe(sam, "Memo", null, "s3");
        pubSub.subscribe(kim, "Note", null, "k1");

        pubSub.unsubscribeAll(sam);

        pubSub.publish("Note", new Note("hello", "sam"));
        pubSub.publish("Memo", new Note("hello", "sam"));

        assertThat(sam.ids(), is(empty()));
        assertThat(kim.ids(), contains(List.of("k1")));
    }


    /// The registry is also what tells an inbound payload which class it belongs to.
    @Test
    void answersWhatClassAChannelCarries()
    {
        pubSub.register("Note", Note.class);

        assertThat(pubSub.topicType("Note"), is((Object) Note.class));
        assertThat(pubSub.topicType("Nope"), is((Object) null));
    }


    @Test
    void refusesToRebindAChannelToAnotherClass()
    {
        pubSub.register("Note", Note.class);
        pubSub.register("Note", Note.class);

        assertThrows(QLiveException.class, () -> pubSub.register("Note", String.class));
    }


    // -----------------------------------------------------------------------------------------------------
    // fixtures
    // -----------------------------------------------------------------------------------------------------

    /// A recipient that keeps what it was sent.
    private static final class Collecting
        implements Recipient
    {
        private final List<com.dataciders.qlive.model.push.Topic> received = new ArrayList<>();


        @Override
        public void send(ServerMessage message)
        {
            received.add((com.dataciders.qlive.model.push.Topic) message);
        }


        List<String> topics()
        {
            return received.stream().map(com.dataciders.qlive.model.push.Topic::getTopic).toList();
        }


        List<List<String>> ids()
        {
            return received.stream().map(com.dataciders.qlive.model.push.Topic::getIds).toList();
        }


        List<Object> payloads()
        {
            return received.stream().map(com.dataciders.qlive.model.push.Topic::getPayload).toList();
        }
    }


    public static final class Note
    {
        private final String text;

        private final String author;


        Note(String text, String author)
        {
            this.text = text;
            this.author = author;
        }


        public String getText()
        {
            return text;
        }


        public String getAuthor()
        {
            return author;
        }
    }
}
