package no.itfakultetet.dbdemo;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.sql.ResultSet;
import java.sql.SQLException;

@Controller
public class Select {

    @GetMapping(value = "/select")

    public String hentSql() {

        return "select";
    }

    @PostMapping(value = "/select")
    public String hentData(Model model, @RequestParam(value = "query") String query) {

        ResultSet resultSet = Postgres.createResultset(query);

        try {
            model.addAttribute("tableHeader", Postgres.createHeader(resultSet));
            model.addAttribute("tableContent", Postgres.createTabledata(resultSet));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return "resultat";
    }


}
