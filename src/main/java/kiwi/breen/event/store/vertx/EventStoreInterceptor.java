package kiwi.breen.event.store.vertx;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.DeliveryContext;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static java.util.stream.IntStream.range;

public class EventStoreInterceptor implements Handler<DeliveryContext<JsonObject>>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(EventStoreInterceptor.class);

    private final EventStore store;
    private final Function<MultiMap, Long> sequenceExtractor;
    private final Function<MultiMap, Instant> timestampExtractor;

    public EventStoreInterceptor(
            final EventStore store,
            final Function<MultiMap, Long> sequenceExtractor,
            final Function<MultiMap, Instant> timestampExtractor)
    {
        this.store = store;
        this.sequenceExtractor = sequenceExtractor;
        this.timestampExtractor = timestampExtractor;
    }

    @Override
    public void handle(final DeliveryContext<JsonObject> context)
    {
        final Message<JsonObject> message = context.message();
        final long sequence = sequenceExtractor.apply(message.headers());
        final Instant timestamp = timestampExtractor.apply(message.headers());
        final JsonArray headers = jsonifyHeadersPreservingDuplicates(message.headers());

        store.store(sequence, timestamp, message.address(), context.send(), message.body(), headers)
                .onFailure(failure -> LOGGER.error("Failed to store event {}", this, failure))
                .onComplete(v -> context.next());
    }

    static JsonArray jsonifyHeadersPreservingDuplicates(final MultiMap headers)
    {
        return headers.entries().stream()
                .map(entry -> JsonObject.of(entry.getKey(), entry.getValue()))
                .reduce(new JsonArray(), JsonArray::add, JsonArray::addAll);
    }

    static void rebuildHeaders(final JsonArray headers, final BiConsumer<String, String> sink)
    {
        range(0, headers.size())
                .mapToObj(headers::getJsonObject)
                .forEach(header -> header.forEach(entry -> sink.accept(entry.getKey(), (String) entry.getValue())));
    }
}
