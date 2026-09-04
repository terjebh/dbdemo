package no.itfakultetet.dbdemo.controller;

import no.itfakultetet.dbdemo.model.Dao;
import no.itfakultetet.dbdemo.model.DbConnection;
import no.itfakultetet.dbdemo.model.SQLiteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JSON-endepunkt for å kjøre SQL uten side-reload (pgAdmin4-stil).
 * <p>
 * POST /rest/kjor/{rdbms_sti} med body {"db": "...", "query": "..."}
 * returnerer {"header": [...], "rows": [[...]]} eller en feilmelding.
 * Tilkoblingsfeil flagges med {"feil": "...", "tilkobling": true} slik at
 * klienten kun viser «Oppdater tilkoblingsinformasjonen»-lenken for de
 * feilene som faktisk har med tilkobling å gjøre.
 */
@RestController
public class QueryRestController {

    private static final Logger logger = LoggerFactory.getLogger(QueryRestController.class);

    private final Dao dao;
    private final ConnectionHelper connectionHelper;
    private final SQLiteService sqliteService;

    public QueryRestController(Dao dao, ConnectionHelper connectionHelper, SQLiteService sqliteService) {
        this.dao = dao;
        this.connectionHelper = connectionHelper;
        this.sqliteService = sqliteService;
    }

    /** Forespørsel-body: valgt database + SQL (+ faneId for transaksjoner). */
    public record KjorRequest(String db, String query, Integer fane) {
        public KjorRequest {
            if (fane == null) fane = 1;
        }
    }

    // ===== Transaksjonsstøtte (per fane): BEGIN/COMMIT/ROLLBACK =====
    // Når brukeren kjører BEGIN/START TRANSACTION åpnes ÉN skrivbar tilkobling
    // for den fanen (autocommit=false) som holdes åpen i HttpSession til
    // COMMIT/ROLLBACK. Alle spørringer i mellomtiden går på den tilkoblingen,
    // så INSERT/UPDATE/DELETE + ROLLBACK kan demonstreres. Utenfor en åpen
    // transaksjon er appen read-only som før; delte databaser er beskyttet av
    // DB-rettighetene (kurs-brukerne har kun SELECT på hr/brreg).

    /** Session-attributt: faneId → åpen transaksjonstilkobling. */
    private static final String TX_ATTRIBUTT = "transaksjonTilkoblinger";

    /** Henter transaksjonskartet (faneId → Connection) fra sesjonen. */
    @SuppressWarnings("unchecked")
    private static Map<Integer, java.sql.Connection> txKart(jakarta.servlet.http.HttpSession session) {
        Map<Integer, java.sql.Connection> kart = (Map<Integer, java.sql.Connection>) session.getAttribute(TX_ATTRIBUTT);
        if (kart == null) {
            kart = new java.util.concurrent.ConcurrentHashMap<>();
            session.setAttribute(TX_ATTRIBUTT, kart);
        }
        return kart;
    }

    /**
     * Gjenkjenner transaksjonskommandoer. Returnerer "BEGIN", "COMMIT" eller
     * "ROLLBACK" hvis spørringen er en ren slik kommando, ellers null.
     * Støtter MySQL/PostgreSQL (BEGIN/START TRANSACTION), MSSQL
     * (BEGIN TRANSACTION/BEGIN TRAN) og Oracle (kun COMMIT/ROLLBACK —
     * transaksjoner er implisitte der, styres av autocommit=false).
     */
    static String transaksjonsKommando(String query, String rdbms) {
        if (query == null) return null;
        String q = query.trim();
        String enkelt = q.endsWith(";") ? q.substring(0, q.length() - 1).trim() : q;
        if (enkelt.contains(";")) return null; // kun én ren kommando
        String uq = enkelt.toUpperCase(Locale.ROOT);

        if (uq.equals("COMMIT") || uq.equals("COMMIT WORK")
                || uq.equals("COMMIT TRANSACTION") || uq.equals("COMMIT TRAN")) {
            return "COMMIT";
        }
        if (uq.equals("ROLLBACK") || uq.equals("ROLLBACK WORK")
                || uq.equals("ROLLBACK TRANSACTION") || uq.equals("ROLLBACK TRAN")) {
            return "ROLLBACK";
        }
        if ("oracle".equals(rdbms)) return null; // Oracle har ingen BEGIN
        // Kun RENE BEGIN-setninger: «BEGIN», «BEGIN TRANSACTION» (MSSQL),
        // «BEGIN TRAN» eller «START TRANSACTION» — aldri «BEGIN SELECT …»
        if (uq.equals("BEGIN") || uq.equals("BEGIN WORK")
                || uq.equals("BEGIN TRANSACTION") || uq.equals("BEGIN TRAN")
                || uq.equals("START TRANSACTION")) {
            return "BEGIN";
        }
        return null;
    }

