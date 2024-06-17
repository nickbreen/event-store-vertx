package kiwi.breen.event.store.vertx;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.desc.ColumnDescriptor;

import java.sql.JDBCType;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class JdbcEventStore implements EventStore
{
    public enum Factory
    {
        SQLITE(
                Pattern.compile("^jdbc:(?:tc:)?sqlite:"),
                "CREATE TABLE journal (sequence BIGINT PRIMARY KEY, timestamp TEXT, address TEXT, send BOOLEAN, body BLOB, headers BLOB)", // ? IF NOT EXISTS?
                "INSERT INTO journal (sequence, timestamp, address, send, body, headers) VALUES (?, ?, ?, ?, ?, ?)",
                "SELECT * FROM journal"),
        H2(
                Pattern.compile("^jdbc:(?:tc:)?h2:"),
                "CREATE TABLE journal (sequence BIGINT PRIMARY KEY, timestamp TIMESTAMP, address VARCHAR(80), send BOOLEAN, body JSON, headers JSON)", // ? IF NOT EXISTS?
                "INSERT INTO journal (sequence, timestamp, address, send, body, headers) VALUES (?, ?, ?, ?, ? FORMAT JSON, ? FORMAT JSON)",
                "SELECT * FROM journal"),
        OTHER(
                Pattern.compile("^jdbc:(?:tc:)?"),
                "CREATE TABLE journal (sequence BIGINT PRIMARY KEY, timestamp TIMESTAMP, address VARCHAR(80), send BOOLEAN, body BLOB, headers BLOB)", // ? IF NOT EXISTS?
                "INSERT INTO journal (sequence, timestamp, address, send, body, headers) VALUES (?, ?, ?, ?, ?, ?)",
                "SELECT * FROM journal");

        public static Factory select(final String jdbcUri)
        {
            return Stream.of(values())
                    .filter(factory -> factory.selector.matcher(jdbcUri).find())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Not a JDBC URI: " + jdbcUri));
        }

        private final Pattern selector;
        private final String createDdl;
        private final String insertDml;
        private final String selectSql;

        Factory(final Pattern selector, final String createDdl, final String insertDml, final String selectSql)
        {
            this.selector = selector;
            this.createDdl = createDdl;
            this.insertDml = insertDml;
            this.selectSql = selectSql;
        }

        public Future<JdbcEventStore> create(final JDBCPool pool)
        {
            return pool.query(createDdl)
                    .execute()
                    .map(x -> new JdbcEventStore(pool, insertDml, selectSql));
        }
    }

    private final JDBCPool pool;
    private final String insertDml;
    private final String selectDml;

    protected JdbcEventStore(final JDBCPool pool, final String insertDml, final String selectDml)
    {
        this.pool = pool;
        this.insertDml = insertDml;
        this.selectDml = selectDml;
    }

    @Override
    public Future<Void> store(final Event event)
    {
        return pool.preparedQuery(insertDml)
                .execute(Tuple.of(
                        event.sequence(),
                        event.timestamp(),
                        event.address(),
                        event.send(),
                        event.body().toBuffer().getBytes(),
                        event.headers().toBuffer().getBytes()))
                .mapEmpty();
    }

    @Override
    public void replay(final Consumer<Event> consumer)
    {
        pool.query(selectDml).execute().onSuccess(rows -> {
            final JDBCType timestampJdbcType = rows.columnDescriptors().stream().filter(x -> x.name().equals("timestamp")).map(ColumnDescriptor::jdbcType).findFirst().orElse(JDBCType.OTHER);
            // TODO handle other types
            rows.forEach(row -> {
                final long sequence = row.getLong("sequence");
                final Instant timestamp = switch (timestampJdbcType)
                {
                    case JDBCType.TIMESTAMP -> Instant.from(row.getLocalDateTime("timestamp").atZone(ZoneOffset.UTC));
                    default -> Instant.parse(row.getString("timestamp"));
                };
                final String address = row.getString("address");
                final boolean sent = row.getBoolean("send");
                final JsonObject body = row.getJsonObject("body");
                final JsonArray headers = row.getJsonArray("headers");
                consumer.accept(new Event(sequence, timestamp, address, sent, body, headers));
            });
        });
    }

}
