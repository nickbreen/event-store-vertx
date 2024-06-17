package kiwi.breen.event.store.vertx;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.concurrent.ConcurrentSkipListMap;

@ExtendWith(VertxExtension.class)
class EventStoreTest
{
    public static final String TEST_ADDRESS = "test.address";

    private final ConcurrentSkipListMap<Long, EventStore.Event> store = new ConcurrentSkipListMap<>();
    private final EventStore eventStore = new MapEventStore(store);
    private final Instant time = Instant.now();

    @Test
    void shouldFailToStoreEventWithDuplicateSequence(final VertxTestContext context)
    {
        final EventStore.Event event = new EventStore.Event(1, time, TEST_ADDRESS, true, JsonObject.of(), JsonArray.of());
        eventStore.store(event).onFailure(context::failNow);
        eventStore.store(event)
                .onFailure(e -> context.completeNow())
                .onSuccess(e -> context.failNow("Should not have been able to store event"));
    }
}