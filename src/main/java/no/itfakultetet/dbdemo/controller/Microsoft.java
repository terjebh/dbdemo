package no.itfakultetet.dbdemo.controller;

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

public class Microsoft {
    private static final Logger logger = LoggerFactory.getLogger(Microsoft.class);

        public static ResultSet createResultset(String db, String query, String username,String pwd ) {
            System.out.println("username: "+ username);
            System.out.println("pwd: "+ pwd);
            String url = "jdbc:sqlserver://noderia.com:1433;databaseName=hr;user=kurs2;password=:)Kurs123;encrypt=false";
        ResultSet rs = null;
        try {
            Connection conn = DriverManager.getConnection(url);
            Statement st = conn.createStatement();
            rs = st.executeQuery(query);
            // ResultSetMetaData rsmd = rs.getMetaData();

        } catch (SQLException e) {
            //throw new RuntimeException(e);
            // System.out.println("Noe gikk galt: \nFeilkode:" + e.getErrorCode() + "\nFeilmelding: " + e.getMessage());
            logger.error("Noe gikk galt. Feilmelding: " + e.getMessage());
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

    public static List<String> createTableList(ResultSet resultSetTables) throws SQLException {
        List<String> tableList = new ArrayList<>();

        while (resultSetTables.next()) {
            tableList.add(resultSetTables.getString(1)+": "+resultSetTables.getString(2)+" ("+resultSetTables.getString(3)+")");
        }
        return tableList.stream().toList();
    }

// get list of tables
    // SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';


}
