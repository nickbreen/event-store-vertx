package kiwi.breen.event.store.vertx;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import static kiwi.breen.event.store.vertx.EventStoreInterceptor.rebuildHeaders;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(VertxExtension.class)
public class EventStoreInterceptorTest
{
    public static final String TEST_ADDRESS = "test.address";

    private final ConcurrentSkipListMap<Long, EventStore.Event> store = new ConcurrentSkipListMap<>();
    private final EventStore eventStore = new MapEventStore(store);
    private final Instant time = Instant.now();
    private final EventStoreInterceptor eventStoreInterceptor = new EventStoreInterceptor(
            eventStore,
            SequenceInterceptor::extractSequence,
            TimeStampInterceptor::extractTimestamp);

    record BodyAndHeader(JsonObject body, DeliveryOptions options)
    {
    }

    final List<BodyAndHeader> fixtures = List.of(
            new BodyAndHeader(
                    JsonObject.of("msg", "Hello World 10!"),
                    new DeliveryOptions()
                            .addHeader("header", "value1")
                            .addHeader(TimeStampInterceptor.HEADER, time.plusSeconds(1).toString())
                            .addHeader(SequenceInterceptor.HEADER, "1")),
            new BodyAndHeader(
                    JsonObject.of("msg", "Hello World 20!"),
                    new DeliveryOptions()
                            .addHeader("header", "value2")
                            .addHeader(TimeStampInterceptor.HEADER, time.plusSeconds(2).toString())
                            .addHeader(SequenceInterceptor.HEADER, "2")),
            new BodyAndHeader(
                    JsonObject.of("msg", "Hello World 30!"),
                    new DeliveryOptions()
                            .addHeader("header", "value3")
                            .addHeader(TimeStampInterceptor.HEADER, time.plusSeconds(3).toString())
                            .addHeader(SequenceInterceptor.HEADER, "3")),
            new BodyAndHeader(
                    JsonObject.of("msg", "Hello World 40!"),
                    new DeliveryOptions()
                            .addHeader("header", "value4")
                            .addHeader(TimeStampInterceptor.HEADER, time.plusSeconds(4).toString())
                            .addHeader(SequenceInterceptor.HEADER, "4")),
            new BodyAndHeader(
                    JsonObject.of("msg", "Hello World 50!"),
                    new DeliveryOptions()
                            .addHeader("header", "value5")
                            .addHeader(TimeStampInterceptor.HEADER, time.plusSeconds(5).toString())
                            .addHeader(SequenceInterceptor.HEADER, "5")));

    @BeforeEach
    void setUp(final Vertx vertx)
    {
        vertx.eventBus().addOutboundInterceptor(eventStoreInterceptor);
    }

    @Test
    void shouldFailToJsonifyMapWithoutJacksonDatabind()
    {
        try
        {
            JsonObject.mapFrom(MultiMap.caseInsensitiveMultiMap());
            fail("Expected DecodeException griping about Jackson databind missing");
        }
        catch (final DecodeException e)
        {
            // expected
        }
    }

    @Test
    void shouldMapHeadersLosslessly()
    {
        final MultiMap headers = HeadersMultiMap.headers();
        headers.add("header1", "value1");
        headers.add("header2", "value2");
        headers.add("header1", "value3");
        headers.add("header1", "value4");

        assertEquals(2, headers.size(), "Expected single entry for each header");
        assertEquals("value1", headers.get("header1"), "Expected to get the first value");
        assertIterableEquals(
                List.of("value1", "value3", "value4"),
                headers.getAll("header1"),
                "Expected to get the all values in order");
        assertIterableEquals(
                headers.entries().stream().map(Map.Entry::copyOf).toList(),
                List.of(
                        Map.entry("header1", "value1"),
                        Map.entry("header2", "value2"),
                        Map.entry("header1", "value3"),
                        Map.entry("header1", "value4")),
                "Expected to iterate over the entries in the order they were added");

        final JsonArray jsonArray = EventStoreInterceptor.jsonifyHeadersPreservingDuplicates(headers);
        assertIterableEquals(JsonArray.of(
                JsonObject.of("header1", "value1"),
                JsonObject.of("header2", "value2"),
                JsonObject.of("header1", "value3"),
                JsonObject.of("header1", "value4")
        ), jsonArray);

        final MultiMap rebuiltHeaders = HeadersMultiMap.headers();
        EventStoreInterceptor.rebuildHeaders(jsonArray, rebuiltHeaders::add);
        // We can't compare the MultiMap directly, so we have to convert it to a list of entries first
        // We also can't compare the HeadersMultiMap entries directly, so we have to copy them to the JDK's equivalent first
        assertIterableEquals(
                headers.entries().stream().map(Map.Entry::copyOf).toList(),
                rebuiltHeaders.entries().stream().map(Map.Entry::copyOf).toList());
    }

