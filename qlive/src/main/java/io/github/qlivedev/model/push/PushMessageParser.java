package io.github.qlivedev.model.push;

import io.github.qlivedev.model.condition.CNode;
import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.runtime.util.TypeMappers;
import io.github.qlivedev.util.JSONUtil;
import org.svenson.JSONParser;

/// Reads a websocket frame as the message class its "type" names.
///
/// Two hierarchies are dispatched in one pass: the message kind, and the FilterDSL condition a
/// {@link Subscribe} carries. Each gets its own class-name mapper, scoped to its own base type, and
/// {@link TypeMappers#firstAnswer} is what lets both be consulted -- Svenson's own composite cannot, see
/// there.
///
/// The one field neither mapper touches is a pub/sub payload. `Publish.message` is declared as `Object`,
/// so no matcher fires on it and it lands as a plain `Map`: which class it belongs to is decided by the
/// channel, and that is runtime state no parser can be given at construction time.
/// {@link PayloadRecast} is how a caller asks for the typed instance, if it wants one.
public final class PushMessageParser
{
    private final JSONParser parser;


    public PushMessageParser()
    {
        this.parser = new JSONParser();
        this.parser.setObjectSupport(JSONUtil.OBJECT_SUPPORT);
        this.parser.setTypeMapper(
            TypeMappers.firstAnswer(
                TypeMappers.byClassName(PushMessage.class),
                TypeMappers.byClassName(CNode.class)
            )
        );
    }


    /// Reads a frame a client sent.
    ///
    /// @throws QLiveException   if the frame names a message kind that only travels the other way
    public ClientMessage parseClientMessage(String json)
    {
        return direction(parser.parse(PushMessage.class, json), ClientMessage.class);
    }


    /// Reads a frame the server sent. Nothing in the server does this -- an outgoing message is
    /// serialized, never read back -- but the server's own kinds are as parseable as the client's, and
    /// proving it is what keeps the two directions one facility instead of two.
    ///
    /// @throws QLiveException   if the frame names a message kind that only travels the other way
    public ServerMessage parseServerMessage(String json)
    {
        return direction(parser.parse(PushMessage.class, json), ServerMessage.class);
    }


    private static <T extends PushMessage> T direction(PushMessage message, Class<T> expected)
    {
        if (message != null && !expected.isInstance(message))
        {
            throw new QLiveException(
                message.getType() + " is not a " + expected.getSimpleName() + ": it only travels the other way"
            );
        }

        return expected.cast(message);
    }
}
