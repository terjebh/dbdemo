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
    public record QueryResult(List<String> header, List<List<String>> rows, long tidMs) {
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

    /** Full sti til en databasefil for nedlasting (validerer, ingen .db-suffix i navnet). */
    public Path databaseFilUtenSuffix(String brukernavn, String navn) {
        return databaseFil(brukernavn, navn);
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

    /** Sletter brukerens SQLite-database (kun egne filer — path-traversal-blokkert). */
    public void slettDatabase(String brukernavn, String navn) {
        Path fil = databaseFil(brukernavn, navn);
        if (!Files.exists(fil)) {
            throw new IllegalArgumentException("Databasen finnes ikke: " + navn);
        }
        try {
            Files.delete(fil);
            logger.info("SQLite-database slettet: {} for {}", navn, brukernavn);
        } catch (Exception e) {
            logger.error("Kunne ikke slette SQLite-database {}: {}", navn, e.getMessage());
            throw new IllegalArgumentException("Kunne ikke slette databasen: " + e.getMessage());
        }
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
                return new QueryResult(List.of("Antall rader"), List.of(List.of(String.valueOf(Math.max(endret, 0)))), elapsed);
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
                return new QueryResult(header, rader, elapsed);
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

    /** Lister indekser per tabell — Map&lt;tabell, liste av [navn, unik, kolonner]&gt;. */
    public java.util.Map<String, List<List<String>>> getIndexes(String brukernavn, String navn) throws SQLException {
        java.util.Map<String, List<List<String>>> indekser = new java.util.LinkedHashMap<>();
        try (Connection c = koble(brukernavn, navn);
             Statement st = c.createStatement();
             ResultSet tabeller = st.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
            List<String> tabellNavn = new ArrayList<>();
            while (tabeller.next()) {
                tabellNavn.add(tabeller.getString(1));
            }
            for (String tabell : tabellNavn) {
                List<List<String>> forTabell = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement("PRAGMA index_list(\"" + tabell + "\")");
                     ResultSet rs = ps.executeQuery()) {
                    List<String[]> liste = new ArrayList<>(); // [navn, unik]
                    while (rs.next()) {
                        // Kolonner: seq(1), name(2), unique(3), origin(4), partial(5)
                        String indeksNavn = rs.getString(2);
                        boolean unik = rs.getInt(3) == 1;
                        liste.add(new String[]{indeksNavn, unik ? "UNIK" : ""});
                    }
                    for (String[] ix : liste) {
                        StringBuilder kolonner = new StringBuilder();
                        try (PreparedStatement ps2 = c.prepareStatement("PRAGMA index_info(\"" + ix[0] + "\")");
                             ResultSet rs2 = ps2.executeQuery()) {
                            while (rs2.next()) {
                                if (kolonner.length() > 0) kolonner.append(", ");
                                kolonner.append(rs2.getString(3)); // name
                            }
                        }
                        forTabell.add(List.of(ix[0], ix[1], kolonner.toString()));
                    }
                }
                if (!forTabell.isEmpty()) indekser.put(tabell, forTabell);
            }
        }
        return indekser;
    }

    /** Primær-/fremmednøkler per kolonne — Map&lt;tabell, Map&lt;kolonne, "PK"|"FK"&gt;&gt;. */
    public java.util.Map<String, java.util.Map<String, String>> getKeys(String brukernavn, String navn)
            throws SQLException {
        java.util.Map<String, java.util.Map<String, String>> nokler = new java.util.LinkedHashMap<>();
        try (Connection c = koble(brukernavn, navn);
             Statement st = c.createStatement();
             ResultSet tabeller = st.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
            List<String> tabellNavn = new ArrayList<>();
            while (tabeller.next()) {
                tabellNavn.add(tabeller.getString(1));
            }
            for (String tabell : tabellNavn) {
                java.util.Map<String, String> forTabell = new java.util.LinkedHashMap<>();
                // Primærnøkkel: PRAGMA table_info gir pk > 0 for PK-kolonner
                try (PreparedStatement ps = c.prepareStatement("PRAGMA table_info(\"" + tabell + "\")");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        // Kolonner: cid(1), name(2), type(3), notnull(4), dflt(5), pk(6)
                        if (rs.getInt(6) > 0) {
                            forTabell.put(rs.getString(2), "PK");
                        }
                    }
                }
                // Fremmednøkler: PRAGMA foreign_key_list
                try (PreparedStatement ps = c.prepareStatement("PRAGMA foreign_key_list(\"" + tabell + "\")");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        // Kolonner: id(1), seq(2), table(3), from(4), to(5), ...
                        String fra = rs.getString(4);
                        if (fra != null && !forTabell.containsKey(fra)) {
                            forTabell.put(fra, "FK");
                        }
                    }
                }
                if (!forTabell.isEmpty()) nokler.put(tabell, forTabell);
            }
        }
        return nokler;
    }
}
