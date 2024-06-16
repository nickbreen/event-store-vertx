package kiwi.breen.event.store.vertx;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(VertxExtension.class)
class SequenceInterceptorTest
{
    public static final String TEST_ADDRESS = "test.address";
    public static final JsonObject TEST_MESSAGE_BODY = JsonObject.of("msg", "Hello World!");
    private final AtomicLong sequence = new AtomicLong(700);
    private final SequenceInterceptor interceptor = new SequenceInterceptor(sequence);

    @BeforeEach
    void setUp(final Vertx vertx)
    {
        vertx.eventBus().addOutboundInterceptor(interceptor);
    }

    @Test
    void shouldIncrementSequence(final Vertx vertx)
    {
        vertx.eventBus().send(TEST_ADDRESS, TEST_MESSAGE_BODY);
        assertEquals(701, sequence.get());
    }

    @Test
    @Timeout(value = 1, timeUnit = TimeUnit.SECONDS)
    void shouldAddHeader(final Vertx vertx, final VertxTestContext context)
    {
        vertx.eventBus().consumer(TEST_ADDRESS, message -> {
            final long sequence = SequenceInterceptor.extractSequence(message.headers());
            assertEquals(701, sequence, "Expected sequence to be incremented");
            context.completeNow();
        });

        vertx.eventBus().send(TEST_ADDRESS, TEST_MESSAGE_BODY);
    }
}