package com.dataciders.qlive.model.push;

import jakarta.validation.constraints.NotNull;

/// Sends a payload to a channel, from a client.
///
/// The mirror image of the `publish()` call any framework or application bean can make server-side, and
/// the one payload the server receives as raw JSON rather than as a live object. Which channels a client
/// may publish to is an authorisation question the transport answers, not this class.
public class Publish
    extends ClientMessage
    implements DynamicPayload
{
    private String topic;

    private Object message;


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


    /// The payload, as parsed: a `Map`, since what class it belongs to is the channel's business and not
    /// this message kind's. {@link PayloadRecast} is how a handler that wants it typed asks for that.
    public Object getMessage()
    {
        return message;
    }


    public void setMessage(Object message)
    {
        this.message = message;
    }


    @Override
    public Object dynamicPayload()
    {
        return message;
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "topic = '" + topic + '\''
            + ", message = " + message
            ;
    }
}
