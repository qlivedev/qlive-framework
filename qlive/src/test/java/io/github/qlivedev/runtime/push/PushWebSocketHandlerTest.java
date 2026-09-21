package io.github.qlivedev.runtime.push;

import io.github.qlivedev.model.push.ClientMessage;
import io.github.qlivedev.model.push.Error;
import io.github.qlivedev.model.push.ServerMessage;
import io.github.qlivedev.model.push.Subscribe;
import io.github.qlivedev.model.push.Unsubscribe;
import io.github.qlivedev.runtime.QLiveException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Covers the transport with no feature behind it and no socket in front of it: the handlers are test
/// doubles claiming kinds, and a {@link Recipient} is a list.
///
/// That the transport can be exercised without pub/sub existing is the point of the split, so these tests
/// mention no channel.
class PushWebSocketHandlerTest
{
    @Test
    void routesEachKindToTheHandlerThatClaimedIt()
    {
        final Collecting subscribes = new Collecting(Subscribe.class);
        final Collecting unsubscribes = new Collecting(Unsubscribe.class);
        final PushWebSocketHandler handler = handler(subscribes, unsubscribes);

        handler.onFrame(
            new Recording(),
            "{\"type\":\"Subscribe\",\"topic\":\"Note\",\"id\":\"s1\"}"
        );

        assertThat(subscribes.taken.size(), is(1));
        assertThat(subscribes.taken.get(0), is(instanceOf(Subscribe.class)));
        assertThat(unsubscribes.taken, is(empty()));
    }


    /// At wiring time, because the alternative is one of the two quietly never running.
    @Test
    void refusesTwoHandlersClaimingOneKind()
    {
        final QLiveException e = assertThrows(
            QLiveException.class,
            () -> handler(new Collecting(Subscribe.class), new Collecting(Subscribe.class))
        );

        assertThat(e.getMessage().contains("claim Subscribe"), is(true));
    }


    /// A well-formed frame of a kind this application wired nobody to take. Worth saying plainly: what is
    /// missing is a handler, not a field.
    @Test
    void answersAKindNothingHandles()
    {
        final PushWebSocketHandler handler = handler(new Collecting(Unsubscribe.class));
        final Recording recipient = new Recording();

        handler.onFrame(recipient, "{\"type\":\"Subscribe\",\"topic\":\"Note\",\"id\":\"s1\"}");

        assertThat(error(recipient).getMessage(), is("Nothing handles Subscribe on this connection"));
    }


    /// A handler refuses by throwing, and the sender is told against the subscription it named -- read off
    /// {@link io.github.qlivedev.model.push.Addressed}, so a kind added beside pub/sub gets the same
    /// without this class learning its message classes.
    @Test
    void addressesAFailureAtWhatTheMessageNamed()
    {
        final PushWebSocketHandler handler = handler(new Refusing(Subscribe.class, "no"));
        final Recording recipient = new Recording();

        handler.onFrame(recipient, "{\"type\":\"Subscribe\",\"topic\":\"Note\",\"id\":\"s1\"}");

        final Error error = error(recipient);
        assertThat(error.getTopic(), is("Note"));
        assertThat(error.getId(), is("s1"));
        assertThat(error.getMessage(), is("no"));
    }


    /// A frame that did not parse names nothing at all, and saying only what went wrong is more use than
    /// inventing a channel it might have meant.
    @Test
    void answersAFrameThatDidNotParseWithoutInventingAnAddress()
    {
        final PushWebSocketHandler handler = handler(new Collecting(Subscribe.class));
        final Recording recipient = new Recording();

        handler.onFrame(recipient, "{\"type\":\"NoSuchKind\"}");

        final Error error = error(recipient);
        assertThat(error.getTopic(), is(nullValue()));
        assertThat(error.getId(), is(nullValue()));
    }


    /// What a closing connection costs is each feature's to sweep, and one of them failing is no reason for
    /// the next one to leak.
    @Test
    void tellsEveryListenerAConnectionClosedEvenWhenOneThrows()
    {
        final List<String> swept = new ArrayList<>();

        final PushWebSocketHandler handler = new PushWebSocketHandler(
            List.of(),
            List.of(
                recipient ->
                {
                    swept.add("first");
                    throw new IllegalStateException("broken");
                },
                recipient -> swept.add("second")
            )
        );

        handler.closed(new Recording());

        assertThat(swept, contains("first", "second"));
    }


    private static PushWebSocketHandler handler(PushMessageHandler... handlers)
    {
        return new PushWebSocketHandler(List.of(handlers), List.of());
    }


    private static Error error(Recording recipient)
    {
        assertThat(recipient.sent.size(), is(1));
        assertThat(recipient.sent.get(0), is(instanceOf(Error.class)));
        return (Error) recipient.sent.get(0);
    }


    private static class Recording
        implements Recipient
    {
        private final List<ServerMessage> sent = new ArrayList<>();


        @Override
        public void send(ServerMessage message)
        {
            sent.add(message);
        }
    }


    private static class Collecting
        implements PushMessageHandler
    {
        private final Set<Class<? extends ClientMessage>> kinds;

        private final List<ClientMessage> taken = new ArrayList<>();


        @SafeVarargs
        Collecting(Class<? extends ClientMessage>... kinds)
        {
            this.kinds = Set.of(kinds);
        }


        @Override
        public Set<Class<? extends ClientMessage>> handles()
        {
            return kinds;
        }


        @Override
        public void handle(Recipient recipient, ClientMessage message)
        {
            taken.add(message);
        }
    }


    private record Refusing(
        Class<? extends ClientMessage> kind,
        String why
    )
        implements PushMessageHandler
    {
        @Override
        public Set<Class<? extends ClientMessage>> handles()
        {
            return Set.of(kind);
        }


        @Override
        public void handle(Recipient recipient, ClientMessage message)
        {
            throw new QLiveException(why);
        }
    }
}
