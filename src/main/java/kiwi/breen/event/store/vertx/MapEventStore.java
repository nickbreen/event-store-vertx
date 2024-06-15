package kiwi.breen.event.store.vertx;

import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Consumer;

public class MapEventStore implements EventStore
{
    private final ConcurrentNavigableMap<Long, Event> eventStore = new ConcurrentSkipListMap<>();

    @Override
    public void store(final Event event)
    {
        eventStore.put(event.sequence(), event);
    }

    @Override
    public void replay(final Consumer<Event> consumer)
    {
        eventStore.values().forEach(consumer);
    }
}
