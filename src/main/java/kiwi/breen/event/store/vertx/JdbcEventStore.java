package kiwi.breen.event.store.vertx;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.desc.ColumnDescriptor;

import java.sql.JDBCType;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JdbcEventStore implements EventStore
{
    public enum Factory
    {
        SQLITE(
                Pattern.compile("^jdbc:(?:tc:)?sqlite:"),
                "CREATE TABLE IF NOT EXISTS journal (sequence BIGINT PRIMARY KEY, timestamp VARCHAR, address VARCHAR, send INTEGER, body VARCHAR, headers VARCHAR)",
                "INSERT INTO journal (sequence, timestamp, address, send, body, headers) VALUES (?, ?, ?, ?, ?, ?)",
                "SELECT * FROM journal"),
        H2(
                Pattern.compile("^jdbc:(?:tc:)?h2:"),
                "CREATE TABLE IF NOT EXISTS journal (sequence BIGINT PRIMARY KEY, timestamp TIMESTAMP WITH TIME ZONE, address VARCHAR, send BOOLEAN, body VARCHAR, headers VARCHAR)",
                "INSERT INTO journal (sequence, timestamp, address, send, body, headers) VALUES (?, ?, ?, ?, ?, ?)",
                "SELECT * FROM journal"),
        OTHER(
                Pattern.compile("^jdbc:(?:tc:)?"),
                "CREATE TABLE IF NOT EXISTS journal (sequence BIGINT PRIMARY KEY, timestamp TIMESTAMP, address VARCHAR, send BOOLEAN, body VARCHAR, headers VARCHAR)",
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
    private final String selectSql;
    private final BiFunction<Row, Map<String, JDBCType>, Event> rowMapper = JdbcEventStore::rowMapper;
    private final Function<Event, Tuple> parameterMapper = JdbcEventStore::parameterMapper;

    protected JdbcEventStore(final JDBCPool pool, final String insertDml, final String selectSql)
    {
        this.pool = pool;
        this.insertDml = insertDml;
        this.selectSql = selectSql;
    }


    @Override
    public Future<Void> store(final Event event)
    {
        synchronized (pool)
        {
            return pool.preparedQuery(insertDml)
                    .execute(parameterMapper.apply(event))
                    .mapEmpty();
        }
    }

    @Override
    public void replay(final Consumer<Event> consumer)
    {
        pool.query(selectSql).execute().onSuccess(rows -> {
            final Map<String, JDBCType> columnJdbcType = rows.columnDescriptors().stream().collect(Collectors.toMap(ColumnDescriptor::name, ColumnDescriptor::jdbcType));
            rows.forEach(row -> consumer.accept(rowMapper.apply(row, columnJdbcType)));
        });
    }

    private static Tuple parameterMapper(final Event event)
    {
        return Tuple.of(
                event.sequence(),
                event.timestamp(),
                event.address(),
                event.send(),
                event.body().encode(),
                event.headers().encode());
    }

    private static Event rowMapper(final Row row, final Map<String, JDBCType> columnJdbcType)
    {
        final long sequence = row.getLong("sequence");
        final Instant timestamp = switch (columnJdbcType.get("timestamp"))
        {
            case JDBCType.TIMESTAMP_WITH_TIMEZONE -> Instant.from(row.getOffsetDateTime("timestamp"));
            case JDBCType.TIMESTAMP -> Instant.from(row.getLocalDateTime("timestamp").atZone(ZoneOffset.UTC));
            default -> Instant.parse(row.getString("timestamp"));
        };
        final String address = row.getString("address");
        final boolean sent = switch (columnJdbcType.get("send"))
        {
            case JDBCType.BOOLEAN, JDBCType.BIT -> row.getBoolean("send");
            default -> row.getInteger("send") != 0;
        };
        final JsonObject body = switch (columnJdbcType.get("body"))
        {
            case JDBCType.CLOB, JDBCType.BLOB -> row.getBuffer("body").toJsonObject();
            default -> Buffer.buffer(row.getString("body")).toJsonObject();
        };
        final JsonArray headers = switch (columnJdbcType.get("headers"))
        {
            case JDBCType.CLOB, JDBCType.BLOB -> row.getBuffer("headers").toJsonArray();
            default -> Buffer.buffer(row.getString("headers")).toJsonArray();
        };
        return new Event(sequence, timestamp, address, sent, body, headers);
    }
}
