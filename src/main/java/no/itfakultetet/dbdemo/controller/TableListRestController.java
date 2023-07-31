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
public class TableListRestController {

    private static final Logger logger = LoggerFactory.getLogger(TableListRestController.class);
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

    @GetMapping(value = "/rest/get/tablelist/{rdbms_sti}/{db}")
    public String hentTabeller(@PathVariable("rdbms_sti") String rdbms_sti, @PathVariable("db") String database) {

        String tableQuery = null;
        String username = null;
        String pwd = null;
        List tableList;


        if (rdbms_sti.equals("postgres")) {
            username = pgUsername;
            //  logger.info("pgUsername er: "+pgUsername);
            pwd = pgPwd;
            //  logger.info("pgPwd er: "+pgPwd);
            tableQuery = "select table_schema, table_name, table_type from information_schema.tables where not table_schema in ('pg_catalog','information_schema')  order by table_schema, table_type, table_name";
            //   logger.info("dbQuery er: "+tableQuery);
        } else if (rdbms_sti.equals("microsoft")) {
            username = msUsername;
            pwd = msPwd;
            tableQuery = "SELECT TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_CATALOG='" + database + "'";
        } else if (rdbms_sti.equals("oracle")) {
            username = orUsername;
            pwd = orPwd;
            tableQuery = "SELECT owner, table_name, 'TABLE' FROM all_tables where owner='" + username.toUpperCase() + "' union Select owner, view_name, 'VIEW' from all_views where owner='" + username.toUpperCase() + "'";
        } else if (rdbms_sti.equals("mysql")) {
            username = myUsername;
            pwd = myPwd;
            tableQuery = "SELECT table_schema, table_name, table_type FROM information_schema.tables WHERE table_schema = '" + database + "'";
        } else {
            logger.error("Ukjent databasehåndteringssystem: " + rdbms_sti);
        }


        List<String> tabellListe = new ArrayList<>();
        Dao dao = new Dao();

        if (!database.equals("Velg Database")) {
            try (ResultSet resultsetTables = dao.createResultset(rdbms_sti, database, tableQuery, username, pwd)) {
                tabellListe = dao.createTableList(resultsetTables);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        StringBuilder tabeller = new StringBuilder();

        tabeller.append("<h4>Tabeller og views</h4>\n");
        // tabeller.append("<ul>");
        for (String tabell : tabellListe) {
            tabeller.append(tabell + "<br/>\n");
        }
        //tabeller.append("</ul");

        return tabeller.toString();
    }


}




