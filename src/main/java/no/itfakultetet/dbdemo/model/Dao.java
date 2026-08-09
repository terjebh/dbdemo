package no.itfakultetet.dbdemo.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Kobler til databaser og utfører SQL.
 * <p>
 * Sikkerhets- og kvalitetsforbedringer (2026-08-09):
 * <ul>
 *   <li>Ingen passord i JDBC-URL-er — Properties-objekt til DriverManager.</li>
 *   <li>try-with-resources: Connection, Statement og ResultSet lukkes alltid.</li>
 *   <li>PreparedStatement for katalog-spørringer (ingen SQL-injeksjon).</li>
 *   <li>queryTimeout + maxRows på ALLE spørringer (student kan ikke henge serveren).</li>
 *   <li>Read-only-kobling som standard (hindrer uhell som DROP/UPDATE).</li>
 *   <li>Feil kastes som SQLException — håndteres i controller, ikke som Object-retur.</li>
 * </ul>
 *
 * @author Terje Berg-Hansen
 */
@Component
public class Dao {

    private static final Logger logger = LoggerFactory.getLogger(Dao.class);

    @Value("${db.host:noderia.com}")
    private String host;

    @Value("${db.port.oracle:1521}")
    private int oraclePort;

    @Value("${db.port.mssql:1433}")
    private int mssqlPort;

    @Value("${db.oracle.service:FREE}")
    private String oracleService;

    @Value("${db.query.timeout.seconds:15}")
    private int queryTimeoutSeconds;

    @Value("${db.query.max.rows:10000}")
    private int maxRows;

    @Value("${db.readonly:true}")
    private boolean readOnly;

    /** Resultat av en vilkårlig SQL-spørring: kolonneoverskrifter + rader. */
    public record QueryResult(List<String> header, List<List<String>> rows) {
    }

    /**
     * Åpner en tilkobling til angitt RDBMS/database.
     * Passord sendes via Properties — aldri i URL-en.
     */
    private Connection connect(String rdbms, String db, String username, String pwd) throws SQLException {
        String url = buildUrl(rdbms, db);
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", pwd);

        Connection conn = DriverManager.getConnection(url, props);
        if (readOnly) {
            try {
                conn.setReadOnly(true);
            } catch (SQLException e) {
                logger.debug("setReadOnly støttes ikke av {}: {}", rdbms, e.getMessage());
            }
        }
        return conn;
    }

    /** Bygger JDBC-URL uten brukernavn/passord. */
    private String buildUrl(String rdbms, String db) {
        return switch (rdbms) {
            case "postgres" -> "jdbc:postgresql://" + host + "/" + db + "?ssl=false";
            case "microsoft" -> "jdbc:sqlserver://" + host + ":" + mssqlPort
                    + ";databaseName=" + db + ";encrypt=false";
            case "oracle" -> "jdbc:oracle:thin:@" + host + ":" + oraclePort + ":" + oracleService;
            case "mysql" -> "jdbc:mysql://" + host + "/" + db;
            default -> throw new IllegalArgumentException("Ukjent RDBMS: " + rdbms);
        };
    }

    /** Setter timeout og maxRows på et statement. */
    private void begrens(Statement st) throws SQLException {
        st.setQueryTimeout(queryTimeoutSeconds);
        st.setMaxRows(maxRows);
    }

    /**
     * Kjører en vilkårlig SQL-spørring (studentens egen SQL) og returnerer
     * header + rader. Read-only, timeout og maxRows beskytter serveren.
     */
    public QueryResult executeQuery(String rdbms, String db, String query,
                                    String username, String pwd) throws SQLException {
        try (Connection conn = connect(rdbms, db, username, pwd);
             Statement st = conn.createStatement()) {
            begrens(st);
            long start = System.currentTimeMillis();
            try (ResultSet rs = st.executeQuery(query)) {
                long elapsed = System.currentTimeMillis() - start;
                ResultSetMetaData meta = rs.getMetaData();
                int kolonner = meta.getColumnCount();

                List<String> header = new ArrayList<>(kolonner);
                for (int i = 1; i <= kolonner; i++) {
                    header.add(meta.getColumnLabel(i));
                }

                List<List<String>> rader = new ArrayList<>();
                while (rs.next()) {
                    List<String> rad = new ArrayList<>(kolonner);
                    for (int i = 1; i <= kolonner; i++) {
                        rad.add(rs.getString(i));
                    }
                    rader.add(rad);
                }
                logger.info("Query mot {} ({}) tok {} ms, {} rader", rdbms, db, elapsed, rader.size());
                return new QueryResult(header, rader);
            }
        }
    }

