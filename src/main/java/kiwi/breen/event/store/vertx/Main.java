package kiwi.breen.event.store.vertx;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class Main
{
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(Main.class);

    public static void main(final String[] args)
    {
        final Vertx vertx = Vertx.vertx();

        final TimeStampInterceptor timeStampInterceptor = new TimeStampInterceptor(Instant::now);
        vertx.eventBus().addOutboundInterceptor(timeStampInterceptor);

        final AtomicLong sequence = new AtomicLong();
        final SequenceInterceptor sequenceInterceptor = new SequenceInterceptor(sequence);
        vertx.eventBus().addOutboundInterceptor(sequenceInterceptor);

        final EventStore eventStore = null;
        final EventStoreInterceptor eventStoreInterceptor = new EventStoreInterceptor(
                eventStore,
                SequenceInterceptor::extractSequence,
                TimeStampInterceptor::extractTimestamp);

        final ConditionalInterceptor<JsonObject> eventPrefixInterceptor = new ConditionalInterceptor<>(
                deliveryContext -> deliveryContext.message().address().startsWith("event."),
                eventStoreInterceptor);

        vertx.eventBus().addOutboundInterceptor(eventPrefixInterceptor);

        vertx.deployVerticle(new EventStoreVerticle()).onComplete(
                x -> LOGGER.info("Verticle started {}", x),
                cause -> LOGGER.error("Verticle failed to start", cause)
        );
    }
}
