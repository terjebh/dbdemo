package no.itfakultetet.dbdemo.controller;

import no.itfakultetet.dbdemo.model.Dao;
import no.itfakultetet.dbdemo.model.DbConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;

@Controller
public class Select {

    private static final Logger logger = LoggerFactory.getLogger(Select.class);

    private final Dao dao;
    private final ConnectionHelper connectionHelper;

    public Select(Dao dao, ConnectionHelper connectionHelper) {
        this.dao = dao;
        this.connectionHelper = connectionHelper;
    }

    @GetMapping(value = "/select/{rdbms_sti}")
    public String hentSql(Model model, @PathVariable("rdbms_sti") String rdbms_sti,
                          @RequestParam(value = "db", required = false) String db,
                          @CookieValue(value = "skin", defaultValue = "agate") String skin) {
        try {
            DbConnection conn = connectionHelper.hentEllerFeil(rdbms_sti);
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
           @RequestParam(value = "query") String query) {

        model.addAttribute("rdbms", ConnectionHelper.rdbmsNavn(rdbms_sti));
        model.addAttribute("rdbms_sti", rdbms_sti);
        model.addAttribute("query", query);
        model.addAttribute("db", db);

        try {
            DbConnection conn = connectionHelper.hentEllerFeil(rdbms_sti);
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
