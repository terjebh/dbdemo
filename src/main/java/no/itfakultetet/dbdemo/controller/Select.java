package no.itfakultetet.dbdemo.controller;

import no.itfakultetet.dbdemo.model.Dao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;

@Controller
public class Select {

    private static final Logger logger = LoggerFactory.getLogger(Select.class);

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
            default -> {
                logger.error("Ukjent databasehåndteringssystem: {}", rdbms_sti);
                yield null;
            }
        };
    }

    private String finnRdbmsNavn(String rdbms_sti) {
        return switch (rdbms_sti) {
            case "postgres" -> "PostgreSQL";
            case "microsoft" -> "Microsoft SQL Server";
            case "oracle" -> "Oracle";
            case "mysql" -> "MySQL/MariaDB";
            default -> "unknown";
        };
    }

    @GetMapping(value = "/select/{rdbms_sti}")
    public String hentSql(Model model, @PathVariable("rdbms_sti") String rdbms_sti,
                          @CookieValue(value = "skin", defaultValue = "agate") String skin) {
        model.addAttribute("rdbms", finnRdbmsNavn(rdbms_sti));
        model.addAttribute("rdbms_sti", rdbms_sti);
        model.addAttribute("skin", skin);
        return "select";
    }

    @PostMapping(value = "/select/{rdbms_sti}")
    public String hentData(Model model,
           @PathVariable("rdbms_sti") String rdbms_sti,
           @RequestParam(value = "db") String db,
           @RequestParam(value = "query") String query) {

        String rdbms = finnRdbmsNavn(rdbms_sti);
        String[] bruker = finnBruker(rdbms_sti);

        model.addAttribute("query", query);
        model.addAttribute("db", db);
        model.addAttribute("rdbms", rdbms);
        model.addAttribute("rdbms_sti", rdbms_sti);

        if (bruker == null) {
            model.addAttribute("feil", "Ukjent databasehåndteringssystem: " + rdbms_sti);
            return "select";
        }

        try {
            Dao.QueryResult resultat = dao.executeQuery(rdbms_sti, db, query, bruker[0], bruker[1]);
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
