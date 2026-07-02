import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class H2ToPostgresMigrator {

    private static final List<String> TABLES = Arrays.asList("products", "news_articles", "orders");

    public static void main(String[] args) throws Exception {
        String h2Url = env("H2_URL", "jdbc:h2:file:./data/kingnest;AUTO_SERVER=TRUE");
        String h2User = env("H2_USERNAME", "sa");
        String h2Pass = env("H2_PASSWORD", "");

        String pgUrl = env("DB_URL", "jdbc:postgresql://localhost:5432/kingnest");
        String pgUser = env("DB_USERNAME", "postgres");
        String pgPass = env("DB_PASSWORD", "postgres");

        Class.forName("org.h2.Driver");
        Class.forName("org.postgresql.Driver");

        try (Connection h2Conn = DriverManager.getConnection(h2Url, h2User, h2Pass);
             Connection pgConn = DriverManager.getConnection(pgUrl, pgUser, pgPass)) {

            pgConn.setAutoCommit(false);

            ensureSchema(pgConn);
            truncateTargets(pgConn);

            for (String table : TABLES) {
                migrateTable(h2Conn, pgConn, table);
            }

            syncSequence(pgConn, "products");
            syncSequence(pgConn, "news_articles");
            syncSequence(pgConn, "orders");

            pgConn.commit();
            System.out.println("Migration completed successfully.");
        }
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void ensureSchema(Connection pgConn) throws SQLException {
        try (Statement st = pgConn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS products (
                  id BIGSERIAL PRIMARY KEY,
                  slug VARCHAR(255) NOT NULL UNIQUE,
                  title VARCHAR(255) NOT NULL,
                  short_desc VARCHAR(1000),
                  category VARCHAR(255),
                  price BIGINT NOT NULL,
                  image VARCHAR(255) NOT NULL,
                  gallery TEXT,
                  benefits TEXT,
                  usage TEXT,
                  specs TEXT,
                  description TEXT,
                  highlights TEXT,
                  product_type VARCHAR(255),
                  need TEXT,
                  status TEXT,
                  badge VARCHAR(255),
                  content_html TEXT
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS news_articles (
                  id BIGSERIAL PRIMARY KEY,
                  slug VARCHAR(255) NOT NULL UNIQUE,
                  title VARCHAR(255) NOT NULL,
                  category VARCHAR(255) NOT NULL,
                  excerpt VARCHAR(1000),
                  image VARCHAR(255) NOT NULL,
                  published_year VARCHAR(255),
                  content_html TEXT NOT NULL,
                  sort_order INTEGER NOT NULL DEFAULT 0
                )
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS orders (
                  id BIGSERIAL PRIMARY KEY,
                  order_code VARCHAR(64) NOT NULL UNIQUE,
                  customer_name VARCHAR(255) NOT NULL,
                  customer_email VARCHAR(255) NOT NULL,
                  customer_phone VARCHAR(255) NOT NULL,
                  customer_address VARCHAR(500) NOT NULL,
                  customer_note VARCHAR(1000),
                  items_json TEXT NOT NULL,
                  coupon VARCHAR(255),
                  subtotal BIGINT NOT NULL,
                  discount BIGINT NOT NULL,
                  total BIGINT NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  payment_method VARCHAR(16) NOT NULL,
                  email_sent BOOLEAN NOT NULL,
                  created_at TIMESTAMP NOT NULL
                )
                """);
        }
    }

    private static void truncateTargets(Connection pgConn) throws SQLException {
        try (Statement st = pgConn.createStatement()) {
            st.execute("TRUNCATE TABLE orders, news_articles, products RESTART IDENTITY");
        }
    }

    private static void migrateTable(Connection h2Conn, Connection pgConn, String table) throws SQLException {
        String selectSql = "SELECT * FROM " + table;
        try (PreparedStatement source = h2Conn.prepareStatement(selectSql);
             ResultSet rs = source.executeQuery()) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            List<String> columns = new ArrayList<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                columns.add(meta.getColumnLabel(i).toLowerCase(Locale.ROOT));
            }

            String columnList = columns.stream().map(H2ToPostgresMigrator::quote).collect(Collectors.joining(", "));
            String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
            String insertSql = "INSERT INTO " + quote(table) + " (" + columnList + ") VALUES (" + placeholders + ")";

            int count = 0;
            try (PreparedStatement target = pgConn.prepareStatement(insertSql)) {
                while (rs.next()) {
                    for (int i = 1; i <= colCount; i++) {
                        Object value = rs.getObject(i);
                        if (value instanceof Clob clob) {
                            value = clob.getSubString(1, (int) clob.length());
                        }
                        target.setObject(i, value);
                    }
                    target.addBatch();
                    count++;
                }
                target.executeBatch();
            }

            System.out.printf("Migrated table %s: %d rows%n", table, count);
        }
    }

    private static void syncSequence(Connection pgConn, String table) throws SQLException {
        String sql = "SELECT setval(pg_get_serial_sequence(?, 'id'), COALESCE(MAX(id), 1), MAX(id) IS NOT NULL) FROM " + quote(table);
        try (PreparedStatement st = pgConn.prepareStatement(sql)) {
            st.setString(1, table);
            st.execute();
        }
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}