    /**
     * Åpner en skrivbar transaksjonstilkobling (autocommit=false) for én fane.
     * Returnerer null hvis en transaksjon allerede er åpen for fanen.
     */
    private java.sql.Connection apneTransaksjon(DbConnection conn, String db,
                                                jakarta.servlet.http.HttpSession session, int faneId) throws SQLException {
        Map<Integer, java.sql.Connection> kart = txKart(session);
        java.sql.Connection eksisterende = kart.get(faneId);
        if (eksisterende != null && !eksisterende.isClosed()) {
            return null; // allerede åpen — ikke start en ny
        }
        // Oracle: «db» er skjema, ikke database — ikke bytt i URL-en.
        DbConnection kobling = conn;
        if (!"oracle".equals(conn.getRdbms())) {
            kobling = dao.kopiMedDatabasePublikk(conn, db);
        }
        java.sql.Connection c = dao.connect(kobling, true); // skrivbar!
        c.setAutoCommit(false);
        kart.put(faneId, c);
        return c;
    }

    /** Klassifiserer om en feilmelding har med tilkobling å gjøre. */
    static boolean erTilkoblingsfeil(String melding) {
        if (melding == null) return false;
        String m = melding.toLowerCase(Locale.ROOT);
        return m.contains("connection")           // generisk
                || m.contains("could not connect")
                || m.contains("cannot connect")
                || m.contains("refused")
                || m.contains("timed out")
                || m.contains("timeout")
                || m.contains("unreachable")
                || m.contains("no route to host")
                || m.contains("unknown host")
                || m.contains("connection reset")
                || m.contains("broken pipe")
                || m.contains("no suitable driver")
                || m.contains("ikke konfigurert")  // vår egen melding i ConnectionHelper
                || m.contains("login failed")
                || m.contains("access denied")
                || m.contains("ora-12541")         // Oracle: ingen lytter
                || m.contains("ora-12514")         // Oracle: ukjent tjeneste
                || m.contains("ora-12154")         // Oracle: TNS kunne ikke oversettes
                || m.contains("ora-01017")         // Oracle: feil brukernavn/passord
                || m.contains("connection failure")
                || m.contains("network error")
                || m.contains("communications link failure")
                || m.contains("link failure")
                || m.contains("communicating")
                || m.contains("fatal error");
    }

    /** Bygger feil-svar med tilkoblingsflagg. */
    private ResponseEntity<Map<String, Object>> feilSvar(String melding) {
        return ResponseEntity.badRequest().body(Map.of(
                "feil", melding == null ? "Ukjent feil" : melding,
                "tilkobling", erTilkoblingsfeil(melding)));
    }

