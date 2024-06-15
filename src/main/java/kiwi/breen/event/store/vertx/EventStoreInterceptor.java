package kiwi.breen.event.store.vertx;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryContext;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class EventStoreInterceptor implements Handler<DeliveryContext<JsonObject>>
{
    private final AtomicLong sequence;
    private final Supplier<Instant> time;
    private final EventStore store;

    public EventStoreInterceptor(final AtomicLong sequence, final Supplier<Instant> time, final EventStore store)
    {
        this.sequence = sequence;
        this.time = time;
        this.store = store;
    }

    @Override
    public void handle(final DeliveryContext<JsonObject> context)
    {
        final Message<JsonObject> message = context.message();
        final JsonArray headers = message.headers().entries().stream()
                .map(entry -> JsonObject.of(entry.getKey(), entry.getValue()))
                .reduce(new JsonArray(), JsonArray::add, JsonArray::addAll);
        store.store(
                sequence.incrementAndGet(),
                time.get(),
                message.address(),
                context.send(),
                message.body(),
                headers);
        context.next();
    }
}
