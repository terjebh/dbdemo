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

    /** Forespørsel-body: valgt database + SQL. */
    public record KjorRequest(String db, String query) {
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
            Dao.QueryResult resultat = dao.executeQuery(conn, db, query, sattSearchPath);
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
