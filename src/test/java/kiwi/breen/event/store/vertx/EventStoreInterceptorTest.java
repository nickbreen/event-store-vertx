package kiwi.breen.event.store.vertx;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static java.time.temporal.ChronoUnit.MINUTES;

@ExtendWith(VertxExtension.class)
public class EventStoreInterceptorTest
{
    private final EventStore eventStore = new MapEventStore();
    private final Instant time = Instant.now();
    private final AtomicLong sequence = new AtomicLong();
    private final EventStoreInterceptor eventStoreInterceptor = new EventStoreInterceptor(sequence, () -> time, eventStore);
    private final Vertx vertx = Vertx.vertx();

    @BeforeEach
    void setUp()
    {
        vertx.eventBus().addOutboundInterceptor(eventStoreInterceptor);
    }

    @Test
    public void shouldInterceptAndStoreMessages()
    {
        vertx.eventBus().send("test.address", JsonObject.of("msg", "Hello World 10!"));
        vertx.eventBus().send("test.address", JsonObject.of("msg", "Hello World 20!"));
        vertx.eventBus().send("test.address", JsonObject.of("msg", "Hello World 30!"));
        vertx.eventBus().send("test.address", JsonObject.of("msg", "Hello World 40!"));
        vertx.eventBus().send("test.address", JsonObject.of("msg", "Hello World 50!"));

        Assertions.assertEquals(5, sequence.get());
    }

    @Test
    void shouldReplayMessages()
    {
        eventStore.store(sequence.incrementAndGet(), time.plusSeconds(1), "test.address", true, JsonObject.of("msg", "Hello World 10!"), JsonArray.of(JsonObject.of("header1", "value1")));
        eventStore.store(sequence.incrementAndGet(), time.plusSeconds(2), "test.address", true, JsonObject.of("msg", "Hello World 20!"), JsonArray.of(JsonObject.of("header2", "value2")));
        eventStore.store(sequence.incrementAndGet(), time.plusSeconds(3), "test.address", false, JsonObject.of("msg", "Hello World 30!"), JsonArray.of(JsonObject.of("header3", "value3")));
        eventStore.store(sequence.incrementAndGet(), time.plusSeconds(4), "test.address", true, JsonObject.of("msg", "Hello World 40!"), JsonArray.of(JsonObject.of("header4", "value4")));
        eventStore.store(sequence.incrementAndGet(), time.plusSeconds(5), "test.address", false, JsonObject.of("msg", "Hello World 50!"), JsonArray.of(JsonObject.of("header5", "value5")));

        final List<EventStore.Event> events = new ArrayList<>();
        eventStore.replay(events::add);

        Assertions.assertEquals(5, events.size());
    }
}