package kiwi.breen.event.store.vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageProducer;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;

public class NoiseVerticle extends AbstractVerticle
{
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(NoiseVerticle.class);

    private final int count;
    private final String address;

    public NoiseVerticle(final int count, final String address)
    {
        this.count = count;
        this.address = address;
    }

    @Override
    public void start()
    {
        final MessageProducer<JsonObject> sender = vertx.eventBus().<JsonObject>sender(address).deliveryOptions(new DeliveryOptions().addHeader("x-origin", context.deploymentID()));
        vertx.eventBus().consumer(address, message -> {}); // just consume our messages
        vertx.executeBlocking(() -> this.noise(sender))
                .onSuccess(i -> LOGGER.info("Sent {} messages", i))
                .onFailure(cause -> LOGGER.error("Failed to send messages", cause));

    }

    private int noise(final MessageProducer<JsonObject> sender)
    {
        for (int i = 0; i < count; i++)
        {
            sender.write(JsonObject.of("i", i))
                    .onFailure(cause -> LOGGER.error("Failed to send message", cause));
        }
        return count;
    }
}
