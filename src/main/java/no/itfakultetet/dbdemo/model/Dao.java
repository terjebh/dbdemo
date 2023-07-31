package no.itfakultetet.dbdemo.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Klasse som kobler til Databaser og lager resultatsett og header og content til tabeller i Thymeleaf.
 *
 * @author Terje Berg-Hansen
 */

public class Dao {
    private static final Logger logger = LoggerFactory.getLogger(Dao.class);
    private String rdbms_sti, db, username, pwd;
    private Connection conn;

    private Connection getConn() {
        // Get connection strings
        String url = "";
        if (rdbms_sti.equals("postgres")) {
            url = "jdbc:postgresql://noderia.com/" + db + "?user=" + username + "&password=" + pwd + "&ssl=false";
        } else if (rdbms_sti.equals("microsoft")) {
            url = "jdbc:sqlserver://noderia.com:1433;databaseName=hr;user=" + username + ";password=" + pwd + ";encrypt=false";
        } else if (rdbms_sti.equals("oracle")) {
            url = "jdbc:oracle:thin:@noderia.com:1521:FREE";
        } else if (rdbms_sti.equals("mysql")) {
            url = "jdbc:mysql://noderia.com/" + db + "?user=" + username + "&password=" + pwd;
        } else {
            logger.error("Ukjent databasehåndteringssystem...: " + rdbms_sti);
        }


        try {

            if (rdbms_sti.equals("oracle")) {
                conn = DriverManager.getConnection(url, username, pwd);
            } else {
                conn = DriverManager.getConnection(url);
            }

        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
        return conn;
    }


    /**
     * Creates a resultset from a supplied SQL statement
     *
     * @param rdbms_sti
     * @param db
     * @param query
     * @param username
     * @param pwd
     * @return
     */
    public ResultSet createResultset(String rdbms_sti, String db, String query, String username, String pwd) {
        this.rdbms_sti = rdbms_sti;
        this.username = username;
        this.db = db;
        this.pwd = pwd;

        ResultSet rs = null;
        Connection conn = getConn();
        try {
            Statement st = conn.createStatement();
            rs = st.executeQuery(query);
            // ResultSetMetaData rsmd = rs.getMetaData();

        } catch (SQLException e) {
            //throw new RuntimeException(e);
            // System.out.println("Noe gikk galt: \nFeilkode:" + e.getErrorCode() + "\nFeilmelding: " + e.getMessage());
            logger.error("Noe gikk galt i createResultSet. Feilmelding: " + e.getMessage());
        }

        return rs;
    }

    public Object createHeader(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        final int count = metadata.getColumnCount();
        final List<String> header = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            header.add(metadata.getColumnName(i));
        }
        return header;
    }

    public List<List<String>> createTabledata(ResultSet resultSet) throws SQLException {

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
        conn.close();
        resultSet.close();
        return tabell;
    }

    public List<String> createDbList(ResultSet resultSetDB) throws SQLException {
        List<String> dbList = new ArrayList<>();

        while (resultSetDB.next()) {
            dbList.add(resultSetDB.getString(1));
        }
        resultSetDB.close();
        conn.close();
        return dbList.stream().sorted().toList();
    }

    public List<String> createTableList(ResultSet resultSetTables) throws SQLException {
        List<String> tableList = new ArrayList<>();

        while (resultSetTables.next()) {
            tableList.add(resultSetTables.getString(1) + ": " + resultSetTables.getString(2) + " (" + resultSetTables.getString(3) + ")");
        }
        resultSetTables.close();
        conn.close();
        return tableList.stream().toList();
    }

}
