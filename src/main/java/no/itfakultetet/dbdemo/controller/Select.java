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
    private String username;

    @Value("${pg.pwd}")
    private String pwd;

    @GetMapping(value = "/select/{rdbms_sti}")
    public String hentSql(Model model, @RequestParam(value = "rdbms_sti") String rdbms_sti) {
        String rdbms;

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

    @PostMapping(value = "/select/postgres")
    public String hentData(Model model,
           @RequestParam(value = "db") String db,
           @RequestParam(value = "query") String query) {

        ResultSet resultSet = Dao.createResultset(db,query,username,pwd, pwd);

        try {
            model.addAttribute("tableHeader", Dao.createHeader(resultSet));
            model.addAttribute("tableContent", Dao.createTabledata(resultSet));
            model.addAttribute("query", query);
            model.addAttribute("db",db);
            model.addAttribute("rdbms","PostgreSQL");
            model.addAttribute("rdbms_sti","postgres");

        } catch (SQLException e) {
            // throw new RuntimeException(e);
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


    @GetMapping(value = "/select/microso  ft")
         public String hentSqlMs(Model model) {
        String database = "hr";
        String databaseQuery = "Select * from Sys.Databases";
        try ( ResultSet resultSetDbs = Microsoft.createResultset(database,databaseQuery,"kurs1",":)Kurs123");) {
            model.addAttribute("dbList", Microsoft.createDbList(resultSetDbs));
            model.addAttribute("rdbms","Microsoft SQL Server");
            model.addAttribute("rdbms_sti","microsoft");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return "select";
    }

    @PostMapping(value = "/select/microsoft")
    public String hentDataMS(Model model, @RequestParam(value = "db") String db, @RequestParam(value = "query") String query) {

        ResultSet resultSet = Microsoft.createResultset(db,query,"kurs1",":)Kurs123");

        try {
            model.addAttribute("tableHeader", Dao.createHeader(resultSet));
            model.addAttribute("tableContent", Dao.createTabledata(resultSet));
            model.addAttribute("query", query);
            model.addAttribute("db",db);
            model.addAttribute("rdbms","Microsoft SQL Server");
            model.addAttribute("rdbms_sti","microsoft");
        } catch (SQLException e) {
            // throw new RuntimeException(e);
            model.addAttribute("feilmelding",e);
            return "error";
        }

        return "resultat";
    }


}
