package no.itfakultetet.dbdemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Klasse som kobler til Postgres og lager resultatsett og header og content til tabeller i Thymeleaf.
 *
 * @author Terje Berg-Hansen
 */
public class Postgres {
    private static final Logger logger = LoggerFactory.getLogger(Postgres.class);

    public static ResultSet createResultset(String db, String query) {
        String url = "jdbc:postgresql://noderia.com/"+db+"?user=kurs&password=kurs123&ssl=false";
        ResultSet rs = null;
        try {
            Connection conn = DriverManager.getConnection(url);
            Statement st = conn.createStatement();
            rs = st.executeQuery(query);
            // ResultSetMetaData rsmd = rs.getMetaData();

        } catch (SQLException e) {
            //throw new RuntimeException(e);
            // System.out.println("Noe gikk galt: \nFeilkode:" + e.getErrorCode() + "\nFeilmelding: " + e.getMessage());
            logger.error("Noe gikk galt: Feilkode:" + e.getErrorCode() + "Feilmelding: " + e.getMessage());

        }
        return rs;
    }

    public static Object createHeader(ResultSet resultSet) throws SQLException {

        ResultSetMetaData metadata = resultSet.getMetaData();
        final int count = metadata.getColumnCount();
        final List<String> header = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            header.add(metadata.getColumnName(i));
        }
        return header;
    }

    public static List<List<String>> createTabledata(ResultSet resultSet) throws SQLException {

        ResultSetMetaData metadata = resultSet.getMetaData();
        int numberOfColumns = metadata.getColumnCount();

        List<List<String>> tabell = new ArrayList<>();

        while (resultSet.next()) {
            List<String> rad = new ArrayList<>();
            for (int i = 1; i <= numberOfColumns; i++) {
                rad.add(resultSet.getString(i));
            }
            tabell.add(rad);
        }
        return tabell;
    }

    public static List<String> createDbList(ResultSet resultSet)  throws SQLException{
        List<String> dbList = new ArrayList<>();

        while (resultSet.next()) {
            dbList.add(resultSet.getString(1));
        }
            return dbList.stream().sorted().toList();
    }

// get list of tables
    // SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';


}
