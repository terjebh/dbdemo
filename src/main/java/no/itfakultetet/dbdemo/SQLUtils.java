package no.itfakultetet.dbdemo;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

public class SQLUtils {

    static void writeMetadata(ResultSetMetaData rsmd) throws SQLException {
        System.out.printf("%-2s \t", "#");
        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
            try {
                System.out.printf("%20s", rsmd.getColumnName(i) + "\t");
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }
        System.out.println("\n" + "-".repeat((rsmd.getColumnCount() + 1) * 20 + 3));
    }

    static void writeRecords(ResultSet rs, ResultSetMetaData rsmd) throws SQLException {
        while (rs.next()) {
            System.out.printf("%2s", rs.getRow() + "\t");
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                System.out.printf("%20s", rs.getObject(i));
            }
            System.out.println();

        }
    }

}
