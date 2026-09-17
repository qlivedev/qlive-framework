package com.dataciders.qlive.runtime.push;

/// Told when a push connection opens and when it closes.
///
/// What a closing connection costs is the feature's, not the transport's: pub/sub sweeps the connection's
/// subscriptions, and whatever is built beside it sweeps whatever it was holding. The transport knows only
/// that the connection is gone, which is exactly the amount it should know.
///
/// A {@link PushMessageHandler} that needs this implements both and is wired as both.
public interface ConnectionListener
{
    /// A connection is open and about to start sending frames.
    default void opened(Recipient recipient)
    {
    }


    /// A connection is gone. Whatever was held on its behalf will not be collected by anyone else.
    ///
    /// Called for every listener even if one of them throws, because a feature failing to clean up is no
    /// reason for the next one to leak too.
    void closed(Recipient recipient);
}