    /**
     * Lister databaser/skjemaer brukeren har tilgang til i det valgte systemet.
     * Katalog-spørringene er per RDBMS og bruker PreparedStatement.
     */
    public List<String> getDatabases(String rdbms, String username, String pwd) throws SQLException {
        String sql;
        String db = switch (rdbms) {
            case "postgres" -> "postgres";
            case "microsoft" -> "master";
            case "oracle" -> "";
            case "mysql" -> "";
            default -> throw new IllegalArgumentException("Ukjent RDBMS: " + rdbms);
        };

        switch (rdbms) {
            case "postgres" -> sql = "SELECT datname FROM pg_database "
                    + "WHERE has_database_privilege(?, datname, 'CONNECT') AND NOT datistemplate "
                    + "ORDER BY datname";
            case "microsoft" -> sql = "SELECT name FROM sys.databases WHERE HAS_DBACCESS(name) = 1 ORDER BY name";
            case "oracle" -> sql = "SELECT owner FROM ("
                    + "  SELECT owner FROM all_tables "
                    + "  UNION SELECT owner FROM all_views"
                    + ") WHERE owner NOT IN ('SYS','SYSTEM','CTXSYS','DBSNMP','MDSYS','OLAPSYS',"
                    + "'ORDSYS','OUTLN','WMSYS','XDB','APPQOSSYS','AUDSYS','DVSYS','LBACSYS',"
                    + "'ORDDATA','ORDPLUGINS','SI_INFORMTN_SCHEMA','SYSBACKUP','SYSDG','SYSKM','SYSMAN') "
                    + "ORDER BY owner";
            case "mysql" -> sql = "SHOW DATABASES";
            default -> throw new IllegalArgumentException("Ukjent RDBMS: " + rdbms);
        }

        try (Connection conn = connect(rdbms, db, username, pwd);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if ("postgres".equals(rdbms)) {
                ps.setString(1, username);
            }
            begrens(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> dbList = new ArrayList<>();
                while (rs.next()) {
                    String navn = rs.getString(1);
                    // Skjul system-databaser for MySQL
                    if ("mysql".equals(rdbms) && List.of("information_schema", "mysql",
                            "performance_schema", "sys").contains(navn)) {
                        continue;
                    }
                    dbList.add(navn);
                }
                return dbList.stream().sorted().toList();
            }
        }
    }

    /**
     * Lister tabeller + views i valgt database/skjema.
     * Returnerer data (schema, navn, type) — ikke HTML (XSS-sikkert).
     */
    public List<List<String>> getTables(String rdbms, String db, String username, String pwd) throws SQLException {
        String sql;
        String tilkoblingsDb;

        switch (rdbms) {
            case "postgres" -> {
                sql = "SELECT table_schema, table_name, table_type FROM information_schema.tables "
                        + "WHERE table_schema NOT IN ('pg_catalog','information_schema') "
                        + "ORDER BY table_schema, table_type, table_name";
                tilkoblingsDb = db;
            }
            case "microsoft" -> {
                sql = "SELECT TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_CATALOG = ? ORDER BY TABLE_TYPE";
                tilkoblingsDb = db;
            }
            case "oracle" -> {
                sql = "SELECT owner, table_name, 'TABLE' FROM all_tables WHERE owner = ? "
                        + "UNION SELECT owner, view_name, 'VIEW' FROM all_views WHERE owner = ? "
                        + "ORDER BY 1, 2";
                tilkoblingsDb = db;
            }
            case "mysql" -> {
                sql = "SELECT table_schema, table_name, table_type FROM information_schema.tables "
                        + "WHERE table_schema = ? ORDER BY table_type";
                tilkoblingsDb = db;
            }
            default -> throw new IllegalArgumentException("Ukjent RDBMS: " + rdbms);
        }

        try (Connection conn = connect(rdbms, tilkoblingsDb, username, pwd);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if ("microsoft".equals(rdbms)) {
                ps.setString(1, db);
            } else if ("oracle".equals(rdbms)) {
                ps.setString(1, db);
                ps.setString(2, db);
            } else if ("mysql".equals(rdbms)) {
                ps.setString(1, db);
            }
            begrens(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<List<String>> tabeller = new ArrayList<>();
                while (rs.next()) {
                    List<String> rad = new ArrayList<>(3);
                    rad.add(rs.getString(1));
                    rad.add(rs.getString(2));
                    rad.add(rs.getString(3));
                    tabeller.add(rad);
                }
                return tabeller;
            }
        }
    }
}
