package no.itfakultetet.dbdemo.controllers;

import jakarta.websocket.OnError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.sql.ResultSet;
import java.sql.SQLException;

@Controller
public class Select {

    @Value("${app.username}")
    private String username;

    @Value("${app.pwd}")
    private String pwd;

    @GetMapping(value = "/select/postgres")
    public String hentSql(Model model) {
        String database = "dbdemo";
        String databaseQuery = "select datname from pg_database WHERE has_database_privilege('"+username+"', datname, 'CONNECT') and datistemplate = false";
        try ( ResultSet resultSetDbs = Postgres.createResultset(database,databaseQuery,username,pwd);) {
            model.addAttribute("dbList", Postgres.createDbList(resultSetDbs));
            model.addAttribute("rdbms","PostgreSQL");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return "select";
    }

    @PostMapping(value = "/select/postgres")
    public String hentData(Model model, @RequestParam(value = "db") String db, @RequestParam(value = "query") String query) {

        ResultSet resultSet = Postgres.createResultset(db,query,username,pwd);

        try {
            model.addAttribute("tableHeader", Postgres.createHeader(resultSet));
            model.addAttribute("tableContent", Postgres.createTabledata(resultSet));
            model.addAttribute("query", query);
            model.addAttribute("db",db);
            //model.addAttribute("rdbms","postgres");
        } catch (SQLException e) {
            // throw new RuntimeException(e);
            model.addAttribute("feilmelding",e);
            return "error";
        }

        return "resultat";
    }


    @GetMapping(value = "/select/microsoft")
    public String hentSqlMs(Model model) {
        String database = "hr";
        String databaseQuery = "Select * from Sys.Databases";
        try ( ResultSet resultSetDbs = Microsoft.createResultset(database,databaseQuery,"kurs1",":)Kurs123");) {
            model.addAttribute("dbList", Microsoft.createDbList(resultSetDbs));
            model.addAttribute("rdbms","Microsoft SQL Server");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return "select";
    }

}
