package no.itfakultetet.dbdemo;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.sql.ResultSet;
import java.sql.SQLException;

@Controller
public class Select {

    @GetMapping(value = "/select")
    public String hentSql(Model model) {
        String databases = "select datname from pg_database";
        String tables = "select table_schema, table_name, table_type from information_schema.tables where not table_schema in ('pg_catalog','information_schema')";
        try ( ResultSet resultSet = Postgres.createResultset("hr",databases)) {
            model.addAttribute("dbList", Postgres.createDbList(resultSet));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return "select";
    }

    @PostMapping(value = "/select")
    public String hentData(Model model, @RequestParam(value = "db") String db, @RequestParam(value = "query") String query) {

        ResultSet resultSet = Postgres.createResultset(db,query);

        try {
            model.addAttribute("tableHeader", Postgres.createHeader(resultSet));
            model.addAttribute("tableContent", Postgres.createTabledata(resultSet));
            model.addAttribute("query", query);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return "resultat";
    }


}