    @PostMapping("/rest/kjor/{rdbms_sti}")
    public ResponseEntity<?> kjor(@PathVariable("rdbms_sti") String rdbms_sti,
                                  @RequestBody KjorRequest request,
                                  Authentication authentication,
                                  jakarta.servlet.http.HttpSession session) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("feil", "SQL-spørringen er tom"));
        }
        String db = request.db() == null ? "" : request.db();
        String query = request.query().trim();

        // psql-oppførsel: «SET search_path TO skjema» lagres i brukerens sesjon
        // og gjelder for alle påfølgende spørringer (til logout / ny SET).
        String searchPath = fangOppSearchPath(query);
        if (searchPath != null) {
            session.setAttribute("searchPath", searchPath);
            return ResponseEntity.ok(Map.of(
                    "header", List.of(),
                    "rows", List.of(),
                    "tidMs", 0L,
                    "melding", "Search_path satt til " + searchPath));
        }
        String sattSearchPath = (String) session.getAttribute("searchPath");

        // SQLite: kjør mot brukerens egen databasefil
        if ("sqlite".equals(rdbms_sti)) {
            String bruker = authentication == null ? "anonym" : authentication.getName();
            try {
                SQLiteService.QueryResult resultat = sqliteService.kjørSQL(bruker, db, query);
                return ResponseEntity.ok(Map.of(
                        "header", resultat.header(),
                        "rows", resultat.rows(),
                        "tidMs", resultat.tidMs()));
            } catch (SQLException | IllegalArgumentException e) {
                logger.error("SQLite-feil (db={}): {}", db, e.getMessage());
                return feilSvar(e.getMessage());
            }
        }

        // Vanlige RDBMS-er: tilkobling fra DEN INNLOGGEDE BRUKERENS config
        try {
            String bruker = authentication == null ? "anonym" : authentication.getName();
            DbConnection conn = connectionHelper.hentEllerFeil(rdbms_sti, bruker);

            // ===== Transaksjonsmodus: BEGIN/COMMIT/ROLLBACK =====
            // En åpen transaksjon per fane (faneId fra klienten). Mens den er
            // åpen kjører ALLE spørringer på transaksjonstilkoblingen (skrivbar,
            // autocommit=false), så INSERT/UPDATE/DELETE + ROLLBACK fungerer.
            int faneId = request.fane() == null ? 1 : request.fane();
            String txKommando = transaksjonsKommando(query, rdbms_sti);
            Map<Integer, java.sql.Connection> txKart = txKart(session);
            java.sql.Connection tx = txKart.get(faneId);
            if (tx != null && tx.isClosed()) {
                txKart.remove(faneId);
                tx = null;
            }

            if ("BEGIN".equals(txKommando)) {
                if (tx != null) {
                    return ResponseEntity.ok(Map.of("melding",
                            "En transaksjon er allerede åpen for denne fanen — avslutt med COMMIT eller ROLLBACK"));
                }
                java.sql.Connection c = apneTransaksjon(conn, db, session, faneId);
                if (c == null) {
                    return ResponseEntity.ok(Map.of("melding",
                            "En transaksjon er allerede åpen for denne fanen — avslutt med COMMIT eller ROLLBACK"));
                }
                // Transaksjonen startes av setAutoCommit(false) i apneTransaksjon
                // — vi kjører IKKE BEGIN som SQL (MSSQL krever «BEGIN TRANSACTION»,
                // MySQL/PG «BEGIN», Oracle har ingen BEGIN; autocommit=false
                // starter en implisitt transaksjon i alle fire).
                return ResponseEntity.ok(Map.of(
                        "header", List.of(),
                        "rows", List.of(),
                        "tidMs", 0L,
                        "transaksjon", true,
                        "melding", "Transaksjon startet — endringer er ikke lagret før COMMIT. Prøv: INSERT, SELECT, deretter ROLLBACK eller COMMIT."));
            }

            if (("COMMIT".equals(txKommando) || "ROLLBACK".equals(txKommando)) && tx == null) {
                return ResponseEntity.ok(Map.of("melding",
                        "Ingen åpen transaksjon i denne fanen — start med BEGIN (eller START TRANSACTION)"));
            }
            if (("COMMIT".equals(txKommando) || "ROLLBACK".equals(txKommando)) && tx != null) {
                txKart.remove(faneId);
                try (java.sql.Statement st = tx.createStatement()) {
                    st.execute(txKommando);
                } catch (SQLException e) {
                    tx.rollback(); // forsøk å rydde opp ved feil
                    throw e;
                } finally {
                    try { tx.close(); } catch (SQLException ignored) { }
                }
                boolean erCommit = "COMMIT".equals(txKommando);
                return ResponseEntity.ok(Map.of(
                        "header", List.of(),
                        "rows", List.of(),
                        "tidMs", 0L,
                        "transaksjon", false,
                        "melding", erCommit
                                ? "COMMIT utført — endringene er nå lagret permanent."
                                : "ROLLBACK utført — alle endringer i transaksjonen ble angret."));
            }

            // Åpen transaksjon → kjør spørringen på transaksjonstilkoblingen
            // (skrivbar). Session-kommandoer/SET replayes IKKE her — de kjørte
            // allerede på denne tilkoblingen da de ble satt.
            if (tx != null) {
                Dao.QueryResult resultat = dao.executeQueryPåTilkobling(tx, db, query);
                return ResponseEntity.ok(Map.of(
                        "header", resultat.header(),
                        "rows", resultat.rows(),
                        "tidMs", resultat.tidMs(),
                        "transaksjon", true));
            }

            // psql-oppførsel: andre session-kommandoer (SET lc_time_names,
            // SET TIME ZONE, ALTER SESSION SET NLS_…, SET LANGUAGE …) lagres
            // per RDBMS i brukerens sesjon og replayes på hver nye tilkobling,
            // siden hver spørring får en fersk tilkobling.
            String sessionKommando = fangOppSessionKommando(query, rdbms_sti);
            if (sessionKommando != null) {
                // Valider kommandoen mot databasen FØR vi lagrer den — replayes
                // den senere med feil, blokkerer den alle spørringer.
                dao.executeQuery(conn, db, sessionKommando, sattSearchPath, hentSessionKommandorer(session, rdbms_sti));
                List<String> lagrede = hentSessionKommandorer(session, rdbms_sti);
                lagrede.removeIf(k -> k.equalsIgnoreCase(sessionKommando));
                lagrede.add(sessionKommando);
                return ResponseEntity.ok(Map.of(
                        "header", List.of(),
                        "rows", List.of(),
                        "tidMs", 0L,
                        "melding", "Session-kommandoen ble lagret og gjelder for videre spørringer"));
            }

            Dao.QueryResult resultat = dao.executeQuery(conn, db, query, sattSearchPath,
                    hentSessionKommandorer(session, rdbms_sti));
            return ResponseEntity.ok(Map.of(
                    "header", resultat.header(),
                    "rows", resultat.rows(),
                    "tidMs", resultat.tidMs()));
        } catch (SQLException e) {
            logger.error("SQL-feil mot {} (db={}): {}", rdbms_sti, db, e.getMessage());
            return feilSvar(e.getMessage());
        } catch (IllegalArgumentException e) {
            return feilSvar(e.getMessage());
        }
    }

    /** Session-attributtnøkkel: lagrede session-kommandoer per RDBMS. */
    private static final String SESSION_KOMMANDOER = "sessionKommandorer:";

    /** Henter (og oppretter ved behov) listen over lagrede session-kommandoer for én RDBMS. */
    @SuppressWarnings("unchecked")
    private static List<String> hentSessionKommandorer(jakarta.servlet.http.HttpSession session, String rdbms) {
        String nokkel = SESSION_KOMMANDOER + rdbms;
        List<String> liste = (List<String>) session.getAttribute(nokkel);
        if (liste == null) {
            liste = new java.util.ArrayList<>();
            session.setAttribute(nokkel, liste);
        }
        return liste;
    }

    /**
     * Gjenkjenner rene session-kommandoer (SET / ALTER SESSION SET) som skal
     * gjelde for alle påfølgende spørringer — tilsvarende search_path-logikken,
     * men for alle RDBMS-ene. Returnerer kommandoen (uten avsluttende semikolon)
     * eller null hvis spørringen ikke er en slik kommando.
     * Ekskluderer kommandoer som ikke gir mening å replaye: transaksjonsstyring
     * (autocommit/transaction), rollebytte, @-variabler og Oracle CONTAINER.
     */
    static String fangOppSessionKommando(String query, String rdbms) {
        if (query == null || "sqlite".equals(rdbms)) return null;
        String q = query.trim();
        // Kun ÉN ren setning — ingen semikolon midt i (batch kjøres som vanlig)
        String enkelt = q.endsWith(";") ? q.substring(0, q.length() - 1).trim() : q;
        if (enkelt.contains(";")) return null;
        String uq = enkelt.toUpperCase(Locale.ROOT);

        boolean erSet = uq.equals("SET") || uq.startsWith("SET ");
        boolean erAlterSession = "oracle".equals(rdbms) && uq.startsWith("ALTER SESSION SET ");
        if (!erSet && !erAlterSession) return null;

        // Ekskluder kommandoer som ikke er trygge/nyttige å replaye
        if (uq.contains("AUTOCOMMIT")) return null;
        if (uq.contains("SESSION AUTHORIZATION")) return null;
        if (uq.startsWith("SET ROLE")) return null;
        if (uq.startsWith("SET @")) return null;                    // MySQL-bruker-variabler
        if (uq.contains("ALTER SESSION SET CONTAINER")) return null; // Oracle: bytter PDB
        // MSSQL: SET TRANSACTION ISOLATION LEVEL er en gyldig session-innstilling
        if ("microsoft".equals(rdbms) && uq.startsWith("SET TRANSACTION ISOLATION")) return enkelt;
        // MySQL/PG: SET TRANSACTION / SET SESSION TRANSACTION er transaksjonsstyring
        if (uq.startsWith("SET SESSION TRANSACTION")) return null;
        if (uq.startsWith("SET TRANSACTION")) return null;
        return enkelt;
    }

    /**
     * Gjenkjenner «SET search_path TO skjema» (psql-syntaks). Returnerer
     * skjemanavnet, eller null hvis spørringen ikke er en slik setning.
     * Støtter både «TO» og «=», og «DEFAULT» (tilbakestiller).
     */
    static String fangOppSearchPath(String query) {
        if (query == null) return null;
        // Enkelt-setning: settning må starte med SET search_path
        String uq = query.trim().toUpperCase(Locale.ROOT);
        if (!uq.startsWith("SET ") || !uq.contains("SEARCH_PATH")) return null;
        // Kun rene SET-setninger — ikke midt i annen SQL
        if (query.contains(";") && !query.trim().endsWith(";")) return null;
        var m = java.util.regex.Pattern.compile(
                "^SET\\s+SEARCH_PATH\\s+(?:TO|=)\\s*([A-Za-z0-9_\\\"\\.\\-$]+)\\s*;?$",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(query.trim());
        if (!m.matches()) return null;
        String verdi = m.group(1).trim();
        if (verdi.equalsIgnoreCase("DEFAULT")) {
            return ""; // tom = tilbakestill
        }
        return verdi.replace("\"", "");
    }
}
