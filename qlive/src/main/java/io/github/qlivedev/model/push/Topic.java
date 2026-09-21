package io.github.qlivedev.model.push;

import jakarta.validation.constraints.NotNull;
import org.svenson.JSONTypeHint;

import java.util.List;

/// One channel message, delivered to one connection.
///
/// {@link #getIds()} is a list because a connection can hold several subscriptions on the same channel
/// with different conditions. A publish matching more than one of them is batched into a single outgoing
/// message rather than sent once per match, so the receiving client sees the payload once and knows every
/// one of its own subscriptions it belongs to.
public class Topic
    extends ServerMessage
    implements DynamicPayload
{
    private String topic;

    private List<String> ids;

    private Object payload;


    @Override
    @NotNull
    public String getTopic()
    {
        return topic;
    }


    public void setTopic(String topic)
    {
        this.topic = topic;
    }


    /// The ids of this connection's subscriptions that the payload matched.
    @NotNull
    @JSONTypeHint(String.class)
    public List<String> getIds()
    {
        return ids;
    }


    public void setIds(List<String> ids)
    {
        this.ids = ids;
    }


    /// What was published, serialized on the way out and never read back server-side. The client is its
    /// only consumer, and what type it gives the value there is a TypeScript question.
    public Object getPayload()
    {
        return payload;
    }


    public void setPayload(Object payload)
    {
        this.payload = payload;
    }


    @Override
    public Object dynamicPayload()
    {
        return payload;
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "topic = '" + topic + '\''
            + ", ids = " + ids
            + ", payload = " + payload
            ;
    }
}
