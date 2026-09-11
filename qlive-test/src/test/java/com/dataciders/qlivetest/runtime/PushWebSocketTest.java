package com.dataciders.qlivetest.runtime;

import com.dataciders.qlive.model.push.Error;
import com.dataciders.qlive.model.push.PushMessageParser;
import com.dataciders.qlive.model.push.ServerMessage;
import com.dataciders.qlive.model.push.Subscribe;
import com.dataciders.qlive.model.push.Subscribed;
import com.dataciders.qlive.model.push.Unsubscribe;
import com.dataciders.qlive.runtime.QLivePaths;
import com.dataciders.qlive.runtime.pubsub.PubSubService;
import com.dataciders.qlive.runtime.service.DevStaticAnalysisProvider;
import com.dataciders.qlive.runtime.service.ProdStaticAnalysisProvider;
import de.quinscape.spring.jsview.util.JSONUtil;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.dataciders.qlive.runtime.scalar.FilterDSL.field;
import static com.dataciders.qlive.runtime.scalar.FilterDSL.value;
import static com.dataciders.qlivetest.domain.Tables.APP_USER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// The push endpoint against a real server, a real login and a real websocket.
///
/// The question this exists to answer before anything is built on top of it: does the security filter
/// chain run against a websocket upgrade at all? It is an ordinary same-origin GET carrying the session
/// cookie, so it ought to -- but "ought to" is what the design refused to build on, and an application
/// whose catch-all rule turns out not to cover the handshake has an unauthenticated push endpoint and no
/// sign of it. {@link #refusesAHandshakeFromSomebodyNotLoggedIn} is that question, asked out loud.
///
/// The login is a real form post, CSRF token and all, against a user this test makes and removes again.
/// Nothing here is a mock, because a mock of the filter chain would answer the one question the test is
/// for with whatever it was told to say.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PushWebSocketTest
{
    private final static String TOPIC = "PushWebSocketTestNote";

    private final static String LOGIN = "push-test-user";

    private final static String PASSWORD = "push-test-password";

    private final static long TIMEOUT_SECONDS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private PubSubService pubSub;

    @Autowired
    private DSLContext dslContext;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DevStaticAnalysisProvider staticAnalysisProvider;

    private final String userId = UUID.randomUUID().toString();


    @BeforeEach
    void prepare()
    {
        pubSub.register(TOPIC, Note.class);

        // The bootstrap endpoint is where the login form's CSRF token comes from, and it answers 503 until
        // the analysis is there -- the same data vite dev pushes into it.
        staticAnalysisProvider.replace(new ProdStaticAnalysisProvider().getTrackUsageData());

        dslContext.insertInto(APP_USER)
            .set(APP_USER.ID, userId)
            .set(APP_USER.LOGIN, LOGIN)
            .set(APP_USER.PASSWORD, passwordEncoder.encode(PASSWORD))
            .set(APP_USER.ROLES, "ROLE_USER")
            .set(APP_USER.CREATED, new Timestamp(System.currentTimeMillis()))
            .execute();
    }


    @AfterEach
    void removeWhatWasMade()
    {
        dslContext.deleteFrom(APP_USER).where(APP_USER.ID.eq(userId)).execute();
    }


    /// The gating question. If this fails, the push endpoint is open to anyone who can reach the port and
    /// the identity design above it is built on nothing.
    @Test
    void refusesAHandshakeFromSomebodyNotLoggedIn() throws Exception
    {
        // The upgrade never happens, and the reason it never happens is the one that matters: the chain
        // ran, found nobody logged in, and answered the way it answers every other unauthenticated
        // request. Asserted on the plain GET as well, because what the client throws on a refused upgrade
        // is the client library's business and could change without the refusal changing.
        final HttpResponse<String> refused = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(uri(QLivePaths.PUSH_URI)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(refused.statusCode(), is(401));

        assertThrows(Exception.class, () -> new Client().connect(null));
    }


    /// What the dev-mode Vite proxy has to be configured around.
    ///
    /// Spring registers an `OriginHandshakeInterceptor` with an empty allow-list, which means same-origin
    /// only: the browser's `Origin` has to match the host the handshake arrived at. A dev server proxying
    /// the upgrade must therefore leave the `Host` header alone -- Vite's `changeOrigin`, which the
    /// `/api` and `/graphql` entries beside it do set, is what would make the two differ and get every
    /// handshake refused.
    ///
    /// Pinned here rather than taken from the framework's documentation, because it is the default that is
    /// doing the work and a default is exactly the kind of thing that changes under a project.
    @Test
    void refusesAHandshakeFromAnotherOrigin() throws Exception
    {
        final String cookie = login();

        assertThrows(
            Exception.class,
            () -> new Client().connect(cookie, "http://localhost:5173")
        );

        // The same login, from the origin the endpoint is served on, connects -- so what the case above
        // asserts is the origin check and not a second way of being unauthenticated.
        try (Client client = new Client())
        {
            client.connect(cookie, "http://localhost:" + port);
        }
    }


    @Test
    void deliversWhatIsPublishedToASubscriber() throws Exception
    {
        try (Client client = new Client())
        {
            client.connect(login());

            client.send(subscribe(TOPIC, "s1", null));
            assertThat(client.next(), instanceOf(Subscribed.class));

            pubSub.publish(TOPIC, new Note("hello", "kim"));

            final com.dataciders.qlive.model.push.Topic message =
                (com.dataciders.qlive.model.push.Topic) client.next();

            assertThat(message.getTopic(), is(TOPIC));
            assertThat(message.getIds(), contains("s1"));

            // The payload is JSON by the time it gets here, which is the whole of what the client is
            // promised: a map of the publisher's own properties, no type mapper involved.
            assertThat(payload(message).get("text"), is("hello"));
            assertThat(payload(message).get("author"), is("kim"));
        }
    }


    /// Each subscription's own condition, evaluated server-side, per message.
    @Test
    void deliversOnlyWhatTheSubscriptionsConditionMatches() throws Exception
    {
        try (Client client = new Client())
        {
            client.connect(login());

            client.send(subscribe(TOPIC, "mine", field("author").eq(value("kim"))));
            assertThat(client.next(), instanceOf(Subscribed.class));

            pubSub.publish(TOPIC, new Note("not for you", "sam"));
            pubSub.publish(TOPIC, new Note("for you", "kim"));

            final com.dataciders.qlive.model.push.Topic message =
                (com.dataciders.qlive.model.push.Topic) client.next();

            assertThat(payload(message).get("text"), is("for you"));
        }
    }


    /// A refused subscription comes back as a refusal. The alternative -- a server log line and a
    /// subscription that silently never matches -- is what this protocol acknowledges subscribes to avoid.
    @Test
    void answersASubscribeToAnUnknownChannelWithAnError() throws Exception
    {
        try (Client client = new Client())
        {
            client.connect(login());

            client.send(subscribe("NoSuchChannel", "s1", null));

            final ServerMessage answer = client.next();

            assertThat(answer, instanceOf(Error.class));
            assertThat(((Error) answer).getTopic(), is("NoSuchChannel"));
            assertThat(((Error) answer).getId(), is("s1"));
            assertThat(((Error) answer).getMessage(), containsString("No such channel"));
        }
    }


    @Test
    void stopsDeliveringAfterAnUnsubscribe() throws Exception
    {
        try (Client client = new Client())
        {
            client.connect(login());

            client.send(subscribe(TOPIC, "s1", null));
            assertThat(client.next(), instanceOf(Subscribed.class));

            final Unsubscribe unsubscribe = new Unsubscribe();
            unsubscribe.setTopic(TOPIC);
            unsubscribe.setId("s1");
            client.send(unsubscribe);

            waitForSubscriptionCount(0);

            pubSub.publish(TOPIC, new Note("hello", "kim"));

            assertThat(client.nothingWithin(1), is(true));
        }
    }


    /// A closed connection takes every subscription it held with it. Worth a test of its own rather than a
    /// line of code somebody trusts: Automaton lost exactly this cleanup in a refactor once, and nothing
    /// about a subscription nobody is listening to makes a noise.
    @Test
    void aClosedConnectionLosesItsSubscriptions() throws Exception
    {
        final Client client = new Client();
        client.connect(login());

        client.send(subscribe(TOPIC, "s1", null));
        assertThat(client.next(), instanceOf(Subscribed.class));

        waitForSubscriptionCount(1);

        client.close();

        waitForSubscriptionCount(0);
    }


    /// Publishing on a channel nobody is on does nothing at all, which is what a framework-internal
    /// publisher needs: it has no reason to know whether anyone has subscribed yet.
    @Test
    void publishingToAChannelNobodyIsOnIsANoOp()
    {
        pubSub.publish(TOPIC, new Note("nobody there", "kim"));
    }


    // -----------------------------------------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------------------------------------

    private void waitForSubscriptionCount(int expected) throws InterruptedException
    {
        final long deadline = System.currentTimeMillis() + TIMEOUT_SECONDS * 1000;

        while (pubSub.subscriptionCount(TOPIC) != expected && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(25);
        }

        assertThat(pubSub.subscriptionCount(TOPIC), is(expected));
    }


    private static Subscribe subscribe(String topic, String id, com.dataciders.qlive.model.condition.CNode condition)
    {
        final Subscribe subscribe = new Subscribe();
        subscribe.setTopic(topic);
        subscribe.setId(id);
        subscribe.setCondition(condition);
        return subscribe;
    }


    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(com.dataciders.qlive.model.push.Topic message)
    {
        return (Map<String, Object>) message.getPayload();
    }


    /// Logs in the way a browser does -- bootstrap for the CSRF token, then the form post -- and returns
    /// the session cookie the websocket handshake then carries.
    private String login() throws Exception
    {
        final CookieManager cookies = new CookieManager();
        final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .cookieHandler(cookies)
            .build();

        final HttpResponse<String> bootstrap = http.send(
            HttpRequest.newBuilder(uri("/api/bootstrap?path=/app/home")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(bootstrap.statusCode(), is(200));

        @SuppressWarnings("unchecked")
        final Map<String, Object> csrf = (Map<String, Object>)
            ((Map<String, Object>) JSONUtil.DEFAULT_PARSER.parse(Map.class, bootstrap.body())).get("csrfToken");

        final String form = "username=" + LOGIN + "&password=" + PASSWORD
            + "&" + csrf.get("param") + "=" + csrf.get("value");

        final HttpResponse<String> loggedIn = http.send(
            HttpRequest.newBuilder(uri("/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertThat(
            "login failed: " + loggedIn.statusCode() + " -> " + loggedIn.headers().firstValue("Location"),
            loggedIn.headers().firstValue("Location").orElse(""),
            containsString("/app/home")
        );

        final StringBuilder header = new StringBuilder();
        for (HttpCookie cookie : cookies.getCookieStore().getCookies())
        {
            header.append(header.isEmpty() ? "" : "; ").append(cookie.getName()).append('=').append(cookie.getValue());
        }

        assertThat(header.toString(), containsString("JSESSIONID"));

        return header.toString();
    }


    private URI uri(String path)
    {
        return URI.create("http://localhost:" + port + path);
    }


    /// A websocket client that keeps what it was sent, read back through the framework's own parser --
    /// which is the first time anything reads a server message rather than writing one.
    private final class Client
        implements AutoCloseable
    {
        private final PushMessageParser parser = new PushMessageParser();

        private final BlockingQueue<String> frames = new ArrayBlockingQueue<>(32);

        private WebSocketSession session;


        void connect(String cookie) throws Exception
        {
            connect(cookie, null);
        }


        /// @param origin `Origin` header to send, or null to send none. A browser always sends one; a
        ///               client like this one only does when it is asked to.
        void connect(String cookie, String origin) throws Exception
        {
            final WebSocketHttpHeaders headers = new WebSocketHttpHeaders();

            if (cookie != null)
            {
                headers.add("Cookie", cookie);
            }

            if (origin != null)
            {
                headers.add("Origin", origin);
            }

            session = new StandardWebSocketClient()
                .execute(
                    new TextWebSocketHandler()
                    {
                        @Override
                        protected void handleTextMessage(WebSocketSession session, TextMessage message)
                        {
                            frames.add(message.getPayload());
                        }
                    },
                    headers,
                    URI.create("ws://localhost:" + port + QLivePaths.PUSH_URI)
                )
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }


        void send(Object message) throws Exception
        {
            session.sendMessage(new TextMessage(JSONUtil.DEFAULT_GENERATOR.forValue(message)));
        }


        ServerMessage next() throws Exception
        {
            final String frame = frames.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertThat("no frame arrived within " + TIMEOUT_SECONDS + "s", frame, is(notNullValue()));

            return parser.parseServerMessage(frame);
        }


        boolean nothingWithin(long seconds) throws Exception
        {
            return frames.poll(seconds, TimeUnit.SECONDS) == null;
        }


        @Override
        public void close() throws Exception
        {
            if (session != null && session.isOpen())
            {
                session.close(CloseStatus.NORMAL);
            }
        }
    }


    /// What the test channel carries. A channel's payload class is whatever the publisher decided it is --
    /// a domain object, a record of a change, or a note.
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
