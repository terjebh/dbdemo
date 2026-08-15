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
 * Tilkoblingsinformasjonen kommer fra {@link DbConnection}-objekter
 * (konfigurert i first-run-veiviseren), ikke fra properties.
 * <p>
 * Sikkerhet:
 * <ul>
 *   <li>Ingen passord i JDBC-URL-er — Properties-objekt til DriverManager.</li>
 *   <li>try-with-resources: Connection, Statement og ResultSet lukkes alltid.</li>
 *   <li>PreparedStatement for katalog-spørringer (ingen SQL-injeksjon).</li>
 *   <li>queryTimeout + maxRows på ALLE spørringer (student kan ikke henge serveren).</li>
 *   <li>Read-only-kobling som standard (hindrer uhell som DROP/UPDATE).</li>
 * </ul>
 *
 * @author Terje Berg-Hansen
 */
@Component
public class Dao {

    private static final Logger logger = LoggerFactory.getLogger(Dao.class);

    @Value("${db.query.timeout.seconds:15}")
    private int queryTimeoutSeconds;

    @Value("${db.query.max.rows:10000}")
    private int maxRows;

    @Value("${db.readonly:true}")
    private boolean readOnly;

    /** Resultat av en vilkårlig SQL-spørring: kolonneoverskrifter + rader + utførelsestid. */
    public record QueryResult(List<String> header, List<List<String>> rows, long tidMs) {
    }

    /** Standardporter per RDBMS (brukes i setup-skjemaet som forslag). */
    public static int defaultPort(String rdbms) {
        return switch (rdbms) {
            case "postgres" -> 5432;
            case "microsoft" -> 1433;
            case "oracle" -> 1521;
            case "mysql" -> 3306;
            default -> 0;
        };
    }

    /**
     * Åpner en tilkobling via en DbConnection.
     * Passord sendes via Properties — aldri i URL-en.
     */
    private Connection connect(DbConnection conn) throws SQLException {
        if (conn == null || !conn.isValid()) {
            throw new SQLException("Tilkoblingen er ikke konfigurert (RDBMS/host/bruker/passord mangler)");
        }
        String url = buildUrl(conn);
        Properties props = new Properties();
        props.setProperty("user", conn.getUsername());
        props.setProperty("password", conn.getPassword());

        Connection c = DriverManager.getConnection(url, props);
        if (readOnly) {
            try {
                c.setReadOnly(true);
            } catch (SQLException e) {
                logger.debug("setReadOnly støttes ikke av {}: {}", conn.getRdbms(), e.getMessage());
            }
        }
        return c;
    }

    /** Bygger JDBC-URL fra en DbConnection (uten brukernavn/passord). */
    public String buildUrl(DbConnection conn) {
        String rdbms = conn.getRdbms();
        return switch (rdbms) {
            case "postgres" -> "jdbc:postgresql://" + conn.getHost() + ":" + conn.getPort()
                    + "/" + conn.getDatabase() + "?ssl=false";
            case "microsoft" -> "jdbc:sqlserver://" + conn.getHost() + ":" + conn.getPort()
                    + ";databaseName=" + conn.getDatabase() + ";encrypt=false";
            case "oracle" -> "jdbc:oracle:thin:@//" + conn.getHost() + ":" + conn.getPort()
                    + "/" + conn.getDatabase();
            case "mysql" -> "jdbc:mysql://" + conn.getHost() + ":" + conn.getPort()
                    + "/" + conn.getDatabase();
            default -> throw new IllegalArgumentException("Ukjent RDBMS: " + rdbms);
        };
    }

