package no.itfakultetet.dbdemo.controller;

import no.itfakultetet.dbdemo.model.Dao;
import no.itfakultetet.dbdemo.model.DbConnection;
import no.itfakultetet.dbdemo.model.SQLiteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;

@Controller
public class Select {

    private static final Logger logger = LoggerFactory.getLogger(Select.class);

    private final Dao dao;
    private final ConnectionHelper connectionHelper;
    private final SQLiteService sqliteService;

    public Select(Dao dao, ConnectionHelper connectionHelper, SQLiteService sqliteService) {
        this.dao = dao;
        this.connectionHelper = connectionHelper;
        this.sqliteService = sqliteService;
    }

    @GetMapping(value = "/select/{rdbms_sti}")
    public String hentSql(Model model, @PathVariable("rdbms_sti") String rdbms_sti,
                          @RequestParam(value = "db", required = false) String db,
                          @CookieValue(value = "skin", defaultValue = "agate") String skin,
                          Authentication authentication) {
        String bruker = authentication == null ? "anonym" : authentication.getName();
        if ("sqlite".equals(rdbms_sti)) {
            // SQLite er per-bruker-filer (ingen tilkobling i config):
            // velg første database hvis ingen er angitt, ellers åpne SQL-editoren
            if (db == null || db.isBlank()) {
                List<String> databaser = sqliteService.listDatabaser(bruker);
                if (!databaser.isEmpty()) {
                    db = databaser.get(0);
                }
            }
            if (db != null && !db.isBlank()) {
                model.addAttribute("db", db);
                model.addAttribute("rdbms", "SQLite");
                model.addAttribute("rdbms_sti", "sqlite");
                model.addAttribute("skin", skin);
                model.addAttribute("selectSide", true);
                return "select";
            }
            // Ingen databaser ennå → «Mine databaser»-siden for å opprette
            return "redirect:/sqlite";
        }
        try {
            DbConnection conn = connectionHelper.hentEllerFeil(rdbms_sti, bruker);
            // Forhåndsvelg databasen fra tilkoblingsskjemaet hvis ingen er valgt
            if (db == null || db.isBlank()) {
                db = conn.getDatabase();
            }
        } catch (IllegalArgumentException e) {
            model.addAttribute("feil", e.getMessage());
        }
        model.addAttribute("rdbms", ConnectionHelper.rdbmsNavn(rdbms_sti));
        model.addAttribute("rdbms_sti", rdbms_sti);
        model.addAttribute("db", db);
        model.addAttribute("skin", skin);
        model.addAttribute("selectSide", true);
        return "select";
    }

    @PostMapping(value = "/select/{rdbms_sti}")
    public String hentData(Model model,
           @PathVariable("rdbms_sti") String rdbms_sti,
           @RequestParam(value = "db") String db,
           @RequestParam(value = "query") String query,
           Authentication authentication) {

        model.addAttribute("rdbms", ConnectionHelper.rdbmsNavn(rdbms_sti));
        model.addAttribute("rdbms_sti", rdbms_sti);
        model.addAttribute("query", query);
        model.addAttribute("db", db);

        try {
            String bruker = authentication == null ? "anonym" : authentication.getName();
            DbConnection conn = connectionHelper.hentEllerFeil(rdbms_sti, bruker);
            Dao.QueryResult resultat = dao.executeQuery(conn, db, query);
            model.addAttribute("tableHeader", resultat.header());
            model.addAttribute("tableContent", resultat.rows());
            return "resultat";
        } catch (SQLException e) {
            logger.error("SQL-feil mot {} (db={}): {}", rdbms_sti, db, e.getMessage());
            model.addAttribute("feil", e.getMessage());
            return "select";
        } catch (IllegalArgumentException e) {
            logger.error("Ugyldig RDBMS: {}", rdbms_sti);
            model.addAttribute("feil", e.getMessage());
            return "select";
        }
    }
    
    @PostMapping(value = "/select/edit")
    public String redigerData(Model model,
              @RequestParam(value = "db") String db,
              @RequestParam(value = "query") String query,
              @RequestParam(value = "rdbms") String rdbms,
              @RequestParam(value = "rdbms_sti") String rdbms_sti) {

        model.addAttribute("query", query);
        model.addAttribute("db", db);
        model.addAttribute("rdbms", rdbms);
        model.addAttribute("rdbms_sti", rdbms_sti);

        return "select";
    }

}
