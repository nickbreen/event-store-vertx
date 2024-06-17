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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
class JdbcEventStoreTest
{
    private static final String TEST_ADDRESS = "test.address";
    private static final Instant time = Instant.now();
    private JDBCPool pool;

    @AfterEach
    void tearDown()
    {
        Optional.ofNullable(pool).ifPresent(pool -> pool.query("DROP TABLE IF EXISTS journal").execute());
        Optional.ofNullable(pool).ifPresent(JDBCPool::close);
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:sqlite::memory:", "jdbc:h2:mem:journal"})
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

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:sqlite::memory:journal", "jdbc:h2:mem:journal"})
    void shouldInitialiseTable(final String jdbcUri, final Vertx vertx, final VertxTestContext context) throws InterruptedException
    {
        pool = JDBCPool.pool(vertx, JsonObject.of("url", jdbcUri));
        JdbcEventStore.Factory.select(jdbcUri).create(pool)
                .onComplete(x -> context.completeNow(), context::failNow);

    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:sqlite::memory:journal", "jdbc:h2:mem:journal;DATABASE_TO_UPPER=false"})
    void shouldStoreEvent(final String jdbcUri, final Vertx vertx, final VertxTestContext context)
    {
        pool = JDBCPool.pool(vertx, JsonObject.of("url", jdbcUri));
        final EventStore eventStore = JdbcEventStore.Factory.select(jdbcUri).create(pool).toCompletionStage().toCompletableFuture().join();

        eventStore.store(1, time, TEST_ADDRESS, true, JsonObject.of(), JsonArray.of())
                .onComplete(x -> context.completeNow(), context::failNow);

    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:sqlite::memory:journal", "jdbc:h2:mem:journal;DATABASE_TO_UPPER=false"})
    @Disabled("need to handle reading types")
    void shouldStoreEventAndReplayEvent(final String jdbcUri, final Vertx vertx, final VertxTestContext context)
    {
        pool = JDBCPool.pool(vertx, JsonObject.of("url", jdbcUri));
        final EventStore eventStore = JdbcEventStore.Factory.select(jdbcUri).create(pool).toCompletionStage().toCompletableFuture().join();

        eventStore.store(1, time, TEST_ADDRESS, true, JsonObject.of(), JsonArray.of())
                .onFailure(context::failNow)
                .toCompletionStage()
                .toCompletableFuture()
                .join();

        final Checkpoint checkpoint = context.checkpoint(1);
        eventStore.replay(event -> context.verify(() -> {
            assertEquals(1, event.sequence());
            assertEquals(time, event.timestamp());
            assertEquals(TEST_ADDRESS, event.address());
            assertTrue(event.send());
            assertEquals(JsonObject.of(), event.body());
            assertEquals(JsonArray.of(), event.headers());
            checkpoint.flag();
        }));
    }
}