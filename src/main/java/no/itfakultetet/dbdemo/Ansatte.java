package no.itfakultetet.dbdemo;

import org.springframework.beans.factory.annotation.Value;
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
    @Value("${app.username}")
    public static String username;

    @Value("${app.pwd}")
    public static String pwd;

    @RequestMapping(value = "/ansatte")
    public String ansatte(Model model) {

        ResultSet resultSet = Postgres.createResultset("hr", "Select * from employees limit 100",username,pwd);

        try {
            model.addAttribute("tableHeader", Postgres.createHeader(resultSet));
            model.addAttribute("tableContent", Postgres.createTabledata(resultSet));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return "ansatte";
    }


}
