package no.itfakultetet.dbdemo;

import no.itfakultetet.dbdemo.model.AppConfig;
import no.itfakultetet.dbdemo.model.DbConnection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tester per-bruker tilkoblinger (kun egne — ingen global fallback). */
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
    void egneTilkoblingerBrukesKunForEieren() {
        AppConfig config = new AppConfig();
        config.setAdminUsername("kurs");
        config.getBrukerTilkoblinger().put("student1",
                java.util.Map.of("postgres", conn("postgres", "student-eget.no")));

        // student1 ser sin egen
        assertEquals("student-eget.no", config.getConnection("postgres", "student1").getHost());
        // kurs har IKKE student1s tilkobling — ingen global fallback
        assertNull(config.getConnection("postgres", "kurs"));
    }

    @Test
    void adminBrukerHarKunEgneTilkoblinger() {
        AppConfig config = new AppConfig();
        config.setAdminUsername("kurs");
        config.getBrukerTilkoblinger().put("kurs",
                java.util.Map.of("postgres", conn("postgres", "admin-eget.no")));
        config.getBrukerTilkoblinger().put("student1",
                java.util.Map.of("mysql", conn("mysql", "student-eget.no")));

        // Admin-brukerens tilkoblinger gjelder kun for admin-brukeren
        assertEquals("admin-eget.no", config.getConnection("postgres", "kurs").getHost());
        assertNull(config.getConnection("postgres", "student1"));
        assertNull(config.getConnection("mysql", "kurs"));
    }

    @Test
    void brukerUtenEgneHarIngenTilkoblinger() {
        AppConfig config = new AppConfig();
        config.setAdminUsername("kurs");
        config.getBrukerTilkoblinger().put("kurs",
                java.util.Map.of("postgres", conn("postgres", "admin-eget.no")));

        // Ny bruker uten egne tilkoblinger arver INGENTING (heller ikke admin-ens)
        assertNull(config.getConnection("postgres", "student1"));
        assertNull(config.getConnection("mysql", "student1"));
    }
}
