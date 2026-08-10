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
import java.util.Map;

/**
 * JSON-endepunkt for å kjøre SQL uten side-reload (pgAdmin4-stil).
 * <p>
 * POST /rest/kjor/{rdbms_sti} med body {"db": "...", "query": "..."}
 * returnerer {"header": [...], "rows": [[...]]} eller en feilmelding.
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

    @PostMapping("/rest/kjor/{rdbms_sti}")
    public ResponseEntity<?> kjor(@PathVariable("rdbms_sti") String rdbms_sti,
                                  @RequestBody KjorRequest request,
                                  Authentication authentication) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("feil", "SQL-spørringen er tom"));
        }
        String db = request.db() == null ? "" : request.db();
        String query = request.query().trim();

        // SQLite: kjør mot brukerens egen databasefil
        if ("sqlite".equals(rdbms_sti)) {
            String bruker = authentication == null ? "anonym" : authentication.getName();
            try {
                SQLiteService.QueryResult resultat = sqliteService.kjørSQL(bruker, db, query);
                return ResponseEntity.ok(Map.of(
                        "header", resultat.header(),
                        "rows", resultat.rows()));
            } catch (SQLException | IllegalArgumentException e) {
                logger.error("SQLite-feil (db={}): {}", db, e.getMessage());
                return ResponseEntity.badRequest().body(Map.of("feil", e.getMessage()));
            }
        }

        // Vanlige RDBMS-er: lese-tilkobling fra config
        try {
            DbConnection conn = connectionHelper.hentEllerFeil(rdbms_sti);
            Dao.QueryResult resultat = dao.executeQuery(conn, db, query);
            return ResponseEntity.ok(Map.of(
                    "header", resultat.header(),
                    "rows", resultat.rows()));
        } catch (SQLException e) {
            logger.error("SQL-feil mot {} (db={}): {}", rdbms_sti, db, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("feil", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("feil", e.getMessage()));
        }
    }
}
