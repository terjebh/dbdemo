package no.itfakultetet.dbdemo.controller;

import no.itfakultetet.dbdemo.config.ConfigService;
import no.itfakultetet.dbdemo.model.AppConfig;
import no.itfakultetet.dbdemo.model.DbConnection;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hjem- og «Om DBApp»-sider.
 * <p>
 * Rot-URL-en (/) redirecter til det første tilgjengelige databasesystemet
 * (PostgreSQL hvis konfigurert, ellers det første med tilkoblingsinfo), slik
 * at studentene kan begynne å skrive SQL med en gang — ikke en statisk side.
 */
@Controller
public class HomeController {

    private final ConfigService configService;

    public HomeController(ConfigService configService) {
        this.configService = configService;
    }

    /** Riktig rekkefølge for systemene i menyen. */
    public static final List<String> SYSTEMREKKEFØLGE = List.of(
            "postgres", "microsoft", "oracle", "mysql");

    /** Systemer som er tilgjengelige for en bruker (har tilkoblingsinfo). */
    public static List<String> tilgjengeligeSystemer(Map<String, DbConnection> brukerensTilkoblinger) {
        List<String> systemer = new ArrayList<>();
        if (brukerensTilkoblinger != null) {
            for (String rdbms : SYSTEMREKKEFØLGE) {
                DbConnection c = brukerensTilkoblinger.get(rdbms);
                if (c != null && c.isEnabled() && c.isValid()) {
                    systemer.add(rdbms);
                }
            }
        }
        // SQLite er alltid tilgjengelig for innloggede brukere
        systemer.add("sqlite");
        return systemer;
    }

    /** Første tilgjengelige system — postgres hvis konfigurert, ellers det første. */
    public static String førsteSystem(Map<String, DbConnection> brukerensTilkoblinger) {
        List<String> systemer = tilgjengeligeSystemer(brukerensTilkoblinger);
        if (systemer.isEmpty()) {
            return null;
        }
        // Postgres foretrekkes (har oftest øvelsesdata), ellers første i rekken
        if (systemer.contains("postgres")) {
            return "postgres";
        }
        return systemer.get(0);
    }

    /** Har brukeren minst én gyldig database-tilkobling (ikke bare SQLite)? */
    public static boolean harTilkoblinger(Map<String, DbConnection> brukerensTilkoblinger) {
        if (brukerensTilkoblinger == null) return false;
        for (String rdbms : SYSTEMREKKEFØLGE) {
            DbConnection c = brukerensTilkoblinger.get(rdbms);
            if (c != null && c.isEnabled() && c.isValid()) {
                return true;
            }
        }
        return false;
    }

    @GetMapping("/")
    public String hjem(Authentication authentication) {
        AppConfig config = configService.load();
        if (!configService.isConfigured()) {
            return "redirect:/setup";
        }
        // Ved første innlogging uten egne tilkoblinger → vis tilkoblingsvinduet
        // først, slik at brukeren kan legge inn sine egne.
        String bruker = authentication == null ? "anonym" : authentication.getName();
        Map<String, DbConnection> egne = config.getBrukerTilkoblinger().get(bruker);
        if (!harTilkoblinger(egne)) {
            return "redirect:/setup";
        }
        String system = førsteSystem(egne);
        if (system == null) {
            return "redirect:/setup";
        }
        return "redirect:/select/" + system;
    }

    @GetMapping("/om")
    public String om(Model model) {
        return "index";
    }
}
