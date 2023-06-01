package no.itfakultetet.dbdemo;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.sql.ResultSet;
import java.sql.SQLException;

@Controller
public class Ansatte {



    @RequestMapping(value = "/ansatte")
    public String ansatte(Model model) {

        ResultSet resultSet = Postgres.createResultset("Select * from employees limit 100");

        try {
            model.addAttribute("tableHeader", Postgres.createHeader(resultSet));
            model.addAttribute("tableContent", Postgres.createTabledata(resultSet));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return "ansatte";
    }


}
