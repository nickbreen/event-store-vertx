package kiwi.breen.event.store.vertx;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.dropwizard.MetricsService;
import io.vertx.jdbcclient.JDBCPool;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class Main
{
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(Main.class);

    public static void main(final String[] args)
    {
        final Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(
                new DropwizardMetricsOptions().setJmxEnabled(true).setEnabled(true)));

        final TimeStampInterceptor timeStampInterceptor = new TimeStampInterceptor(Instant::now);
        vertx.eventBus().addOutboundInterceptor(timeStampInterceptor);

        final AtomicLong sequence = new AtomicLong();
        final SequenceInterceptor sequenceInterceptor = new SequenceInterceptor(sequence);
        vertx.eventBus().addOutboundInterceptor(sequenceInterceptor);

        final String jdbcUri = "jdbc:sqlite:file:./event-store.sqlite";
        final JDBCPool pool = JDBCPool.pool(vertx, JsonObject.of(
                "url", jdbcUri,
                "initial_pool_size", 1,
                "min_pool_size", 1,
                "max_pool_size", 1));

        final MetricsService metricsService = MetricsService.create(vertx);
        vertx.setPeriodic(1000, v -> logMetrics(metricsService.getMetricsSnapshot(vertx)));
        pool.query("SELECT MAX(sequence) FROM journal")
                .execute()
                .onFailure(cause -> LOGGER.error("Failed to query event store", cause))
                .map(result -> result.iterator().next())
                .map(row -> row.getLong(0))
                .onSuccess(sequence::set)
                .onComplete(
                        v -> LOGGER.info("Event store sequence {}", sequence.get()),
                        ex -> LOGGER.error("Failed to initialize event store sequence", ex));

        JdbcEventStore.Factory.select(jdbcUri).create(pool)
                .onFailure(cause -> LOGGER.error("Failed to create event store", cause))
                .onSuccess(eventStore -> {
                    final EventStoreInterceptor eventStoreInterceptor = new EventStoreInterceptor(
                            eventStore,
                            SequenceInterceptor::extractSequence,
                            TimeStampInterceptor::extractTimestamp);

                    final ConditionalInterceptor<JsonObject> eventPrefixInterceptor = new ConditionalInterceptor<>(
                            deliveryContext -> deliveryContext.message().address().startsWith("event."),
                            eventStoreInterceptor);

                    vertx.eventBus().addOutboundInterceptor(eventPrefixInterceptor);

                    vertx.deployVerticle(new NoiseVerticle(100_000, "event.test")).onComplete(
                            x -> LOGGER.info("Verticle started {}", x),
                            cause -> LOGGER.error("Verticle failed to start", cause)
                    );
                });
    }

    private static void logMetrics(final JsonObject metricsSnapshot)
    {
        List.of(
                        "vertx.eventbus.messages.bytes-written",
                        "vertx.eventbus.messages.pending",
                        "vertx.eventbus.messages.discarded",
                        "vertx.eventbus.messages.sent",
                        "vertx.eventbus.messages.delivered",
                        "vertx.eventbus.messages.received")
                .forEach(metric -> logMetric(metric, metricsSnapshot.getJsonObject(metric)));
    }

    private static void logMetric(final String metric, final JsonObject metricSnapshot)
    {
        switch (metricSnapshot.getString("type"))
        {
            case "counter":
                LOGGER.info("{} {}", metric, metricSnapshot.getNumber("count"));
                break;
            case "gauge":
                LOGGER.info("{} {}", metric, metricSnapshot.getNumber("value"));
                break;
            case "meter":
                LOGGER.info(
                        "{} {} {} {} {} {} {}",
                        metric,
                        metricSnapshot.getNumber("meanRate"),
                        metricSnapshot.getNumber("oneSecondRate"),
                        metricSnapshot.getNumber("oneMinuteRate"),
                        metricSnapshot.getNumber("fiveMinuteRate"),
                        metricSnapshot.getNumber("fifteenMinuteRate"),
                        metricSnapshot.getString("rate"));
                break;
        }
    }
}
