package io.github.qlivedev.model.push;

import jakarta.validation.constraints.NotNull;

/// Reports that something a client asked for did not happen, to the client that asked.
///
/// Named for what it is on the wire. Within this package it shadows `java.lang.Error`, which is the price
/// of the discriminator being the class's own simple name; code here that means the JVM's writes
/// `java.lang.Error`.
public class Error
    extends ServerMessage
{
    private String topic;

    private String id;

    private String message;


    /// Channel the failed request named, if it named one.
    public String getTopic()
    {
        return topic;
    }


    public void setTopic(String topic)
    {
        this.topic = topic;
    }


    /// Subscription id the failed request carried, if it carried one.
    public String getId()
    {
        return id;
    }


    public void setId(String id)
    {
        this.id = id;
    }


    /// What went wrong, in terms the sender can act on.
    @NotNull
    public String getMessage()
    {
        return message;
    }


    public void setMessage(String message)
    {
        this.message = message;
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "topic = '" + topic + '\''
            + ", id = '" + id + '\''
            + ", message = '" + message + '\''
            ;
    }
}
