package no.itfakultetet.dbdemo.controller;

import no.itfakultetet.dbdemo.model.SQLFilService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST-endepunkter for brukernes SQL-filer (lagrings-ikonet over
 * resultat-tabellen): hver bruker har sin egen mappe, og kan lagre,
 * åpne, liste og slette sine egne filer.
 */
@RestController
@RequestMapping("/rest/sqlfil")
public class SQLFilRestController {

    private static final Logger logger = LoggerFactory.getLogger(SQLFilRestController.class);

    private final SQLFilService sqlFilService;

    public SQLFilRestController(SQLFilService sqlFilService) {
        this.sqlFilService = sqlFilService;
    }

    private String bruker(Authentication authentication) {
        return authentication == null ? "anonym" : authentication.getName();
    }

    /** GET /rest/sqlfil — liste over brukerens SQL-filer. */
    @GetMapping
    public ResponseEntity<?> liste(Authentication authentication) {
        try {
            List<String> filer = sqlFilService.listFiler(bruker(authentication));
            return ResponseEntity.ok(filer);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("feil", e.getMessage()));
        }
    }

    /** POST /rest/sqlfil — lagrer (eller overskriver) en SQL-fil. Body: {filnavn, innhold}. */
    @PostMapping
    public ResponseEntity<?> lagre(@RequestBody Map<String, String> body, Authentication authentication) {
        String filnavn = body.get("filnavn");
        String innhold = body.getOrDefault("innhold", "");
        if (filnavn == null || filnavn.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("feil", "Filnavn mangler"));
        }
        try {
            sqlFilService.lagreFil(bruker(authentication), filnavn.trim(), innhold);
            return ResponseEntity.ok(Map.of("ok", true, "filnavn", filnavn.trim()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("feil", e.getMessage()));
        }
    }

    /** GET /rest/sqlfil/{filnavn} — innholdet i en SQL-fil. */
    @GetMapping("/{filnavn}")
    public ResponseEntity<?> les(@PathVariable("filnavn") String filnavn, Authentication authentication) {
        try {
            String innhold = sqlFilService.lesFil(bruker(authentication), filnavn);
            if (innhold == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(Map.of("filnavn", filnavn, "innhold", innhold));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("feil", e.getMessage()));
        }
    }

    /** DELETE /rest/sqlfil/{filnavn} — sletter en SQL-fil. */
    @DeleteMapping("/{filnavn}")
    public ResponseEntity<?> slett(@PathVariable("filnavn") String filnavn, Authentication authentication) {
        try {
            sqlFilService.slettFil(bruker(authentication), filnavn);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("feil", e.getMessage()));
        }
    }
}
