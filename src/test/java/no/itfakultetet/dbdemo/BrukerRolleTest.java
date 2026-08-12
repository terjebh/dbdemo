package no.itfakultetet.dbdemo;

import no.itfakultetet.dbdemo.model.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tester rolle-logikken for brukere (ADMIN/USER). */
class BrukerRolleTest {

    @Test
    void adminBrukerErAlltidAdmin() {
        AppConfig config = new AppConfig();
        config.setAdminUsername("terje");
        config.getUsers().put("student1", "hash");
        // Admin-brukeren er ADMIN selv om roller-kartet er tomt
        assertEquals("ADMIN", config.rolle("terje"));
        // Sammenligning er case-insensitiv
        assertEquals("ADMIN", config.rolle("TERJE"));
    }

    @Test
    void brukerUtenRolleErUser() {
        AppConfig config = new AppConfig();
        config.setAdminUsername("terje");
        config.getUsers().put("student1", "hash");
        assertEquals("USER", config.rolle("student1"));
        assertEquals("USER", config.rolle("ukjent"));
    }

    @Test
    void eksplisittAdminRolleGjelder() {
        AppConfig config = new AppConfig();
        config.setAdminUsername("terje");
        config.getUsers().put("student1", "hash");
        config.getRoller().put("student1", "ADMIN");
        assertEquals("ADMIN", config.rolle("student1"));

        // Nedgradering: fjerne fra roller-kartet → USER igjen
        config.getRoller().remove("student1");
        assertEquals("USER", config.rolle("student1"));
    }
}
