package kiwi.breen.event.store.vertx;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class EventStoreVerticleTest
{
    @Test
    public void shouldStartVerticle()
    {
        Vertx.vertx().deployVerticle(new EventStoreVerticle());
    }
}
