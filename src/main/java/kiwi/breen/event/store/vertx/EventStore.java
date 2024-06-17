package kiwi.breen.event.store.vertx;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.Instant;
import java.util.function.Consumer;

public interface EventStore
{
    record Event(long sequence, Instant timestamp, String address, boolean send, JsonObject body, JsonArray headers) {}

    default Future<Void> store(long sequence, Instant timestamp, String address, boolean send, JsonObject body, JsonArray headers)
    {
        return store(new Event(sequence, timestamp, address, send, body, headers));
    }

    Future<Void> store(Event event);

    void replay(Consumer<Event> consumer);
}
