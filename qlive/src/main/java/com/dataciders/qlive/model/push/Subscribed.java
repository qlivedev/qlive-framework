package com.dataciders.qlive.model.push;

import jakarta.validation.constraints.NotNull;

/// Acknowledges a {@link Subscribe}: the condition compiled, the subscription is registered, and messages
/// on the channel will start arriving.
///
/// The point of acknowledging at all is that the other outcome is visible too. A condition using an
/// operator this backend cannot honor is rejected with an {@link Error} naming it, rather than becoming a
/// subscription that silently never matches anything.
public class Subscribed
    extends ServerMessage
{
    private String topic;

    private String id;


    @NotNull
    public String getTopic()
    {
        return topic;
    }


    public void setTopic(String topic)
    {
        this.topic = topic;
    }


    /// The id the {@link Subscribe} carried.
    @NotNull
    public String getId()
    {
        return id;
    }


    public void setId(String id)
    {
        this.id = id;
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "topic = '" + topic + '\''
            + ", id = '" + id + '\''
            ;
    }
}