    /** Tester om en tilkobling er gyldig (kobler til og lukker). Returnerer null ved suksess. */
    public String testConnection(DbConnection conn) {
        try (Connection c = connect(conn)) {
            return null; // OK
        } catch (SQLException e) {
            logger.warn("Tilkoblingstest feilet ({}): {}", conn.getRdbms(), e.getMessage());
            return e.getMessage();
        }
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
    public QueryResult executeQuery(DbConnection conn, String db, String query) throws SQLException {
        return executeQuery(conn, db, query, null);
    }

    /**
     * Kjører en vilkårlig SQL-spørring med valgfri search_path (psql-oppførsel:
     * «SET search_path TO skjema» gjelder for alle påfølgende spørringer).
     */
    public QueryResult executeQuery(DbConnection conn, String db, String query, String searchPath) throws SQLException {
        // For Oracle betyr "database" egentlig skjema; URL-en bruker service-navnet.
        // For de andre brukes db direkte i URL-en, så vi lager en kopi med riktig database.
        DbConnection kobling = conn;
        if (!"oracle".equals(conn.getRdbms())) {
            kobling = kopiMedDatabase(conn, db);
        }
        try (Connection c = connect(kobling);
             Statement st = c.createStatement()) {
            begrens(st);
            // psql-oppførsel: sett search_path på tilkoblingen FØR spørringen.
            // PostgreSQL støtter ikke parameterbinding i SET — men verdien er
            // allerede strengt validert (kun bokstaver/tall/_.-"$) i
            // QueryRestController.fangOppSearchPath, så interpolering er trygt.
            if (searchPath != null && !searchPath.isBlank()
                    && "postgres".equals(conn.getRdbms())) {
                String gyldig = searchPath.replaceAll("[^A-Za-z0-9_.\\\"\\-$]", "");
                if (gyldig.equals(searchPath)) {
                    st.execute("SET search_path TO " + searchPath);
                }
            }
            long start = System.currentTimeMillis();
            // execute() håndterer BÅDE SELECT (ResultSet) og DDL/andre
            // setninger (CREATE/DROP/ALTER VIEW/TABLE gir ingen ResultSet —
            // executeQuery() ville kastet «No results were returned»).
            boolean harResultat = st.execute(query);
            long elapsed = System.currentTimeMillis() - start;
            if (!harResultat) {
                logger.info("DDL/oppdatering mot {} tok {} ms (ingen resultatsett)", db, elapsed);
                return new QueryResult(List.of(), List.of(), elapsed);
            }
            try (ResultSet rs = st.getResultSet()) {
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
                        rad.add(formaterVerdi(rs.getObject(i)));
                    }
                    rader.add(rad);
                }
                logger.info("Query mot {} tok {} ms, {} rader", db, elapsed, rader.size());
                return new QueryResult(header, rader, elapsed);
            }
        }
    }

    private DbConnection kopiMedDatabase(DbConnection conn, String db) {
        DbConnection kopi = new DbConnection();
        kopi.setRdbms(conn.getRdbms());
        kopi.setEnabled(true);
        kopi.setHost(conn.getHost());
        kopi.setPort(conn.getPort());
        kopi.setDatabase(db);
        kopi.setUsername(conn.getUsername());
        kopi.setPassword(conn.getPassword());
        return kopi;
    }

    /**
     * Formaterer en JDBC-verdi for visning i resultat-tabellen.
     * Dato-verdier (java.sql.Date/Timestamp/LocalDate) vises som ren dato når
     * klokkeslettet er midnatt — Oracle DATE ga f.eks. «2026-08-11 00:00:00».
     */
    private String formaterVerdi(Object verdi) {
        if (verdi == null) return "null";
        if (verdi instanceof java.sql.Date d) {
            return d.toLocalDate().toString(); // kun dato
        }
        if (verdi instanceof java.time.LocalDate d) {
            return d.toString();
        }
        if (verdi instanceof java.sql.Timestamp ts) {
            // Hvis klokkeslettet er midnatt → vis kun datoen
            if (ts.toLocalDateTime().toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
                    && ts.toLocalDateTime().getNano() == 0) {
                return ts.toLocalDateTime().toLocalDate().toString();
            }
            return ts.toLocalDateTime().toString().replace("T", " ");
        }
        if (verdi instanceof java.time.LocalDateTime ldt) {
            if (ldt.toLocalTime().equals(java.time.LocalTime.MIDNIGHT) && ldt.getNano() == 0) {
                return ldt.toLocalDate().toString();
            }
            return ldt.toString().replace("T", " ");
        }
        return String.valueOf(verdi);
    }

    /**
     * Lister databaser/skjemaer brukeren har tilgang til i det valgte systemet.
     * Katalog-spørringene er per RDBMS og bruker PreparedStatement.
     */
    public List<String> getDatabases(DbConnection conn) throws SQLException {
        String rdbms = conn.getRdbms();
        String sql = switch (rdbms) {
            case "postgres" -> "SELECT datname FROM pg_database "
                    + "WHERE has_database_privilege(?, datname, 'CONNECT') AND NOT datistemplate "
                    + "ORDER BY datname";
            case "microsoft" -> "SELECT name FROM sys.databases WHERE HAS_DBACCESS(name) = 1 ORDER BY name";
            case "oracle" -> "SELECT owner FROM ("
                    + "  SELECT owner FROM all_tables "
                    + "  UNION SELECT owner FROM all_views"
                    + ") WHERE owner NOT IN ('SYS','SYSTEM','CTXSYS','DBSNMP','MDSYS','OLAPSYS',"
                    + "'ORDSYS','OUTLN','WMSYS','XDB','APPQOSSYS','AUDSYS','DVSYS','LBACSYS',"
                    + "'ORDDATA','ORDPLUGINS','SI_INFORMTN_SCHEMA','SYSBACKUP','SYSDG','SYSKM','SYSMAN',"
                    + "'GSMADMIN_INTERNAL','GSMUSER','GSMROOTUSER','REMOTE_SCHEDULER_AGENT','DBSFWUSER',"
                    + "'DBSNMP','WMSYS','ANONYMOUS','APEX_PUBLIC_USER','FLOWS_FILES') "
                    + "ORDER BY owner";
            case "mysql" -> "SHOW DATABASES";
            default -> throw new IllegalArgumentException("Ukjent RDBMS: " + rdbms);
        };

        try (Connection c = connect(conn);
             PreparedStatement ps = c.prepareStatement(sql)) {
            if ("postgres".equals(rdbms)) {
                ps.setString(1, conn.getUsername());
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
                    // PostgreSQL: vis kun databaser brukeren faktisk kan LESE i
                    // (has_database_privilege gir bare CONNECT-tilgang)
                    if ("postgres".equals(rdbms) && !harLesetilgang(conn, navn)) {
                        continue;
                    }
                    dbList.add(navn);
                }
                return dbList.stream().sorted().toList();
            }
        }
    }

    /**
     * Sjekker om brukeren kan lese tabeller/views i en PostgreSQL-database.
     * Kobler til databasen og spør information_schema — som kun viser
     * objekter den tilkoblede brukeren har tilgang til.
     */
    private boolean harLesetilgang(DbConnection conn, String database) {
        DbConnection kobling = kopiMedDatabase(conn, database);
        String sql = "SELECT EXISTS (SELECT 1 FROM information_schema.tables t "
                + "WHERE t.table_schema NOT IN ('pg_catalog','information_schema') LIMIT 1)";
        try (Connection c = connect(kobling);
             PreparedStatement ps = c.prepareStatement(sql)) {
            begrens(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        } catch (SQLException e) {
            logger.debug("Ingen lesetilgang til {}: {}", database, e.getMessage());
            return false;
        }
    }

    /**
     * Lister tabeller + views i valgt database/skjema.
     * Returnerer data (schema, navn, type) — ikke HTML (XSS-sikkert).
     */
    public List<List<String>> getTables(DbConnection conn, String db) throws SQLException {
        String rdbms = conn.getRdbms();
        String sql = switch (rdbms) {
            case "postgres" -> "SELECT table_schema, table_name, table_type FROM information_schema.tables "
                    + "WHERE table_schema NOT IN ('pg_catalog','information_schema') "
                    + "UNION ALL "
                    + // Tomme skjemaer (uten tabeller) vises også i treet
                    "SELECT schema_name, NULL, 'SCHEMA' FROM information_schema.schemata "
                    + "WHERE schema_name NOT IN ('pg_catalog','information_schema') "
                    + "ORDER BY 1, 3, 2";
            case "microsoft" -> "SELECT TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE FROM INFORMATION_SCHEMA.TABLES "
                    + "WHERE TABLE_CATALOG = ? ORDER BY TABLE_TYPE";
            case "oracle" -> "SELECT owner, table_name, 'TABLE' FROM all_tables WHERE owner = ? "
                    + "UNION SELECT owner, view_name, 'VIEW' FROM all_views WHERE owner = ? "
                    + "ORDER BY 1, 2";
            case "mysql" -> "SELECT table_schema, table_name, table_type FROM information_schema.tables "
                    + "WHERE table_schema = ? ORDER BY table_type";
            default -> throw new IllegalArgumentException("Ukjent RDBMS: " + rdbms);
        };

        // PostgreSQL/MSSQL: databasen ligger i URL-en — koble til den valgte
        // databasen (ellers hentes tabellene fra config-standarddatabasen,
        // f.eks. hr, og TABLE_CATALOG-filtreringen gir ingen rader for
        // databaser brukeren kun har lesetilgang til). MySQL/Oracle trenger
        // det ikke — information_schema/all_tables er server-globale.
        DbConnection kobling = ("postgres".equals(rdbms) || "microsoft".equals(rdbms))
                ? kopiMedDatabase(conn, db) : conn;
        try (Connection c = connect(kobling);
             PreparedStatement ps = c.prepareStatement(sql)) {
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

    /**
     * Lister kolonner per tabell i valgt database/skjema — brukes til
     * intellisense i SQL-editoren. Returnerer Map&lt;tabell, kolonner&gt;.
     */
    /**
     * Lister kolonner per tabell i valgt database/skjema.
     * Returnerer Map&lt;tabell, liste av [kolonnenavn, datatype]&gt; —
     * brukes til intellisense (navn) og tre-utvidelse (navn + type).
     */
    public java.util.Map<String, List<List<String>>> getColumns(DbConnection conn, String db) throws SQLException {
        String rdbms = conn.getRdbms();
        String sql = switch (rdbms) {
            case "postgres" -> "SELECT table_name, column_name, data_type FROM information_schema.columns "
                    + "WHERE table_schema NOT IN ('pg_catalog','information_schema') "
                    + "ORDER BY table_name, ordinal_position";
            case "microsoft" -> "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE TABLE_CATALOG = ? ORDER BY TABLE_NAME, ORDINAL_POSITION";
            case "oracle" -> "SELECT table_name, column_name, data_type FROM all_tab_columns "
                    + "WHERE owner = ? ORDER BY table_name, column_id";
            case "mysql" -> "SELECT table_name, column_name, data_type FROM information_schema.columns "
                    + "WHERE table_schema = ? ORDER BY table_name, ordinal_position";
            default -> throw new IllegalArgumentException("Ukjent RDBMS: " + rdbms);
        };

        // PostgreSQL/MSSQL: databasen ligger i URL-en — koble til den valgte
        // databasen (ellers viser INFORMATION_SCHEMA.COLUMNS kun kolonnene i
        // config-standarddatabasen). MySQL/Oracle er server-globale.
        DbConnection kobling = ("postgres".equals(rdbms) || "microsoft".equals(rdbms))
                ? kopiMedDatabase(conn, db) : conn;
        try (Connection c = connect(kobling);
             PreparedStatement ps = c.prepareStatement(sql)) {
            if ("microsoft".equals(rdbms) || "oracle".equals(rdbms) || "mysql".equals(rdbms)) {
                ps.setString(1, db);
            }
            begrens(ps);
            java.util.Map<String, List<List<String>>> kolonner = new java.util.LinkedHashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tabell = rs.getString(1);
                    String kolonne = rs.getString(2);
                    String type = rs.getString(3);
                    kolonner.computeIfAbsent(tabell, k -> new ArrayList<>())
                            .add(List.of(kolonne, type == null ? "" : type));
                }
                return kolonner;
            }
        }
    }
}
