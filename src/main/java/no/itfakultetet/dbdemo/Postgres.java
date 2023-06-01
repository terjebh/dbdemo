package no.itfakultetet.dbdemo;

import com.zaxxer.hikari.pool.HikariProxyResultSet;

import javax.sql.RowSet;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Postgres {

    public Postgres(String query) {


    }

    public static ResultSet createResultset(String query) {
        String url = "jdbc:postgresql://noderia.com/hr?user=kurs&password=kurs123&ssl=false";
        ResultSet rs = null;
        try {
            Connection conn = DriverManager.getConnection(url);
            Statement st = conn.createStatement();
            rs = st.executeQuery(query);
            // ResultSetMetaData rsmd = rs.getMetaData();

        } catch (SQLException e) {
            //throw new RuntimeException(e);
            System.out.println("Noe gikk galt: \nFeilkode:" + e.getErrorCode() + "\nFeilmelding: " + e.getMessage());
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


        while(resultSet.next()) {
            List<String> rad = new ArrayList<>();
            for (int i = 1; i <= numberOfColumns; i++) {
                 rad.add(resultSet.getString(i));
            }

            tabell.add(rad);


        }


        return tabell;
    }

}
