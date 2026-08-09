package no.itfakultetet.dbdemo.controller;

import no.itfakultetet.dbdemo.model.Dao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.List;

@RestController
public class DBListRestController {
    private static final Logger logger = LoggerFactory.getLogger(DBListRestController.class);

    @Autowired
    private Dao dao;

    @Value("${pg.username}")
    private String pgUsername;
    @Value("${pg.pwd}")
    private String pgPwd;
    @Value("${ms.username}")
    private String msUsername;
    @Value("${ms.pwd}")
    private String msPwd;
    @Value("${or.username}")
    private String orUsername;
    @Value("${or.pwd}")
    private String orPwd;
    @Value("${my.username}")
    private String myUsername;
    @Value("${my.pwd}")
    private String myPwd;

    private String[] finnBruker(String rdbms_sti) {
        return switch (rdbms_sti) {
            case "postgres" -> new String[]{pgUsername, pgPwd};
            case "microsoft" -> new String[]{msUsername, msPwd};
            case "oracle" -> new String[]{orUsername, orPwd};
            case "mysql" -> new String[]{myUsername, myPwd};
            default -> null;
        };
    }

    @GetMapping(value = "/rest/get/dblist/{rdbms}")
    public ResponseEntity<?> hentDBListe(@PathVariable("rdbms") String rdbms_sti) {
        String[] bruker = finnBruker(rdbms_sti);
        if (bruker == null) {
            return ResponseEntity.badRequest().body("Ukjent databasehåndteringssystem: " + rdbms_sti);
        }
        try {
            List<String> dbListe = dao.getDatabases(rdbms_sti, bruker[0], bruker[1]);
            return ResponseEntity.ok(dbListe);
        } catch (SQLException e) {
            logger.error("Kunne ikke hente databaseliste fra {}: {}", rdbms_sti, e.getMessage());
            return ResponseEntity.internalServerError().body("Kunne ikke hente databaseliste: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
