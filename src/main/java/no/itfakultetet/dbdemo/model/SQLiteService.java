package no.itfakultetet.dbdemo.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * SQLite-støtte: hver bruker kan opprette egne SQLite-databaser (filer)
 * med valgfritt navn, og kjøre SQL mot dem.
 * <p>
 * Filene ligger i {@code <config-mappe>/sqlite/<brukernavn>/<navn>.db} —
 * per bruker, utenfor repoet. Navn og brukernavn valideres strengt for å
 * hindre path-traversal.
 */
@Service
public class SQLiteService {

    private static final Logger logger = LoggerFactory.getLogger(SQLiteService.class);

    /** Tillatte tegn i databasenavn og brukernavn (hindrer path-traversal). */
    private static final Pattern GYLDIG_NAVN = Pattern.compile("^[a-zA-Z0-9æøåÆØÅ_-]{1,60}$");

    @Value("${db.query.timeout.seconds:15}")
    private int queryTimeoutSeconds;

    @Value("${db.query.max.rows:10000}")
    private int maxRows;

    /** Resultat av en SQL-spørring: kolonneoverskrifter + rader. */
    public record QueryResult(List<String> header, List<List<String>> rows) {
    }

    /** Rot-mappe for SQLite-filer: <config-mappe>/sqlite */
    private Path sqliteRot() {
        String env = System.getenv("DBDEMO_CONFIG");
        Path config = (env != null && !env.isBlank())
                ? Paths.get(env)
                : Paths.get(System.getProperty("user.home"), ".dbdemo", "dbconfig.json");
        return config.getParent().resolve("sqlite");
    }

    /** Mappen til en bestemt bruker sine SQLite-databaser. */
    private Path brukerMappe(String brukernavn) {
        return sqliteRot().resolve(brukernavn);
    }

    /** Validerer brukernavn/navn — kaster IllegalArgumentException hvis ugyldig. */
    private void validerNavn(String verdi, String hva) {
        if (verdi == null || !GYLDIG_NAVN.matcher(verdi).matches()) {
            throw new IllegalArgumentException("Ugyldig " + hva + ": «" + verdi + "» — kun bokstaver, tall, bindestrek og understrek er tillatt.");
        }
    }

    /** Full sti til en databasefil (validerer begge ledd). */
    private Path databaseFil(String brukernavn, String navn) {
        validerNavn(brukernavn, "brukernavn");
        validerNavn(navn, "databasenavn");
        return brukerMappe(brukernavn).resolve(navn + ".db");
    }

    /** Lister brukerens SQLite-databaser (filnavn uten .db, sortert). */
    public List<String> listDatabaser(String brukernavn) {
        validerNavn(brukernavn, "brukernavn");
        Path mappe = brukerMappe(brukernavn);
        if (!Files.isDirectory(mappe)) {
            return new ArrayList<>();
        }
        List<String> navn = new ArrayList<>();
        try (var stream = Files.list(mappe)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".db"))
                    .forEach(p -> navn.add(p.getFileName().toString().replaceAll("\\.db$", "")));
        } catch (Exception e) {
            logger.error("Kunne ikke liste SQLite-databaser for {}: {}", brukernavn, e.getMessage());
        }
        return navn.stream().sorted().toList();
    }

    /** Oppretter en ny SQLite-database (tom fil) for brukeren. */
    public void opprettDatabase(String brukernavn, String navn) {
        Path fil = databaseFil(brukernavn, navn);
        try {
            Files.createDirectories(fil.getParent());
            // SQLite oppretter filen ved første tilkobling
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + fil)) {
                c.createStatement().execute("SELECT 1");
            }
            logger.info("SQLite-database opprettet: {} for {}", navn, brukernavn);
        } catch (SQLException e) {
            logger.error("Kunne ikke opprette SQLite-database {}: {}", navn, e.getMessage());
            throw new IllegalArgumentException("Kunne ikke opprette databasen: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Kunne ikke opprette SQLite-database {}: {}", navn, e.getMessage());
            throw new IllegalArgumentException("Kunne ikke opprette databasen: " + e.getMessage());
        }
    }

    /** Åpner tilkobling til en eksisterende database (validerer navn + at filen finnes). */
    private Connection koble(String brukernavn, String navn) throws SQLException {
        Path fil = databaseFil(brukernavn, navn);
        if (!Files.exists(fil)) {
            throw new SQLException("Databasen finnes ikke: " + navn);
        }
        return DriverManager.getConnection("jdbc:sqlite:" + fil);
    }

    /** Kjører en vilkårlig SQL-spørring mot brukerens database. */
    public QueryResult kjørSQL(String brukernavn, String navn, String query) throws SQLException {
        try (Connection c = koble(brukernavn, navn);
             Statement st = c.createStatement()) {
            st.setQueryTimeout(queryTimeoutSeconds);
            st.setMaxRows(maxRows);
            long start = System.currentTimeMillis();

            boolean erResultat = st.execute(query);
            if (!erResultat) {
                // INSERT/UPDATE/CREATE osv.
                int endret = st.getUpdateCount();
                long elapsed = System.currentTimeMillis() - start;
                logger.info("SQLite {}: {} rader endret på {} ms", navn, endret, elapsed);
                return new QueryResult(List.of("Antall rader"), List.of(List.of(String.valueOf(Math.max(endret, 0)))));
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
                        rad.add(rs.getString(i));
                    }
                    rader.add(rad);
                }
                long elapsed = System.currentTimeMillis() - start;
                logger.info("SQLite {}: {} rader på {} ms", navn, rader.size(), elapsed);
                return new QueryResult(header, rader);
            }
        }
    }

    /** Lister tabeller i brukerens database (XSS-sikkert: data, ikke HTML). */
    public List<List<String>> getTables(String brukernavn, String navn) throws SQLException {
        try (Connection c = koble(brukernavn, navn);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT name, type FROM sqlite_master WHERE type IN ('table','view') "
                             + "AND name NOT LIKE 'sqlite_%' ORDER BY type, name")) {
            List<List<String>> tabeller = new ArrayList<>();
            while (rs.next()) {
                List<String> rad = new ArrayList<>(3);
                rad.add(""); // skjema (SQLite har ikke skjemaer)
                rad.add(rs.getString(1));
                rad.add("table".equalsIgnoreCase(rs.getString(2)) ? "TABLE" : "VIEW");
                tabeller.add(rad);
            }
            return tabeller;
        }
    }

    /** Lister kolonner per tabell — brukes til intellisense og tre-utvidelse.
     *  Returnerer Map&lt;tabell, liste av [kolonnenavn, datatype]&gt;. */
    public java.util.Map<String, List<List<String>>> getColumns(String brukernavn, String navn) throws SQLException {
        java.util.Map<String, List<List<String>>> kolonner = new java.util.LinkedHashMap<>();
        try (Connection c = koble(brukernavn, navn);
             Statement st = c.createStatement();
             ResultSet tabeller = st.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
            List<String> tabellNavn = new ArrayList<>();
            while (tabeller.next()) {
                tabellNavn.add(tabeller.getString(1));
            }
            for (String tabell : tabellNavn) {
                try (PreparedStatement ps = c.prepareStatement("PRAGMA table_info(\"" + tabell + "\")");
                     ResultSet rs = ps.executeQuery()) {
                    List<List<String>> cols = new ArrayList<>();
                    while (rs.next()) {
                        cols.add(List.of(rs.getString(2), rs.getString(3))); // navn, type
                    }
                    kolonner.put(tabell, cols);
                }
            }
        }
        return kolonner;
    }
}
