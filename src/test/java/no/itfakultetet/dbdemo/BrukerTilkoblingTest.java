package no.itfakultetet.dbdemo;

import no.itfakultetet.dbdemo.model.AppConfig;
import no.itfakultetet.dbdemo.model.DbConnection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tester per-bruker tilkoblinger (egne først, global som fallback). */
class BrukerTilkoblingTest {

    private DbConnection conn(String rdbms, String host) {
        DbConnection c = new DbConnection();
        c.setRdbms(rdbms);
        c.setHost(host);
        c.setPort(5432);
        c.setDatabase("hr");
        c.setUsername("bruker");
        c.setPassword("pass");
        c.setEnabled(true);
        return c;
    }

    @Test
    void egneTilkoblingerVinnerOverGlobal() {
        AppConfig config = new AppConfig();
        config.setAdminUsername("kurs");
        config.putConnection("postgres", conn("postgres", "felles.no"));
        config.getBrukerTilkoblinger().put("student1",
                java.util.Map.of("postgres", conn("postgres", "student-eget.no")));

        assertEquals("student-eget.no", config.getConnection("postgres", "student1").getHost());
        assertEquals("felles.no", config.getConnection("postgres", "kurs").getHost());
    }

    @Test
    void globalErFallbackForAlle() {
        AppConfig config = new AppConfig();
        config.setAdminUsername("kurs");
        config.putConnection("postgres", conn("postgres", "felles.no"));

        // Bruker uten egne tilkoblinger arver felles/global
        assertEquals("felles.no", config.getConnection("postgres", "student1").getHost());
        // Uten global → null
        assertNull(config.getConnection("mysql", "student1"));
    }

    @Test
    void lagringPerBrukerPaavirkerIkkeAndre() {
        AppConfig config = new AppConfig();
        config.setAdminUsername("kurs");
        config.putConnection("postgres", conn("postgres", "felles.no"));

        config.getBrukerTilkoblinger().put("student1",
                java.util.Map.of("postgres", conn("postgres", "student-eget.no")));

        // kurs' globale tilkobling er urørt av student1s egne
        assertEquals("felles.no", config.getConnection("postgres", "kurs").getHost());
        assertEquals("student-eget.no", config.getConnection("postgres", "student1").getHost());
    }
}
