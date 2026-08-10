package no.itfakultetet.dbdemo.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tester klassifiseringen av tilkoblingsfeil vs. vanlige SQL-feil. */
class TilkoblingsfeilTest {

    @Test
    void tilkoblingsfeilSkalFlagges() {
        assertTrue(QueryRestController.erTilkoblingsfeil("Connection refused (Connection refused)"));
        assertTrue(QueryRestController.erTilkoblingsfeil("Could not connect to host: timeout expired"));
        assertTrue(QueryRestController.erTilkoblingsfeil("No suitable driver found for jdbc:postgresql"));
        assertTrue(QueryRestController.erTilkoblingsfeil("ORA-12541: TNS:no listener"));
        assertTrue(QueryRestController.erTilkoblingsfeil("ORA-01017: invalid username/password"));
        assertTrue(QueryRestController.erTilkoblingsfeil("Communications link failure"));
        assertTrue(QueryRestController.erTilkoblingsfeil("Ikke konfigurert: microsoft"));
        assertTrue(QueryRestController.erTilkoblingsfeil("Login failed for user 'kurs'"));
        assertTrue(QueryRestController.erTilkoblingsfeil("Unknown host: itfakultetet.no"));
    }

    @Test
    void vanligeSqlFeilSkalIkkeFlagges() {
        assertFalse(QueryRestController.erTilkoblingsfeil("ERROR: column \"feil_syntax\" does not exist"));
        assertFalse(QueryRestController.erTilkoblingsfeil("Invalid column name 'FEIL_SYNTAX'"));
        assertFalse(QueryRestController.erTilkoblingsfeil("[SQLITE_ERROR] SQL error or missing database (near \"Inser\": syntax error)"));
        assertFalse(QueryRestController.erTilkoblingsfeil("ORA-00942: table or view does not exist"));
        assertFalse(QueryRestController.erTilkoblingsfeil("SELECT * FROM employees"));
        assertFalse(QueryRestController.erTilkoblingsfeil(null));
    }
}
