package no.itfakultetet.dbdemo.controller;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tester gjenkjenning av transaksjonskommandoer (BEGIN/COMMIT/ROLLBACK)
 * per RDBMS — grunnlaget for transaksjonsstøtten i /rest/kjor.
 */
class TransaksjonsKommandoTest {

    @Test
    void gjenkjennerBeginMySql() {
        assertEquals("BEGIN", QueryRestController.transaksjonsKommando("BEGIN", "mysql"));
        assertEquals("BEGIN", QueryRestController.transaksjonsKommando("BEGIN;", "mysql"));
        assertEquals("BEGIN", QueryRestController.transaksjonsKommando("START TRANSACTION", "mysql"));
    }

    @Test
    void gjenkjennerBeginPostgres() {
        assertEquals("BEGIN", QueryRestController.transaksjonsKommando("BEGIN", "postgres"));
        assertEquals("BEGIN", QueryRestController.transaksjonsKommando("BEGIN;", "postgres"));
    }

    @Test
    void gjenkjennerCommitOgRollback() {
        assertEquals("COMMIT", QueryRestController.transaksjonsKommando("COMMIT", "mysql"));
        assertEquals("ROLLBACK", QueryRestController.transaksjonsKommando("ROLLBACK", "postgres"));
        assertEquals("COMMIT", QueryRestController.transaksjonsKommando("COMMIT WORK", "oracle"));
        assertEquals("ROLLBACK", QueryRestController.transaksjonsKommando("ROLLBACK;", "mysql"));
    }

    @Test
    void oracleHarIngenBegin() {
        assertNull(QueryRestController.transaksjonsKommando("BEGIN", "oracle"));
        assertEquals("COMMIT", QueryRestController.transaksjonsKommando("COMMIT", "oracle"));
    }

    @Test
    void ignorerVanligSelect() {
        assertNull(QueryRestController.transaksjonsKommando("SELECT * FROM employees", "mysql"));
        assertNull(QueryRestController.transaksjonsKommando("BEGIN SELECT * FROM x", "mysql"));
    }

    @Test
    void ignorerBatch() {
        assertNull(QueryRestController.transaksjonsKommando("BEGIN; INSERT INTO x VALUES (1);", "mysql"));
    }

    @Test
    void ignorerInsertSomInneholderBegin() {
        assertNull(QueryRestController.transaksjonsKommando("INSERT INTO t VALUES ('begin')", "mysql"));
    }
}
