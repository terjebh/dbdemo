package no.itfakultetet.dbdemo.controller;

import no.itfakultetet.dbdemo.config.ConfigService;
import no.itfakultetet.dbdemo.model.AppConfig;
import no.itfakultetet.dbdemo.model.DbConnection;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;

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

    /** Systemer som er tilgjengelige for innlogging (har tilkoblingsinfo). */
    public static List<String> tilgjengeligeSystemer(AppConfig config) {
        List<String> systemer = new ArrayList<>();
        if (config != null && config.getConnections() != null) {
            for (String rdbms : SYSTEMREKKEFØLGE) {
                DbConnection c = config.getConnections().get(rdbms);
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
    public static String førsteSystem(AppConfig config) {
        List<String> systemer = tilgjengeligeSystemer(config);
        if (systemer.isEmpty()) {
            return null;
        }
        // Postgres foretrekkes (har oftest øvelsesdata), ellers første i rekken
        if (systemer.contains("postgres")) {
            return "postgres";
        }
        return systemer.get(0);
    }

    @GetMapping("/")
    public String hjem() {
        AppConfig config = configService.load();
        if (!configService.isConfigured()) {
            return "redirect:/setup";
        }
        String system = førsteSystem(config);
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
