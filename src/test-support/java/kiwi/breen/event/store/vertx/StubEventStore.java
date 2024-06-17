package kiwi.breen.event.store.vertx;

import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.util.Optional;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.function.Consumer;

public class StubEventStore implements EventStore
{
    private final ConcurrentNavigableMap<Long, Event> eventStore;

    StubEventStore(final ConcurrentNavigableMap<Long, Event> eventStore)
    {
        this.eventStore = eventStore;
    }

    @Override
    public Future<Void> store(final Event event)
    {
        final Promise<Void> promise = Promise.promise();
        Optional.ofNullable(eventStore.putIfAbsent(event.sequence(), event))
                .map(e -> "Event with sequence " + event.sequence() + " already exists")
                .ifPresentOrElse(promise::fail, promise::complete);
        return promise.future();
    }

    @Override
    public void replay(final Consumer<Event> consumer)
    {
        eventStore.values().forEach(consumer);
    }
}
