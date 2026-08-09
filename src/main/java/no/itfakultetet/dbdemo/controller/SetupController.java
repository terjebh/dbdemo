package no.itfakultetet.dbdemo.controller;

import no.itfakultetet.dbdemo.config.ConfigService;
import no.itfakultetet.dbdemo.model.AppConfig;
import no.itfakultetet.dbdemo.model.Dao;
import no.itfakultetet.dbdemo.model.DbConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * First-run-veiviser (/setup): skjema der man fyller inn admin-brukeren og
 * tilkoblingsinformasjon for de databasene appen skal knyttes til.
 * <p>
 * Når konfigurasjonen er lagret, redirectes alt til innlogging og /setup
 * viser kun en «allerede konfigurert»-side.
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
    public String setupSkjema(Model model) {
        if (configService.isConfigured()) {
            model.addAttribute("alleredeKonfigurert", true);
            return "setup";
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
        model.addAttribute("alleredeKonfigurert", false);
        return "setup";
    }

    /** Lagrer konfigurasjonen fra skjemaet. */
    @PostMapping("/setup")
    public String lagre(Model model,
                        @RequestParam("adminUsername") String adminUsername,
                        @RequestParam("adminPassword") String adminPassword,
                        @RequestParam Map<String, String> alleParametre) {

        if (adminUsername == null || adminUsername.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            model.addAttribute("feil", "Du må fylle inn både brukernavn og passord for admin-brukeren.");
            return setupSkjema(model);
        }

        AppConfig config = new AppConfig();
        config.setAdminUsername(adminUsername.trim());
        config.setAdminPasswordHash(passwordEncoder.encode(adminPassword));

        Map<String, DbConnection> conns = new LinkedHashMap<>();
        for (String rdbms : RDBMSER) {
            DbConnection c = byggTilkobling(rdbms, alleParametre);
            if (c.isValid()) {
                // Test tilkoblingen før vi lagrer
                String feil = dao.testConnection(c);
                if (feil != null) {
                    model.addAttribute("feil", "Kunne ikke koble til " + ConnectionHelper.rdbmsNavn(rdbms)
                            + ": " + feil);
                    model.addAttribute("config", fyllConfigMed(adminUsername, conns));
                    model.addAttribute("alleredeKonfigurert", false);
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
            return "redirect:/login";
        } catch (Exception e) {
            logger.error("Kunne ikke lagre konfigurasjon: {}", e.getMessage());
            model.addAttribute("feil", "Kunne ikke lagre konfigurasjon: " + e.getMessage());
            model.addAttribute("config", config);
            model.addAttribute("alleredeKonfigurert", false);
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

    private DbConnection byggTilkobling(String rdbms, Map<String, String> p) {
        DbConnection c = new DbConnection();
        c.setRdbms(rdbms);
        c.setEnabled(bool(p, "enabled_" + rdbms));
        c.setHost(str(p, "host_" + rdbms));
        c.setPort(hentInt(p, "port_" + rdbms, Dao.defaultPort(rdbms)));
        c.setDatabase(str(p, "database_" + rdbms));
        c.setUsername(str(p, "username_" + rdbms));
        c.setPassword(str(p, "password_" + rdbms));
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
