package no.itfakultetet.dbdemo.controller;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tester gjenkjenning av session-kommandoer (SET/ALTER SESSION) som skal
 * lagres og replayes på nye tilkoblinger — slik at session-state (språk,
 * dato-format, tidssone) overlever mellom spørringer.
 */
class SessionKommandoTest {

    @Test
    void fangerMySqlLcTimeNames() {
        assertEquals("SET lc_time_names = 'fr_FR'",
                QueryRestController.fangOppSessionKommando("SET lc_time_names = 'fr_FR'", "mysql"));
    }

    @Test
    void fangerMySqlMedSemikolon() {
        assertEquals("SET lc_time_names = 'fr_FR'",
                QueryRestController.fangOppSessionKommando("SET lc_time_names = 'fr_FR';", "mysql"));
    }

    @Test
    void fangerMssqlSprak() {
        assertEquals("SET LANGUAGE Norwegian",
                QueryRestController.fangOppSessionKommando("SET LANGUAGE Norwegian", "microsoft"));
    }

    @Test
    void fangerOracleAlterSession() {
        assertEquals("ALTER SESSION SET NLS_LANGUAGE = FRENCH",
                QueryRestController.fangOppSessionKommando("ALTER SESSION SET NLS_LANGUAGE = FRENCH", "oracle"));
    }

    @Test
    void fangerPostgresTidssone() {
        assertEquals("SET TIME ZONE 'UTC'",
                QueryRestController.fangOppSessionKommando("SET TIME ZONE 'UTC'", "postgres"));
    }

    @Test
    void ignorerVanligSelect() {
        assertNull(QueryRestController.fangOppSessionKommando("SELECT * FROM employees", "mysql"));
    }

    @Test
    void ignorerBatchMedFlereSetninger() {
        assertNull(QueryRestController.fangOppSessionKommando(
                "SET lc_time_names = 'fr_FR'; SELECT MONTHNAME(NOW());", "mysql"));
    }

    @Test
    void ignorerAutocommit() {
        assertNull(QueryRestController.fangOppSessionKommando("SET autocommit = 0", "mysql"));
    }

    @Test
    void ignorerBrukerVariabel() {
        assertNull(QueryRestController.fangOppSessionKommando("SET @minvar = 5", "mysql"));
    }

    @Test
    void ignorerTransaksjonsStyring() {
        assertNull(QueryRestController.fangOppSessionKommando("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE", "postgres"));
    }

    @Test
    void tillaterMssqlTransactionIsolation() {
        assertEquals("SET TRANSACTION ISOLATION LEVEL READ COMMITTED",
                QueryRestController.fangOppSessionKommando(
                        "SET TRANSACTION ISOLATION LEVEL READ COMMITTED", "microsoft"));
    }

    @Test
    void ignorerOracleContainer() {
        assertNull(QueryRestController.fangOppSessionKommando(
                "ALTER SESSION SET CONTAINER = KURS1", "oracle"));
    }

    @Test
    void ignorerSqlite() {
        assertNull(QueryRestController.fangOppSessionKommando("SET x = 1", "sqlite"));
    }

    @Test
    void ignorerDdl() {
        assertNull(QueryRestController.fangOppSessionKommando("CREATE TABLE test (id INT)", "mysql"));
    }
}
