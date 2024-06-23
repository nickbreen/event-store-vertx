package kiwi.breen.event.store.vertx;

import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class NoiseVerticleTest
{
    @Test
    public void shouldStartVerticle(final Vertx vertx, final VertxTestContext testContext)
    {
        vertx.deployVerticle(new NoiseVerticle(20, "event.test"), testContext.completing());
    }

    @Test
    public void shouldBeNoisy(final Vertx vertx, final VertxTestContext testContext)
    {
        final Checkpoint messagesSent = testContext.checkpoint(20);
        vertx.eventBus().consumer("event.test", message -> messagesSent.flag());

        final Checkpoint verticleStarted = testContext.checkpoint();
        vertx.deployVerticle(new NoiseVerticle(20, "event.test"), x -> verticleStarted.flag());
    }

    @Test
    public void shouldBeExtraNoisy(final Vertx vertx, final VertxTestContext testContext)
    {
        final Checkpoint messagesSent = testContext.checkpoint(20 * 5);
        vertx.eventBus().consumer("event.test", message -> messagesSent.flag());

        final Checkpoint verticleStarted = testContext.checkpoint(5);
        vertx.deployVerticle(new NoiseVerticle(20, "event.test"), x -> verticleStarted.flag());
        vertx.deployVerticle(new NoiseVerticle(20, "event.test"), x -> verticleStarted.flag());
        vertx.deployVerticle(new NoiseVerticle(20, "event.test"), x -> verticleStarted.flag());
        vertx.deployVerticle(new NoiseVerticle(20, "event.test"), x -> verticleStarted.flag());
        vertx.deployVerticle(new NoiseVerticle(20, "event.test"), x -> verticleStarted.flag());
    }
}
