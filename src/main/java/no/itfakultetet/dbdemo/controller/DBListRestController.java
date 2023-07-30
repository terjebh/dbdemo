package no.itfakultetet.dbdemo.controller;

import no.itfakultetet.dbdemo.model.Dao;
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
    public List hentDBListe(@PathVariable("rdbms") String rdbms_sti) {
        String database = null;
        String databaseQuery = null;
        String username = null;
        String pwd = null;
        List dbListe;

        if (rdbms_sti.equals("postgres")) {
            username = pgUsername;
//            logger.info("pgUsername er: "+pgUsername);
            pwd = pgPwd;
//            logger.info("pgPwd er: "+pgPwd);
            database = "dbdemo";
            databaseQuery = "select datname from pg_database WHERE has_database_privilege('" + username + "', datname, 'CONNECT') and datistemplate = false";
//            logger.info("dbQuery er: "+databaseQuery);
        } else if (rdbms_sti.equals("microsoft")) {
            username = msUsername;
            pwd = msPwd;
            database = "hr";
            databaseQuery = "Select * from Sys.Databases";
        } else if (rdbms_sti.equals("oracle")) {
            username = orUsername;
            pwd = orPwd;
            database = "kurs";
            databaseQuery = "SELECT USERNAME FROM ALL_USERS where username like 'K%' ORDER BY USERNAME";
        } else if (rdbms_sti.equals("mysql")) {
            username = myUsername;
            pwd = myPwd;
            database = "hr";
            databaseQuery = "SELECT schema_name FROM information_schema.schemata";
        } else {
            logger.error("Ukjent databasehåndteringssystem: " + rdbms_sti);
        }

        try (ResultSet resultSetDbs = Dao.createResultset(rdbms_sti, database, databaseQuery, username, pwd);) {
            dbListe = Dao.createDbList(resultSetDbs);
//          logger.info("dbListe: "+dbListe);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return dbListe;
    }

}

