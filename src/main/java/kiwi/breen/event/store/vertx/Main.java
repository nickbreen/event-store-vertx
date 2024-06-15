package kiwi.breen.event.store.vertx;

import io.vertx.core.Vertx;
import org.slf4j.Logger;

public class Main
{
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(Main.class);

    public static void main(final String[] args)
    {
        Vertx.vertx().deployVerticle(new EventStoreVerticle()).onComplete(
                x -> LOGGER.info("Verticle started {}", x),
                cause -> LOGGER.error("Verticle failed to start", cause)
        );
    }
}
