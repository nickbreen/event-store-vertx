package kiwi.breen.event.store.vertx;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
class JdbcEventStoreTest
{
    private static final String TEST_ADDRESS = "test.address";
    private static final Instant timeMillis = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private JDBCPool pool;

    @AfterEach
    void tearDown()
    {
        Optional.ofNullable(pool).ifPresent(pool -> pool.query("DROP TABLE IF EXISTS journal").execute());
        Optional.ofNullable(pool).ifPresent(JDBCPool::close);
    }

    @ParameterizedTest(name = "{0} {displayName}")
    @ValueSource(strings = {"jdbc:sqlite::memory:", "jdbc:h2:mem:"})
    void apparentlyDatabaseMetadataIsNotSupported(final String jdbcUri, final Vertx vertx, final VertxTestContext context)
    {
        pool = JDBCPool.pool(vertx, JsonObject.of("url", jdbcUri));
        pool.getConnection(context.succeeding(con -> {
            try
            {
                con.databaseMetadata();
                context.failNow("Expected Database metadata to be not supported");
            }
            catch (final UnsupportedOperationException e)
            {
                context.completeNow();
            }
        }));
    }

    @ParameterizedTest(name = "{0} {displayName}")
    @ValueSource(strings = {"jdbc:sqlite::memory:", "jdbc:h2:mem:"})
    void shouldInitialiseTable(final String jdbcUri, final Vertx vertx, final VertxTestContext context)
    {
        pool = JDBCPool.pool(vertx, JsonObject.of("url", jdbcUri));
        JdbcEventStore.Factory.select(jdbcUri).create(pool)
                .onComplete(x -> context.completeNow(), context::failNow);

    }

    @ParameterizedTest(name = "{0} {displayName}")
    @ValueSource(strings = {"jdbc:sqlite::memory:", "jdbc:h2:mem:"})
    void shouldStoreEvent(final String jdbcUri, final Vertx vertx, final VertxTestContext context)
    {
        pool = JDBCPool.pool(vertx, JsonObject.of("url", jdbcUri));
        final EventStore eventStore = JdbcEventStore.Factory.select(jdbcUri).create(pool).toCompletionStage().toCompletableFuture().join();

        eventStore.store(1, timeMillis, TEST_ADDRESS, true, JsonObject.of(), JsonArray.of())
                .onComplete(x -> context.completeNow(), context::failNow);

    }

    @ParameterizedTest(name = "{0} {displayName}")
    @ValueSource(strings = {"jdbc:sqlite::memory:", "jdbc:h2:mem:;DATABASE_TO_UPPER=false"})
    @Timeout(1000)
    void shouldStoreEventAndReplayEvent(final String jdbcUri, final Vertx vertx, final VertxTestContext context)
    {
        pool = JDBCPool.pool(vertx, JsonObject.of("url", jdbcUri));
        final EventStore eventStore = JdbcEventStore.Factory.select(jdbcUri).create(pool).toCompletionStage().toCompletableFuture().join();

        final JsonObject body = JsonObject.of("foo", "bar");
        final JsonArray headers = JsonArray.of(JsonObject.of("header", "value"));
        eventStore.store(1, timeMillis, TEST_ADDRESS, true, body, headers)
                .onFailure(context::failNow)
                .toCompletionStage()
                .toCompletableFuture()
                .join();

        final Checkpoint checkpoint = context.checkpoint(1);
        eventStore.replay(event -> context.verify(() -> {
            assertEquals(1, event.sequence());
            assertEquals(timeMillis, event.timestamp());
            assertEquals(TEST_ADDRESS, event.address());
            assertTrue(event.send());
            assertEquals(body, event.body());
            assertEquals(headers, event.headers());
            checkpoint.flag();
        }));
    }
}