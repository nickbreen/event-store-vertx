package kiwi.breen.event.store.vertx;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(VertxExtension.class)
class TimeStampInterceptorTest
{
    private static final String TEST_ADDRESS = "test.address";
    private static final JsonObject TEST_MESSAGE_BODY = JsonObject.of("msg", "Hello World!");
    private static final Instant time = Instant.now();

    private final TimeStampInterceptor interceptor = new TimeStampInterceptor(() -> time);

    @BeforeEach
    void setUp(final Vertx vertx)
    {
        vertx.eventBus().addOutboundInterceptor(interceptor);
    }

    @Test
    @Timeout(value = 1, timeUnit = TimeUnit.SECONDS)
    void shouldAddHeader(final Vertx vertx, final VertxTestContext context)
    {
        vertx.eventBus().consumer(TEST_ADDRESS, message -> {
            final Instant timestamp = TimeStampInterceptor.extractTimestamp(message.headers());
            assertEquals(time, timestamp, "Expected timestamp header");
            context.completeNow();
        });

        vertx.eventBus().send(TEST_ADDRESS, TEST_MESSAGE_BODY);
    }
}