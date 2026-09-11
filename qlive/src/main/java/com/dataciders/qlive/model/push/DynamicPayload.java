package com.dataciders.qlive.model.push;

/// Implemented by the two message kinds whose payload's type is not fixed by the kind but by the channel
/// the message is on.
///
/// Every other message kind is fully described by its own class, so the discriminator alone tells a parser
/// everything. These two are not, which is why their payload is read as a plain `Object` -- a `Map`, for
/// anything that arrives as JSON -- and why a caller wanting compile-time field access has to ask for it
/// by class through {@link PayloadRecast}. That is a property of pub/sub, not a gap in the message model:
/// most consumers never need more, because
/// {@link org.svenson.util.JSONBeanUtil#getProperty(Object, String)} reads a map entry and a bean property
/// through the same call.
public interface DynamicPayload
{
    /// Name of the channel the message belongs to, and what decides the payload's type.
    String getTopic();


    /// The payload as it stands: a live object on the publishing side, a `Map` on the receiving one.
    ///
    /// Deliberately not a JavaBean getter. The two implementations spell the payload differently on the
    /// wire -- `message` on a {@link Publish}, `payload` on a {@link Topic} -- and a `getDynamicPayload()`
    /// here would add a third, phantom property to both.
    Object dynamicPayload();
}
