package io.github.qlivedev.model.push;

import io.github.qlivedev.runtime.QLiveException;
import org.svenson.util.RecastUtil;

/// Turns the untyped payload of a {@link DynamicPayload} message into the class its channel is bound to.
///
/// A step a handler takes because it wants compile-time field access, not one the message model performs
/// on its behalf. Filtering needs none of this -- a condition compiled against `payload.name` neither
/// knows nor cares whether it is reading the parsed `Map` or a recast instance -- so this exists for the
/// Java code that would rather not spell its own payload's fields as strings.
///
/// Recasting walks the parsed map graph straight into a new instance through Svenson's own property
/// accessors. It is driven by the *target* class's declared properties, so content the bound class
/// declares no place for simply has nowhere to land.
public final class PayloadRecast
{
    private PayloadRecast()
    {
        // no instances
    }


    /// Recasts the message's payload into the class its topic is registered with.
    ///
    /// @param message   message whose payload to convert
    /// @param topics    channel registry to resolve the message's topic against
    ///
    /// @return the payload as an instance of the bound class, or `null` for a payload that is `null`
    ///
    /// @throws QLiveException   if no channel of that name is registered
    public static Object typed(DynamicPayload message, TopicTypes topics)
    {
        final String topic = message.getTopic();
        final Class<?> cls = topics.topicType(topic);

        if (cls == null)
        {
            throw new QLiveException("Cannot type the payload of a message on '" + topic + "': no such channel");
        }

        return RecastUtil.recast(cls, message.dynamicPayload());
    }
}
