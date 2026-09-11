package com.dataciders.qlive.model.push;

/// The one thing reading a dynamic payload needs from the pub/sub channel registry: the Java class a topic
/// is bound to.
///
/// Narrow on purpose. The registry is runtime state that grows as an application registers channels, so
/// it cannot be wired into a parser once at construction time the way a fixed subclass list can -- but
/// nothing about turning a payload into its bound type needs more of it than this.
@FunctionalInterface
public interface TopicTypes
{
    /// @param topic     channel name
    ///
    /// @return the class the channel's payload has, or `null` if no channel of that name is registered
    Class<?> topicType(String topic);
}
