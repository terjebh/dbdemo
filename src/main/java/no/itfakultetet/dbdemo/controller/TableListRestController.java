package no.itfakultetet.dbdemo.controller;

import no.itfakultetet.dbdemo.model.Dao;
import no.itfakultetet.dbdemo.model.DbConnection;
import no.itfakultetet.dbdemo.model.SQLiteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.List;

@RestController
public class TableListRestController {

    private static final Logger logger = LoggerFactory.getLogger(TableListRestController.class);

    private final Dao dao;
    private final ConnectionHelper connectionHelper;
    private final SQLiteService sqliteService;

    public TableListRestController(Dao dao, ConnectionHelper connectionHelper, SQLiteService sqliteService) {
        this.dao = dao;
        this.connectionHelper = connectionHelper;
        this.sqliteService = sqliteService;
    }

    @GetMapping(value = "/rest/get/tablelist/{rdbms_sti}/{db}")
    public ResponseEntity<?> hentTabeller(@PathVariable("rdbms_sti") String rdbms_sti,
                                          @PathVariable("db") String database,
                                          Authentication authentication) {
        // SQLite: tabeller i brukerens database
        if ("sqlite".equals(rdbms_sti)) {
            String bruker = authentication == null ? "anonym" : authentication.getName();
            try {
                return ResponseEntity.ok(sqliteService.getTables(bruker, database));
            } catch (SQLException e) {
                return ResponseEntity.internalServerError().body("Kunne ikke hente tabelliste: " + e.getMessage());
            }
        }
        try {
            String bruker = authentication == null ? "anonym" : authentication.getName();
            DbConnection conn = connectionHelper.hentEllerFeil(rdbms_sti, bruker);
            if (database == null || database.isBlank() || "Velg Database".equals(database)) {
                return ResponseEntity.ok(List.of());
            }
            List<List<String>> tabeller = dao.getTables(conn, database);
            return ResponseEntity.ok(tabeller);
        } catch (SQLException e) {
            logger.error("Kunne ikke hente tabelliste fra {} ({}): {}", rdbms_sti, database, e.getMessage());
            return ResponseEntity.internalServerError().body("Kunne ikke hente tabelliste: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Kolonner per tabell — brukes til intellisense i SQL-editoren. */
    @GetMapping(value = "/rest/get/columns/{rdbms_sti}/{db}")
    public ResponseEntity<?> hentKolonner(@PathVariable("rdbms_sti") String rdbms_sti,
                                          @PathVariable("db") String database,
                                          Authentication authentication) {
        // SQLite: kolonner i brukerens database
        if ("sqlite".equals(rdbms_sti)) {
            String bruker = authentication == null ? "anonym" : authentication.getName();
            try {
                return ResponseEntity.ok(sqliteService.getColumns(bruker, database));
            } catch (SQLException e) {
                return ResponseEntity.internalServerError().body("Kunne ikke hente kolonneliste: " + e.getMessage());
            }
        }
        try {
            String bruker = authentication == null ? "anonym" : authentication.getName();
            DbConnection conn = connectionHelper.hentEllerFeil(rdbms_sti, bruker);
            if (database == null || database.isBlank() || "Velg Database".equals(database)) {
                return ResponseEntity.ok(java.util.Map.of());
            }
            java.util.Map<String, List<List<String>>> kolonner = dao.getColumns(conn, database);
            return ResponseEntity.ok(kolonner);
        } catch (SQLException e) {
            logger.error("Kunne ikke hente kolonneliste fra {} ({}): {}", rdbms_sti, database, e.getMessage());
            return ResponseEntity.internalServerError().body("Kunne ikke hente kolonneliste: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
