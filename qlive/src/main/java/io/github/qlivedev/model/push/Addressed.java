package io.github.qlivedev.model.push;

/// Implemented by client message kinds that name what they are about, so a failure can be reported back
/// against that rather than against the connection at large.
///
/// What {@link Error} carries, in other words, seen from the message that provoked it. The transport
/// catches a failure without knowing which kind it was handling -- that is the point of it being a
/// transport -- and this is how a kind says how it wants to be named in the answer. A kind that
/// implements neither this nor {@link DynamicPayload} still gets an `Error`; it just gets one that names
/// only what went wrong.
public interface Addressed
{
    /// Channel this message is about.
    String getTopic();


    /// Subscription this message is about.
    String getId();
}
