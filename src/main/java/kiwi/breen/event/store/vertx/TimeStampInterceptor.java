package kiwi.breen.event.store.vertx;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.DeliveryContext;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

public class TimeStampInterceptor implements Handler<DeliveryContext<Object>>
{
    public static final String HEADER = "x-timestamp";
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_INSTANT;

    private final Supplier<Instant> time;

    public TimeStampInterceptor(final Supplier<Instant> time)
    {
        this.time = time;
    }

    @Override
    public void handle(final DeliveryContext<Object> event)
    {
        setTimestamp(event.message().headers(), time.get());
        event.next();
    }

    static void setTimestamp(final MultiMap headers, final Instant timestamp)
    {
        headers.set(HEADER, FORMAT.format(timestamp));
    }

    static Instant extractTimestamp(final MultiMap headers)
    {
        return FORMAT.parse(headers.get(HEADER), Instant::from);
    }
}
