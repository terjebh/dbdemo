package no.itfakultetet.dbdemo.controller;

import no.itfakultetet.dbdemo.config.ConfigService;
import no.itfakultetet.dbdemo.model.AppConfig;
import no.itfakultetet.dbdemo.model.Dao;
import no.itfakultetet.dbdemo.model.DbConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Oppsettsveiviser (/setup):
 * <ul>
 *   <li>Før appen er konfigurert: viser first-run-skjemaet (åpent for alle).</li>
 *   <li>Etter konfigurering: krever innlogging, og viser skjemaet med
 *       eksisterende verdier fylt ut (redigeringsmodus) — for å oppdatere
 *       utdatert tilkoblingsinformasjon. Tomme passordfelt = behold gammelt.</li>
 *   <li>Viser tilkoblingsstatus (test av alle konfigurerte databaser).</li>
 * </ul>
 */
@Controller
public class SetupController {

    private static final Logger logger = LoggerFactory.getLogger(SetupController.class);
    private static final List<String> RDBMSER = List.of("postgres", "microsoft", "oracle", "mysql");

    private final ConfigService configService;
    private final Dao dao;
    private final PasswordEncoder passwordEncoder;

    public SetupController(ConfigService configService, Dao dao, PasswordEncoder passwordEncoder) {
        this.configService = configService;
        this.dao = dao;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/setup")
    public String setupSkjema(Model model, Authentication authentication) {
        boolean konfigurert = configService.isConfigured();
        String bruker = authentication == null ? "anonym" : authentication.getName();

        // Før oppsett: ingen innlogging nødvendig (SetupInterceptor styrer).
        // Etter oppsett: krever innlogging (SecurityConfig) — hver bruker ser
        // SINE EGNE tilkoblinger.
        AppConfig config = configService.load();
        Map<String, DbConnection> mine = config.getBrukerTilkoblinger().get(bruker);
        if (mine == null || mine.isEmpty()) {
            // Første gang: start fra tomme skjemaer med standardporter
            mine = new LinkedHashMap<>();
            for (String rdbms : RDBMSER) {
                DbConnection c = new DbConnection();
                c.setRdbms(rdbms);
                c.setPort(Dao.defaultPort(rdbms));
                mine.put(rdbms, c);
            }
        }

        model.addAttribute("config", config);
        model.addAttribute("mineTilkoblinger", mine);
        model.addAttribute("bruker", bruker);
        model.addAttribute("erAdminBruker", bruker.equals(config.getAdminUsername()));
        model.addAttribute("alleredeKonfigurert", konfigurert);
        model.addAttribute("redigeringsmodus", konfigurert);
        model.addAttribute("tilkoblingsstatus", testAlleTilkoblinger(mine));
        return "setup";
    }

    /** Lagrer konfigurasjonen fra skjemaet — per innlogget bruker. */
    @PostMapping("/setup")
    public String lagre(Model model,
                        Authentication authentication,
                        @RequestParam(value = "adminUsername", required = false) String adminUsername,
                        @RequestParam(value = "adminPassword", required = false) String adminPassword,
                        @RequestParam Map<String, String> alleParametre) {

        boolean konfigurert = configService.isConfigured();
        String bruker = authentication == null ? "anonym" : authentication.getName();
        if (konfigurert && !erInnlogget(authentication)) {
            return "redirect:/login";
        }

        AppConfig eksisterende = configService.load();

        // Ved first-run er man ikke innlogget ennå — tilkoblingene tilhører
        // den NYE admin-brukeren fra skjemaet, ikke "anonym".
        if (!konfigurert) {
            bruker = adminUsername == null ? "anonym" : adminUsername.trim();
        }

        // Ved redigering: beholder eksisterende passord-hash hvis feltet er tomt
        String adminPasswordHash = eksisterende.getAdminPasswordHash();
        if (!konfigurert) {
            if (adminPassword == null || adminPassword.isBlank()) {
                model.addAttribute("feil", "Du må fylle inn passord for admin-brukeren.");
                return setupSkjema(model, authentication);
            }
            adminPasswordHash = passwordEncoder.encode(adminPassword);
            eksisterende.setAdminUsername(adminUsername.trim());
            eksisterende.setAdminPasswordHash(adminPasswordHash);
        } else if (adminPassword != null && !adminPassword.isBlank()) {
            // Innlogget bruker kan bytte passordet sitt (hvis det er admin-brukeren)
            if (bruker.equals(eksisterende.getAdminUsername())) {
                eksisterende.setAdminPasswordHash(passwordEncoder.encode(adminPassword));
            } else {
                // Vanlige brukere: oppdater passord-hash i users-kartet
                String hash = eksisterende.getUsers().get(bruker);
                if (hash != null) {
                    eksisterende.getUsers().put(bruker, passwordEncoder.encode(adminPassword));
                }
            }
        }

        // Bygg brukerens tilkoblinger fra skjemaet
        Map<String, DbConnection> mine = new LinkedHashMap<>();
        for (String rdbms : RDBMSER) {
            DbConnection c = byggTilkobling(rdbms, alleParametre, eksisterende, bruker);
            if (c.isValid()) {
                // Test tilkoblingen før vi lagrer
                String feil = dao.testConnection(c);
                if (feil != null) {
                    model.addAttribute("feil", "Kunne ikke koble til " + ConnectionHelper.rdbmsNavn(rdbms)
                            + ": " + feil);
                    model.addAttribute("config", eksisterende);
                    model.addAttribute("mineTilkoblinger", mine);
                    model.addAttribute("bruker", bruker);
                    model.addAttribute("erAdminBruker", bruker.equals(eksisterende.getAdminUsername()));
                    model.addAttribute("alleredeKonfigurert", konfigurert);
                    model.addAttribute("redigeringsmodus", konfigurert);
                    model.addAttribute("tilkoblingsstatus", testAlleTilkoblinger(mine));
                    return "setup";
                }
            }
            mine.put(rdbms, c);
        }
        // Lagre per bruker — hver bruker har sine egne tilkoblinger,
        // og admin-brukerens tilkoblinger gjelder kun for den admin-brukeren.
        eksisterende.getBrukerTilkoblinger().put(bruker, mine);

        try {
            configService.save(eksisterende);
            logger.info("Konfigurasjon lagret for bruker {}. Aktive databaser: {}",
                    bruker, mine.values().stream().filter(DbConnection::isValid).count());
            // First-run: man er ikke innlogget ennå → til innlogging.
            // Redigeringsmodus: bli værende på /setup (med suksess-melding)
            // slik at brukeren kan legge til flere tilkoblinger — «Lukk»-
            // knappen sender brukeren videre til appen.
            if (!konfigurert) {
                return "redirect:/login";
            }
            return "redirect:/setup?lagret=1";
        } catch (Exception e) {
            logger.error("Kunne ikke lagre konfigurasjon: {}", e.getMessage());
            model.addAttribute("feil", "Kunne ikke lagre konfigurasjon: " + e.getMessage());
            model.addAttribute("config", eksisterende);
            model.addAttribute("mineTilkoblinger", mine);
            model.addAttribute("bruker", bruker);
            model.addAttribute("erAdminBruker", bruker.equals(eksisterende.getAdminUsername()));
            model.addAttribute("alleredeKonfigurert", konfigurert);
            model.addAttribute("redigeringsmodus", konfigurert);
            return "setup";
        }
    }

    /** REST-endepunkt for «Test tilkobling»-knappen i skjemaet. */
    @PostMapping("/setup/test")
    public ResponseEntity<?> testTilkobling(@RequestBody DbConnection tilkobling,
                                            Authentication authentication) {
        // Tomt passordfelt (sikkerhet: lagret passord vises aldri i skjemaet)
        // → bruk det lagrede passordet fra den INNLOGGEDE brukerens config
        if (tilkobling.getPassword() == null || tilkobling.getPassword().isBlank()) {
            String bruker = authentication == null ? "anonym" : authentication.getName();
            DbConnection lagret = configService.load().getConnection(tilkobling.getRdbms(), bruker);
            if (lagret != null && lagret.getPassword() != null && !lagret.getPassword().isBlank()) {
                tilkobling.setPassword(lagret.getPassword());
            }
        }
        String feil = dao.testConnection(tilkobling);
        if (feil == null) {
            return ResponseEntity.ok(Map.of("ok", true,
                    "melding", "Tilkoblingen til " + ConnectionHelper.rdbmsNavn(tilkobling.getRdbms()) + " fungerer!"));
        }
        return ResponseEntity.ok(Map.of("ok", false, "melding", feil));
    }

    /** Tester alle konfigurerte tilkoblinger og returnerer status per RDBMS. */
    private Map<String, String> testAlleTilkoblinger(Map<String, DbConnection> mine) {
        Map<String, String> status = new LinkedHashMap<>();
        for (String rdbms : RDBMSER) {
            DbConnection c = mine.get(rdbms);
            if (c != null && c.isValid()) {
                String feil = dao.testConnection(c);
                status.put(rdbms, feil == null ? "OK" : feil);
            } else if (c != null && c.isEnabled() && !c.getHost().isBlank()) {
                status.put(rdbms, "Ufullstendig (mangler felt)");
            }
        }
        return status;
    }

    /** Er en reell innlogget bruker (ikke anonymous)? */
    private boolean erInnlogget(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
    }

    private DbConnection byggTilkobling(String rdbms, Map<String, String> p, AppConfig eksisterende, String bruker) {
        DbConnection c = new DbConnection();
        c.setRdbms(rdbms);
        c.setEnabled(bool(p, "enabled_" + rdbms));
        c.setHost(str(p, "host_" + rdbms));
        c.setPort(hentInt(p, "port_" + rdbms, Dao.defaultPort(rdbms)));
        c.setDatabase(str(p, "database_" + rdbms));
        c.setUsername(str(p, "username_" + rdbms));
        String passord = str(p, "password_" + rdbms);
        if (passord.isEmpty() && eksisterende != null) {
            // Behold gammelt passord fra DENNE brukerens egne tilkoblinger
            DbConnection gammel = eksisterende.getConnection(rdbms, bruker);
            if (gammel != null) {
                passord = gammel.getPassword(); // behold gammelt passord
            }
        }
        c.setPassword(passord);
        return c;
    }

    private String str(Map<String, String> p, String nøkkel) {
        String v = p.get(nøkkel);
        return v == null ? "" : v.trim();
    }

    private int hentInt(Map<String, String> p, String nøkkel, int def) {
        String v = p.get(nøkkel);
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private boolean bool(Map<String, String> p, String nøkkel) {
        return "on".equals(p.get(nøkkel)) || "true".equals(p.get(nøkkel));
    }
}
