package io.github.qlivedev.runtime.pubsub;

import io.github.qlivedev.model.condition.CNode;
import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.runtime.filter.FilterTransformer;
import io.github.qlivedev.runtime.push.Recipient;
import io.github.qlivedev.runtime.util.FilterDSLDecompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/// The channel registry and the fan-out, in memory.
///
/// Publishing walks every subscription on the channel and evaluates its predicate. That is a linear scan,
/// and it is the same linear scan Automaton has run for years at this scale without it being a problem;
/// indexing subscriptions by anything would mean knowing which part of a condition to index on, and there
/// is nothing to measure yet that would say.
public class DefaultPubSubService
    implements PubSubService
{
    private final static Logger log = LoggerFactory.getLogger(DefaultPubSubService.class);

    private final Map<String, Topic> topics = new ConcurrentHashMap<>();


    @Override
    public void register(String topic, Class<?> payloadType)
    {
        if (topic == null || payloadType == null)
        {
            throw new IllegalArgumentException("A channel needs both a name and a payload class");
        }

        final Topic existing = topics.putIfAbsent(topic, new Topic(topic, payloadType));

        if (existing != null && existing.payloadType() != payloadType)
        {
            throw new QLiveException(
                "Channel '" + topic + "' carries " + existing.payloadType().getName() +
                    ", cannot register it for " + payloadType.getName()
            );
        }

        log.debug("Registered channel '{}' of {}", topic, payloadType.getName());
    }


    @Override
    public Class<?> topicType(String topic)
    {
        final Topic channel = topics.get(topic);
        return channel == null ? null : channel.payloadType();
    }


    @Override
    public void subscribe(Recipient recipient, String topic, CNode condition, String id)
    {
        if (recipient == null || id == null)
        {
            throw new IllegalArgumentException("A subscription needs both a recipient and an id");
        }

        final Topic channel = topics.get(topic);

        if (channel == null)
        {
            throw new QLiveException("No such channel: '" + topic + "'");
        }

        final Predicate<Object> filter = new FilterTransformer(channel.payloadType()).transform(condition);

        channel.subscribe(new TopicRegistration(recipient, id, filter));

        if (log.isDebugEnabled())
        {
            log.debug("[DEBUG push] subscribe '{}' to '{}' recipient={}\n{}",
                id, topic, recipient, FilterDSLDecompiler.decompile(condition));
        }
        log.debug("Subscribed '{}' to channel '{}'", id, topic);
    }


    @Override
    public void unsubscribe(Recipient recipient, String topic, String id)
    {
        final Topic channel = topics.get(topic);

        if (channel != null && channel.unsubscribe(recipient, id))
        {
            log.debug("Unsubscribed '{}' from channel '{}'", id, topic);
        }
    }


    @Override
    public void unsubscribeAll(Recipient recipient)
    {
        for (Topic channel : topics.values())
        {
            if (channel.unsubscribeAll(recipient))
            {
                log.debug("Swept the subscriptions of {} from channel '{}'", recipient, channel.name());
            }
        }
    }


    @Override
    public int subscriptionCount(String topic)
    {
        final Topic channel = topics.get(topic);
        return channel == null ? 0 : channel.registrations().size();
    }


    @Override
    public void publish(String topic, Object payload)
    {
        final Topic channel = topics.get(topic);

        if (channel == null)
        {
            throw new QLiveException("No such channel: '" + topic + "'");
        }

        if (payload != null && !channel.payloadType().isInstance(payload))
        {
            throw new QLiveException(
                "Channel '" + topic + "' carries " + channel.payloadType().getName() +
                    ", cannot publish a " + payload.getClass().getName()
            );
        }

        deliver(channel, payload);
    }


    /// Evaluates every subscription on the channel and sends one message per recipient.
    ///
    /// Batched by recipient rather than sent per match, because a connection can hold several
    /// subscriptions on one channel with different conditions: a client that matched three of them sees
    /// the payload once and is told all three ids, instead of parsing the same payload three times.
    private void deliver(Topic channel, Object payload)
    {
        final Map<Recipient, List<String>> matched = new LinkedHashMap<>();

        log.debug("[DEBUG push] publish on '{}': payload={}, {} registration(s)",
            channel.name(), payload, channel.registrations().size());

        for (TopicRegistration registration : channel.registrations())
        {
            try
            {
                boolean wants = registration.wants(payload);
                log.debug("[DEBUG push] {} -> wants={}", registration, wants);
                if (wants)
                {
                    matched.computeIfAbsent(registration.recipient(), r -> new ArrayList<>())
                        .add(registration.id());
                }
            }
            catch (RuntimeException e)
            {
                // One subscription's condition failing on one payload is that subscription's problem. It
                // does not match, and everyone else on the channel still gets the message -- a broken
                // filter must not cost the other subscribers theirs.
                log.error("Error evaluating {} on channel '{}'", registration, channel.name(), e);
            }
        }

        for (Map.Entry<Recipient, List<String>> entry : matched.entrySet())
        {
            entry.getKey().send(message(channel.name(), entry.getValue(), payload));
        }
    }


    private static io.github.qlivedev.model.push.Topic message(String topic, List<String> ids, Object payload)
    {
        final io.github.qlivedev.model.push.Topic message = new io.github.qlivedev.model.push.Topic();
        message.setTopic(topic);
        message.setIds(List.copyOf(ids));
        message.setPayload(payload);
        return message;
    }
}
