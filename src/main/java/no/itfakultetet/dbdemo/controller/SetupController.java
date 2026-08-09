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

        if (konfigurert && (authentication == null || !authentication.isAuthenticated())) {
            // Etter oppsett krever /setup innlogging (kun admin kan endre tilkoblinger)
            return "redirect:/login";
        }

        AppConfig config = configService.load();
        if (config.getConnections().isEmpty()) {
            // Fyll inn standardporter som forslag
            Map<String, DbConnection> conns = new LinkedHashMap<>();
            for (String rdbms : RDBMSER) {
                DbConnection c = new DbConnection();
                c.setRdbms(rdbms);
                c.setPort(Dao.defaultPort(rdbms));
                conns.put(rdbms, c);
            }
            config.setConnections(conns);
        }

        model.addAttribute("config", config);
        model.addAttribute("alleredeKonfigurert", konfigurert);
        model.addAttribute("redigeringsmodus", konfigurert);
        model.addAttribute("tilkoblingsstatus", testAlleTilkoblinger(config));
        return "setup";
    }

    /** Lagrer konfigurasjonen fra skjemaet. */
    @PostMapping("/setup")
    public String lagre(Model model,
                        Authentication authentication,
                        @RequestParam("adminUsername") String adminUsername,
                        @RequestParam(value = "adminPassword", required = false) String adminPassword,
                        @RequestParam Map<String, String> alleParametre) {

        boolean konfigurert = configService.isConfigured();
        if (konfigurert && (authentication == null || !authentication.isAuthenticated())) {
            return "redirect:/login";
        }

        AppConfig eksisterende = configService.load();

        // Ved redigering: beholder eksisterende passord-hash hvis feltet er tomt
        String adminPasswordHash;
        if (adminPassword == null || adminPassword.isBlank()) {
            if (konfigurert && eksisterende.getAdminPasswordHash() != null
                    && !eksisterende.getAdminPasswordHash().isBlank()) {
                adminPasswordHash = eksisterende.getAdminPasswordHash();
            } else {
                model.addAttribute("feil", "Du må fylle inn passord for admin-brukeren.");
                return setupSkjema(model, authentication);
            }
        } else {
            adminPasswordHash = passwordEncoder.encode(adminPassword);
        }

        AppConfig config = new AppConfig();
        config.setAdminUsername(adminUsername.trim());
        config.setAdminPasswordHash(adminPasswordHash);

        Map<String, DbConnection> conns = new LinkedHashMap<>();
        for (String rdbms : RDBMSER) {
            DbConnection c = byggTilkobling(rdbms, alleParametre, eksisterende);
            if (c.isValid()) {
                // Test tilkoblingen før vi lagrer
                String feil = dao.testConnection(c);
                if (feil != null) {
                    model.addAttribute("feil", "Kunne ikke koble til " + ConnectionHelper.rdbmsNavn(rdbms)
                            + ": " + feil);
                    model.addAttribute("config", fyllConfigMed(adminUsername, conns));
                    model.addAttribute("alleredeKonfigurert", konfigurert);
                    model.addAttribute("redigeringsmodus", konfigurert);
                    model.addAttribute("tilkoblingsstatus", testAlleTilkoblinger(config));
                    return "setup";
                }
            }
            conns.put(rdbms, c);
        }
        config.setConnections(conns);

        try {
            configService.save(config);
            logger.info("Konfigurasjon lagret. Aktive databaser: {}",
                    conns.values().stream().filter(DbConnection::isValid).count());
            return "redirect:/" + (konfigurert ? "select/postgres" : "login");
        } catch (Exception e) {
            logger.error("Kunne ikke lagre konfigurasjon: {}", e.getMessage());
            model.addAttribute("feil", "Kunne ikke lagre konfigurasjon: " + e.getMessage());
            model.addAttribute("config", config);
            model.addAttribute("alleredeKonfigurert", konfigurert);
            model.addAttribute("redigeringsmodus", konfigurert);
            return "setup";
        }
    }

    /** REST-endepunkt for «Test tilkobling»-knappen i skjemaet. */
    @PostMapping("/setup/test")
    public ResponseEntity<?> testTilkobling(@RequestBody DbConnection tilkobling) {
        String feil = dao.testConnection(tilkobling);
        if (feil == null) {
            return ResponseEntity.ok(Map.of("ok", true,
                    "melding", "Tilkoblingen til " + ConnectionHelper.rdbmsNavn(tilkobling.getRdbms()) + " fungerer!"));
        }
        return ResponseEntity.ok(Map.of("ok", false, "melding", feil));
    }

    /** Tester alle konfigurerte tilkoblinger og returnerer status per RDBMS. */
    private Map<String, String> testAlleTilkoblinger(AppConfig config) {
        Map<String, String> status = new LinkedHashMap<>();
        for (String rdbms : RDBMSER) {
            DbConnection c = config.getConnection(rdbms);
            if (c != null && c.isValid()) {
                String feil = dao.testConnection(c);
                status.put(rdbms, feil == null ? "OK" : feil);
            } else if (c != null && c.isEnabled() && !c.getHost().isBlank()) {
                status.put(rdbms, "Ufullstendig (mangler felt)");
            }
        }
        return status;
    }

    private DbConnection byggTilkobling(String rdbms, Map<String, String> p, AppConfig eksisterende) {
        DbConnection c = new DbConnection();
        c.setRdbms(rdbms);
        c.setEnabled(bool(p, "enabled_" + rdbms));
        c.setHost(str(p, "host_" + rdbms));
        c.setPort(hentInt(p, "port_" + rdbms, Dao.defaultPort(rdbms)));
        c.setDatabase(str(p, "database_" + rdbms));
        c.setUsername(str(p, "username_" + rdbms));
        String passord = str(p, "password_" + rdbms);
        if (passord.isEmpty() && eksisterende != null) {
            DbConnection gammel = eksisterende.getConnection(rdbms);
            if (gammel != null) {
                passord = gammel.getPassword(); // behold gammelt passord
            }
        }
        c.setPassword(passord);
        return c;
    }

    private AppConfig fyllConfigMed(String adminUsername, Map<String, DbConnection> conns) {
        AppConfig cfg = new AppConfig();
        cfg.setAdminUsername(adminUsername);
        cfg.setConnections(conns);
        return cfg;
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
