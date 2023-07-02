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
        String query = "select datname from pg_database";

        try ( ResultSet resultSet = Postgres.createResultset("hr",query)) {
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
