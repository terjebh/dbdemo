package no.itfakultetet.dbdemo;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Klasse som viser en oversikt over ansatte.
 *
 * @author Terje Berg-Hansen
 * @version 0.0.1
 */
@Controller
public class Ansatte {


    @RequestMapping(value = "/ansatte")
    public String ansatte(Model model) {

        ResultSet resultSet = Postgres.createResultset("hr", "Select * from employees limit 100");

        try {
            model.addAttribute("tableHeader", Postgres.createHeader(resultSet));
            model.addAttribute("tableContent", Postgres.createTabledata(resultSet));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return "ansatte";
    }


}
