package io.github.qlivedev.model.push;

import io.github.qlivedev.model.condition.CNode;
import jakarta.validation.constraints.NotNull;

/// Registers one subscription on one channel for the connection that sent it.
///
/// A connection can hold several subscriptions on the same channel with different conditions, which is
/// what {@link #getId()} tells apart: the client picks it, and it comes back on every
/// {@link Topic} message the subscription matched, on the {@link Subscribed} acknowledging it, and on any
/// {@link Error} rejecting it.
public class Subscribe
    extends ClientMessage
    implements Addressed
{
    private String topic;

    private String id;

    private CNode condition;


    /// Name of the channel to subscribe to.
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


    /// The client's own id for this subscription, unique within its connection.
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


    /// Condition every message on the channel is evaluated against before it is delivered to this
    /// subscription, or `null` to receive everything the channel carries.
    public CNode getCondition()
    {
        return condition;
    }


    public void setCondition(CNode condition)
    {
        this.condition = condition;
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "topic = '" + topic + '\''
            + ", id = '" + id + '\''
            + ", condition = " + condition
            ;
    }
}
