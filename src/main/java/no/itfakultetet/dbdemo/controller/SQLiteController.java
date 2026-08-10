package no.itfakultetet.dbdemo.controller;

import no.itfakultetet.dbdemo.model.SQLiteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;

/**
 * SQLite-funksjonalitet: alle innloggede brukere kan opprette egne
 * SQLite-databaser med valgfritt navn, liste dem og kjøre SQL mot dem.
 * <p>
 * Sider:
 * <ul>
 *   <li>GET /sqlite — liste over brukerens databaser + opprett-skjema</li>
 *   <li>POST /sqlite/opprett — oppretter ny database</li>
 *   <li>GET /sqlite/{navn} — SQL-editor for valgt database</li>
 *   <li>POST /sqlite/{navn} — kjører SQL</li>
 * </ul>
 */
@Controller
@RequestMapping("/sqlite")
public class SQLiteController {

    private static final Logger logger = LoggerFactory.getLogger(SQLiteController.class);

    private final SQLiteService sqliteService;

    public SQLiteController(SQLiteService sqliteService) {
        this.sqliteService = sqliteService;
    }

    private String brukernavn(Authentication authentication) {
        return authentication == null ? "anonym" : authentication.getName();
    }

    @GetMapping
    public String liste(Model model, Authentication authentication) {
        String bruker = brukernavn(authentication);
        model.addAttribute("bruker", bruker);
        model.addAttribute("databaser", sqliteService.listDatabaser(bruker));
        return "sqlite";
    }

    @PostMapping("/opprett")
    public String opprett(Model model, Authentication authentication,
                          @RequestParam("navn") String navn) {
        String bruker = brukernavn(authentication);
        try {
            sqliteService.opprettDatabase(bruker, navn.trim());
            return "redirect:/sqlite";
        } catch (IllegalArgumentException e) {
            model.addAttribute("feil", e.getMessage());
            model.addAttribute("bruker", bruker);
            model.addAttribute("databaser", sqliteService.listDatabaser(bruker));
            return "sqlite";
        }
    }

    @GetMapping("/{navn}")
    public String editor(Model model, Authentication authentication, @PathVariable("navn") String navn) {
        model.addAttribute("rdbms", "SQLite");
        model.addAttribute("rdbms_sti", "sqlite");
        model.addAttribute("db", navn);
        model.addAttribute("selectSide", true);
        return "select";
    }

    @PostMapping("/{navn}")
    public String kjør(Model model, Authentication authentication,
                       @PathVariable("navn") String navn,
                       @RequestParam("query") String query) {
        String bruker = brukernavn(authentication);
        model.addAttribute("rdbms", "SQLite");
        model.addAttribute("rdbms_sti", "sqlite");
        model.addAttribute("db", navn);
        model.addAttribute("query", query);

        try {
            SQLiteService.QueryResult resultat = sqliteService.kjørSQL(bruker, navn, query);
            model.addAttribute("tableHeader", resultat.header());
            model.addAttribute("tableContent", resultat.rows());
            return "resultat";
        } catch (SQLException e) {
            logger.error("SQLite-feil (db={}): {}", navn, e.getMessage());
            model.addAttribute("feil", e.getMessage());
            return "select";
        } catch (IllegalArgumentException e) {
            model.addAttribute("feil", e.getMessage());
            return "select";
        }
    }
}
