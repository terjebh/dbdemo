package no.itfakultetet.dbdemo.controllers;

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

    @Value("${app.username}")
    private String username;

    @Value("${app.pwd}")
    private String pwd;

    @GetMapping(value = "/rest/get/dblist/{rdbms}")
    public String hentTabeller(@PathVariable("rdbms") String rdbms) {

        List<String> DBListe = new ArrayList<>();

        if(rdbms.equals("postgres")) {

            String tableQuery = "select table_schema, table_name, table_type from information_schema.tables where not table_schema in ('pg_catalog','information_schema')  order by table_schema, table_type, table_name";
            try (ResultSet resultsetTables = Postgres.createDbList(database, tableQuery, username, pwd)) {
                tabellListe = Postgres.createTableList(resultsetTables);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        StringBuilder tabeller = new StringBuilder();

        tabeller.append("<h4>Tabeller og views</h4>\n");
       // tabeller.append("<ul>");
        for( String tabell: tabellListe) {
            tabeller.append(tabell+"<br/>\n");
        }
        //tabeller.append("</ul");

        System.out.println(tabeller.toString());
        return tabeller.toString();
    }



}




