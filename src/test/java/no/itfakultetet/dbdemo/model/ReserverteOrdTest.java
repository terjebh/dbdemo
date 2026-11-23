package no.itfakultetet.dbdemo.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tester reserve-lista over reserverte ord — brukes til å escape kolonnenavn
 * som kolliderer med nøkkelord/funksjoner (f.eks. «to_date» i MariaDB 12, der
 * navnet ble et funksjonsnavn og «SELECT to_date FROM salaries» gir syntaksfeil).
 */
class ReserverteOrdTest {

    private final Dao dao = new Dao();

    @Test
    void listenInneholderKjenteProblemord() {
        Set<String> ord = dao.getReservedWordsFor("mysql");
        for (String ventet : new String[]{"to_date", "date", "interval", "order", "select", "table"}) {
            assertTrue(ord.contains(ventet), "Mangler «" + ventet + "» i reserve-lista");
        }
    }

    @Test
    void vanligeKolonnenavnErIkkeReserverte() {
        Set<String> ord = dao.getReservedWordsFor("mysql");
        for (String ufarlig : new String[]{"emp_no", "salary", "from_date", "navn", "epost"}) {
            assertFalse(ord.contains(ufarlig), "«" + ufarlig + "» skal ikke regnes som reservert");
        }
    }

    @Test
    void listenErLikForAlleSystemer() {
        // Reserve-lista brukes som fallback for MSSQL/Oracle/SQLite
        assertFalse(dao.getReservedWordsFor("microsoft").isEmpty());
        assertFalse(dao.getReservedWordsFor("oracle").isEmpty());
        assertFalse(dao.getReservedWordsFor("sqlite").isEmpty());
    }
}
