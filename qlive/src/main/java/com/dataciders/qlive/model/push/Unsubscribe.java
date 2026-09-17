package com.dataciders.qlive.model.push;

import jakarta.validation.constraints.NotNull;

/// Drops one of the sending connection's subscriptions.
///
/// Only ever needed while a connection lives: closing one sweeps every subscription it holds, across every
/// channel.
public class Unsubscribe
    extends ClientMessage
    implements Addressed
{
    private String topic;

    private String id;


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


    /// The id the matching {@link Subscribe} carried.
    @Override
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
