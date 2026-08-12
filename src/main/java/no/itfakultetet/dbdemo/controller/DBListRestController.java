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
public class DBListRestController {
    private static final Logger logger = LoggerFactory.getLogger(DBListRestController.class);

    private final Dao dao;
    private final ConnectionHelper connectionHelper;
    private final SQLiteService sqliteService;

    public DBListRestController(Dao dao, ConnectionHelper connectionHelper, SQLiteService sqliteService) {
        this.dao = dao;
        this.connectionHelper = connectionHelper;
        this.sqliteService = sqliteService;
    }

    @GetMapping(value = "/rest/get/dblist/{rdbms}")
    public ResponseEntity<?> hentDBListe(@PathVariable("rdbms") String rdbms_sti,
                                         Authentication authentication) {
        // SQLite: returner brukerens egne databaser
        if ("sqlite".equals(rdbms_sti)) {
            String bruker = authentication == null ? "anonym" : authentication.getName();
            return ResponseEntity.ok(sqliteService.listDatabaser(bruker));
        }
        try {
            String bruker = authentication == null ? "anonym" : authentication.getName();
            DbConnection conn = connectionHelper.hentEllerFeil(rdbms_sti, bruker);
            List<String> dbListe = dao.getDatabases(conn);
            return ResponseEntity.ok(dbListe);
        } catch (SQLException e) {
            logger.error("Kunne ikke hente databaseliste fra {}: {}", rdbms_sti, e.getMessage());
            return ResponseEntity.internalServerError().body("Kunne ikke hente databaseliste: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