    @Test
    public void shouldInterceptAndStoreMessages(final Vertx vertx, final VertxTestContext context)
    {
        final Checkpoint checkpoint = context.checkpoint(fixtures.size()); // ensure we get all the messages
        vertx.eventBus().consumer(TEST_ADDRESS, message -> context.verify(checkpoint::flag)); // ensure we actually get the messages
        fixtures.forEach(fixture -> vertx.eventBus().send(TEST_ADDRESS, fixture.body, fixture.options)); // send all the messages
        assertEquals(fixtures.size(), store.size());
    }

    @Test
    void shouldReplayPointToPointMessagesIntoCollection(final Vertx vertx, final VertxTestContext context)
    {
        final Checkpoint checkpoint = context.checkpoint(fixtures.size()); // ensure we get all the messages
        vertx.eventBus().consumer(TEST_ADDRESS, message -> context.verify(checkpoint::flag)); // ensure we actually get the messages
        fixtures.forEach(fixture -> vertx.eventBus().send(TEST_ADDRESS, fixture.body, fixture.options)); // send all the messages

        final Collection<EventStore.Event> events = new ArrayList<>();
        eventStore.replay(events::add);

        assertEquals(fixtures.size(), events.size());
    }

    @Test
    void shouldReplayPointToPointMessagesWithCheckpoints(final Vertx vertx, final VertxTestContext context)
    {
        final Checkpoint checkpoint = context.checkpoint(fixtures.size()); // ensure we get all the messages
        vertx.eventBus().consumer(TEST_ADDRESS, message -> context.verify(checkpoint::flag)); // ensure we actually get the messages
        fixtures.forEach(fixture -> vertx.eventBus().send(TEST_ADDRESS, fixture.body, fixture.options)); // send all the messages

        final Checkpoint replayCheckpoint = context.checkpoint(store.size());
        eventStore.replay(m -> replayCheckpoint.flag());
    }

    @Test
    void shouldReplayPointToPointMessagesOntoEventBus(final Vertx vertx, final VertxTestContext context)
    {
        final Checkpoint checkpoint = context.checkpoint(2 * fixtures.size()); // ensure we get all the messages, and then again
        vertx.eventBus().consumer(TEST_ADDRESS, message -> context.verify(checkpoint::flag)); // ensure we actually get the messages
        fixtures.forEach(fixture -> vertx.eventBus().send(TEST_ADDRESS, fixture.body, fixture.options)); // send all the messages
        eventStore.replay(event -> playEvent(vertx, event));
    }

    @Test
    void shouldReplayBroadcastMessagesOntoEventBus(final Vertx vertx, final VertxTestContext context)
    {
        final Checkpoint checkpoint = context.checkpoint(2 * fixtures.size()); // ensure we get all the messages, and then again
        vertx.eventBus().consumer(TEST_ADDRESS, message -> context.verify(checkpoint::flag)); // ensure we actually get the messages
        fixtures.forEach(fixture -> vertx.eventBus().publish(TEST_ADDRESS, fixture.body, fixture.options)); // send all the messages

        final Checkpoint replayCheckpoint = context.checkpoint(store.size()); // ensure we get all the replayed messages
        vertx.eventBus().consumer(TEST_ADDRESS, message -> context.verify(replayCheckpoint::flag));

        eventStore.replay(event -> playEvent(vertx, event));
    }

    private static void playEvent(final Vertx vertx, final EventStore.Event event)
    {
        final DeliveryOptions deliveryOptions = new DeliveryOptions();
        rebuildHeaders(event.headers(), deliveryOptions::addHeader);

        if (event.send())
        {
            vertx.eventBus().send(event.address(), event.body(), deliveryOptions);
        }
        else
        {
            vertx.eventBus().publish(event.address(), event.body(), deliveryOptions);
        }
    }
}