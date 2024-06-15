package kiwi.breen.event.store.vertx;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
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

import static java.util.stream.IntStream.range;

@ExtendWith(VertxExtension.class)
public class EventStoreInterceptorTest
{
    private final EventStore eventStore = new MapEventStore();
    private final Instant time = Instant.now();
    private final AtomicLong sequence = new AtomicLong();
    private final EventStoreInterceptor eventStoreInterceptor = new EventStoreInterceptor(sequence, () -> time, eventStore);

    @BeforeEach
    void setUp(final Vertx vertx)
    {
        vertx.eventBus().addOutboundInterceptor(eventStoreInterceptor);
    }

    @Test
    public void shouldInterceptAndStoreMessages(final Vertx vertx)
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
        eventStore.store(sequence.incrementAndGet(), time.plusSeconds(0), "test.address", true, JsonObject.of("msg", "Hello World 10!"), JsonArray.of(JsonObject.of("header1", "value1")));
        eventStore.store(sequence.incrementAndGet(), time.plusSeconds(1), "test.address", true, JsonObject.of("msg", "Hello World 20!"), JsonArray.of(JsonObject.of("header2", "value2")));
        eventStore.store(sequence.incrementAndGet(), time.plusSeconds(2), "test.address", false, JsonObject.of("msg", "Hello World 30!"), JsonArray.of(JsonObject.of("header3", "value3")));
        eventStore.store(sequence.incrementAndGet(), time.plusSeconds(3), "test.address", true, JsonObject.of("msg", "Hello World 40!"), JsonArray.of(JsonObject.of("header4", "value4")));
        eventStore.store(sequence.incrementAndGet(), time.plusSeconds(4), "test.address", false, JsonObject.of("msg", "Hello World 50!"), JsonArray.of(JsonObject.of("header5", "value5")));

        final List<EventStore.Event> events = new ArrayList<>();
        eventStore.replay(events::add);

        Assertions.assertEquals(5, events.size());
    }

    @Test
    void shouldReplayMessagesOntoEventBus(final Vertx vertx)
    {
        final List<EventStore.Event> sourceEvents = List.of(
                new EventStore.Event(1, time.plusSeconds(0), "test.address", true, JsonObject.of("msg", "Hello World 10!"), JsonArray.of(JsonObject.of("header1", "value1"))),
                new EventStore.Event(2, time.plusSeconds(1), "test.address", true, JsonObject.of("msg", "Hello World 20!"), JsonArray.of(JsonObject.of("header2", "value2"))),
                new EventStore.Event(3, time.plusSeconds(2), "test.address", false, JsonObject.of("msg", "Hello World 30!"), JsonArray.of(JsonObject.of("header3", "value3"))),
                new EventStore.Event(4, time.plusSeconds(3), "test.address", true, JsonObject.of("msg", "Hello World 40!"), JsonArray.of(JsonObject.of("header4", "value4"))),
                new EventStore.Event(5, time.plusSeconds(4), "test.address", false, JsonObject.of("msg", "Hello World 50!"), JsonArray.of(JsonObject.of("header5", "value5"))));
        Assertions.assertEquals(5, sourceEvents.size());

        final EventStore sourceEventStore = new MapEventStore();
        sourceEvents.forEach(sourceEventStore::store);

        sourceEventStore.replay(event -> {

            final DeliveryOptions deliveryOptions = new DeliveryOptions();
            range(0, event.headers().size())
                    .mapToObj(event.headers()::getJsonObject)
                    .forEach(header -> header.forEach(entry -> deliveryOptions.addHeader(entry.getKey(), (String) entry.getValue())));

            if (event.send())
            {
                vertx.eventBus().send(event.address(), event.body(), deliveryOptions);
            }
            else
            {
                vertx.eventBus().publish(event.address(), event.body(), deliveryOptions);
            }
        });

        final List<EventStore.Event> events = new ArrayList<>();
        eventStore.replay(events::add);
        Assertions.assertEquals(5, events.size());
        Assertions.assertIterableEquals(sourceEvents.stream().map(EventStore.Event::sequence).toList(), events.stream().map(EventStore.Event::sequence).toList());
        // don't check timestamp, the replayed events have a new timestamp, is this right?
        Assertions.assertIterableEquals(sourceEvents.stream().map(EventStore.Event::address).toList(), events.stream().map(EventStore.Event::address).toList());
        Assertions.assertIterableEquals(sourceEvents.stream().map(EventStore.Event::send).toList(), events.stream().map(EventStore.Event::send).toList());
        Assertions.assertIterableEquals(sourceEvents.stream().map(EventStore.Event::body).toList(), events.stream().map(EventStore.Event::body).toList());
        Assertions.assertIterableEquals(sourceEvents.stream().map(EventStore.Event::headers).toList(), events.stream().map(EventStore.Event::headers).toList());
    }
}