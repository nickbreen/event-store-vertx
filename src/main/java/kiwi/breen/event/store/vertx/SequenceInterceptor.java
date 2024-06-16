package kiwi.breen.event.store.vertx;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.DeliveryContext;

import java.util.concurrent.atomic.AtomicLong;

public class SequenceInterceptor implements Handler<DeliveryContext<Object>>
{
    public static final String HEADER = "x-sequence";

    private final AtomicLong sequence;

    public SequenceInterceptor(final AtomicLong sequence)
    {
        this.sequence = sequence;
    }

    @Override
    public void handle(final DeliveryContext<Object> event)
    {
        setSequence(event.message().headers(), sequence.incrementAndGet());
        event.next();
    }

    static void setSequence(final MultiMap headers, final long sequence)
    {
        headers.set(HEADER, Long.toString(sequence, 16));
    }

    static long extractSequence(final MultiMap headers)
    {
        return Long.parseLong(headers.get(HEADER), 16);
    }
}
