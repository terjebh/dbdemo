package no.itfakultetet.dbdemo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.sql.ResultSet;
import java.sql.SQLException;

@Controller
public class Select {

    @Value("${app.username}")
    public static String username;

    @Value("${app.pwd}")
    public static String pwd;

    @GetMapping(value = "/select")
    public String hentSql(Model model) {
        String database = "hr";
        String user = "dbdemo";
        String databaseQuery = "select datname from pg_database WHERE has_database_privilege('"+user+"', datname, 'CONNECT') and datistemplate = false";
        String tableQuery = "select table_schema, table_name, table_type from information_schema.tables where not table_schema in ('pg_catalog','information_schema')  order by table_schema, table_type, table_name";
        try ( ResultSet resultSetDbs = Postgres.createResultset(database,databaseQuery,username,pwd);
              ResultSet resultsetTables = Postgres.createResultset(database,tableQuery,username,pwd); ) {
            model.addAttribute("dbList", Postgres.createDbList(resultSetDbs));
            model.addAttribute(("dbTables"), Postgres.createTableList(resultsetTables) );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return "select";
    }

    @PostMapping(value = "/select")
    public String hentData(Model model, @RequestParam(value = "db") String db, @RequestParam(value = "query") String query) {

        ResultSet resultSet = Postgres.createResultset(db,query,username,pwd);

        try {
            model.addAttribute("tableHeader", Postgres.createHeader(resultSet));
            model.addAttribute("tableContent", Postgres.createTabledata(resultSet));
            model.addAttribute("query", query);
            model.addAttribute("db",db);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return "resultat";
    }


}
