package kiwi.breen.event.store.vertx;

import io.vertx.core.Vertx;

public class Main
{
    public static void main(final String[] args)
    {
        Vertx.vertx().deployVerticle(new EventStoreVerticle());
    }
}
