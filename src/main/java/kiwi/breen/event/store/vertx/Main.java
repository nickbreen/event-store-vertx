package kiwi.breen.event.store.vertx;

import io.vertx.core.Vertx;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class Main
{
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(Main.class);

    public static void main(final String[] args)
    {
        final Vertx vertx = Vertx.vertx();

        final EventStore eventStore = new MapEventStore();

        vertx.eventBus().addOutboundInterceptor(
                new EventStoreInterceptor(
                        new AtomicLong(),
                        Instant::now,
                        eventStore));
        vertx.deployVerticle(new EventStoreVerticle()).onComplete(
                x -> LOGGER.info("Verticle started {}", x),
                cause -> LOGGER.error("Verticle failed to start", cause)
        );
    }
}
