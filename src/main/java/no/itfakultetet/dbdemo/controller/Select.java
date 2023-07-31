package no.itfakultetet.dbdemo.controller;

import no.itfakultetet.dbdemo.model.Dao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.sql.ResultSet;
import java.sql.SQLException;

@Controller
public class Select {

    private static final Logger logger = LoggerFactory.getLogger(Select.class);
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

    @GetMapping(value = "/select/{rdbms_sti}")
    public String hentSql(Model model, @PathVariable("rdbms_sti") String rdbms_sti) {
        String rdbms;
        String username = null;
        String pwd = null;

        if(rdbms_sti.equals("postgres")) {
            rdbms = "PostgreSQL";
        } else if(rdbms_sti.equals("microsoft")) {
            rdbms = "Microsoft SQL Server";
        } else if(rdbms_sti.equals("oracle")) {
            rdbms = "Oracle";
        } else if (rdbms_sti.equals("mysql")) {
            rdbms = "MySQL/MariaDB";
        } else {
            rdbms = "unknown";
            logger.error("Ukjent databasehåndteringssystem: "+rdbms_sti);
        }

            model.addAttribute("rdbms",rdbms);
            model.addAttribute("rdbms_sti",rdbms_sti);

            return "select";
    }

    @PostMapping(value = "/select/{rdbms_sti}")
    public String hentData(Model model,
           @PathVariable("rdbms_sti") String rdbms_sti,
           @RequestParam(value = "db") String db,
           @RequestParam(value = "query") String query) {

        logger.info("rdbms_sti fra select/post: "+rdbms_sti);

        String rdbms;
        String username = null;
        String pwd = null;

        if(rdbms_sti.equals("postgres")) {
            rdbms = "PostgreSQL";
            username = pgUsername;
            pwd = pgPwd;
        } else if(rdbms_sti.equals("microsoft")) {
            rdbms = "Microsoft SQL Server";
            username = msUsername;
            pwd = msPwd;
        } else if(rdbms_sti.equals("oracle")) {
            rdbms = "Oracle";
            username = orUsername;
            pwd = orPwd;
        } else if (rdbms_sti.equals("mysql")) {
            rdbms = "MySQL/MariaDB";
            username = myUsername;
            pwd = myPwd;
        } else {
            rdbms = "unknown";
            username = "unknown";
            pwd = "unknown";
            logger.error("Ukjent databasehåndteringssystem: "+rdbms_sti+"og brukernavn/passord: "+username+" - "+pwd);
        }

        Dao dao = new Dao();

        try (ResultSet resultSet = dao.createResultset(rdbms_sti, db, query,username,pwd);) {
            model.addAttribute("tableHeader", dao.createHeader(resultSet));
            model.addAttribute("tableContent", dao.createTabledata(resultSet));
            model.addAttribute("query", query);
            model.addAttribute("db",db);
            model.addAttribute("rdbms",rdbms);
            model.addAttribute("rdbms_sti",rdbms_sti);
            logger.info("modell-laget og sendt til resultat.html");
        } catch (SQLException e) {
            // throw new RuntimeException(e);
            logger.error("Feil ved laging av header og tabledata:"+e.getMessage());
            model.addAttribute("feilmelding",e);
            return "error";
        }

        return "resultat";
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
