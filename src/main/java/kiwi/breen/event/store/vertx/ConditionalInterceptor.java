package kiwi.breen.event.store.vertx;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryContext;

import java.util.Optional;
import java.util.function.Predicate;

public class ConditionalInterceptor<T> implements Handler<DeliveryContext<T>>
{
    protected final Predicate<DeliveryContext<T>> filter;
    private final Handler<DeliveryContext<T>> handler;

    public ConditionalInterceptor(final Predicate<DeliveryContext<T>> filter, final Handler<DeliveryContext<T>> handler)
    {
        this.filter = filter;
        this.handler = handler;
    }

    @Override
    public void handle(final DeliveryContext<T> deliveryContext)
    {
        Optional.of(deliveryContext).filter(filter).ifPresentOrElse(handler::handle, deliveryContext::next);
    }
}
