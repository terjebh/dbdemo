package no.itfakultetet.dbdemo.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@RestController
public class DBListRestController {
    private static final Logger logger = LoggerFactory.getLogger(DBListRestController.class);
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

    @GetMapping(value = "/rest/get/dblist/{rdbms}")
    public String hentDBListe(@PathVariable("rdbms") String rdbms_sti) {
        String database;
        String databaseQuery;
        String username;
        String pwd;
        String dbListe = "";
        List<String> DBListe = new ArrayList<>();

        if (rdbms_sti.equals("postgres")) {
            username = pgUsername;
            pwd = pgPwd;
            database = "dbdemo";
            databaseQuery = "select datname from pg_database WHERE has_database_privilege('" + username + "', datname, 'CONNECT') and datistemplate = false";
        } else if (rdbms_sti.equals("microsoft")) {
            username = msUsername;
            pwd = msPwd;
            database = "hr";
            databaseQuery = "select datname from pg_database WHERE has_database_privilege('" + username + "', datname, 'CONNECT') and datistemplate = false";
        } else if (rdbms_sti.equals("oracle")) {
            username = orUsername;
            pwd = orPwd;
            database = "kurs";
            databaseQuery = "select datname from pg_database WHERE has_database_privilege('" + username + "', datname, 'CONNECT') and datistemplate = false";
        } else if (rdbms_sti.equals("mysql")) {
            username = myUsername;
            pwd = myPwd;
            database = "hr";
            databaseQuery = "show databases";
        } else {
            logger.error("Ukjent databasehåndteringssystem: " + rdbms_sti);
            return "Ukjent databasehåndteringssystem: " + rdbms_sti;
        }

        try (ResultSet resultSetDbs = Dao.createResultset(rdbms_sti, database, databaseQuery, username, pwd);) {
            dbListe = Dao.createDbList(resultSetDbs);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }


        return dbListe;
    }

}

