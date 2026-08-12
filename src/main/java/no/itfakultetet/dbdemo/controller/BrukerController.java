package no.itfakultetet.dbdemo.controller;

import no.itfakultetet.dbdemo.config.ConfigService;
import no.itfakultetet.dbdemo.model.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.TreeMap;

/**
 * Brukeradministrasjon (kun admin): liste, opprett og slett vanlige brukere.
 * <p>
 * Vanlige brukere får ROLE_USER — de kan skrive SQL, men får IKKE tilgang
 * til /setup (tilkoblinger) eller /brukere (brukeradministrasjon).
 * <p>
 * Admin-brukeren opprettes i first-run-veiviseren (/setup) og kan ikke
 * slettes her.
 */
@Controller
@RequestMapping("/brukere")
public class BrukerController {

    private static final Logger logger = LoggerFactory.getLogger(BrukerController.class);

    private final ConfigService configService;
    private final PasswordEncoder passwordEncoder;

    public BrukerController(ConfigService configService, PasswordEncoder passwordEncoder) {
        this.configService = configService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String liste(Model model) {
        AppConfig config = configService.load();
        model.addAttribute("adminBruker", config.getAdminUsername());
        model.addAttribute("brukere", new TreeMap<>(config.getUsers()));
        // Rolle per bruker (for tabellen): brukernavn → ADMIN/USER
        Map<String, String> roller = new TreeMap<>();
        for (String navn : config.getUsers().keySet()) {
            roller.put(navn, config.rolle(navn));
        }
        model.addAttribute("roller", roller);
        return "brukere";
    }

    /** Oppretter en bruker med valgfri rolle (ADMIN/USER, default USER). */
    @PostMapping("/opprett")
    public String opprett(Model model,
                          @RequestParam("brukernavn") String brukernavn,
                          @RequestParam("passord") String passord,
                          @RequestParam(value = "rolle", defaultValue = "USER") String rolle) {
        AppConfig config = configService.load();

        String navn = brukernavn == null ? "" : brukernavn.trim();
        if (navn.isBlank() || passord == null || passord.isBlank()) {
            model.addAttribute("feil", "Både brukernavn og passord må fylles ut.");
            return liste(model);
        }
        if (navn.equalsIgnoreCase(config.getAdminUsername())) {
            model.addAttribute("feil", "Dette brukernavnet er reservert for admin-brukeren.");
            return liste(model);
        }
        if (config.getUsers().containsKey(navn)) {
            model.addAttribute("feil", "Brukernavnet finnes allerede.");
            return liste(model);
        }

        config.getUsers().put(navn, passwordEncoder.encode(passord));
        if ("ADMIN".equals(rolle)) {
            config.getRoller().put(navn, "ADMIN");
        }
        try {
            configService.save(config);
            logger.info("Bruker opprettet: {} (rolle: {})", navn, config.rolle(navn));
        } catch (Exception e) {
            logger.error("Kunne ikke lagre bruker: {}", e.getMessage());
            model.addAttribute("feil", "Kunne ikke lagre bruker: " + e.getMessage());
            return liste(model);
        }
        return "redirect:/brukere";
    }

    /**
     * Endrer passord og/eller rolle for en eksisterende bruker.
     * Tomt passordfelt = behold eksisterende passord.
     */
    @PostMapping("/endre")
    public String endre(Model model,
                        @RequestParam("brukernavn") String brukernavn,
                        @RequestParam(value = "passord", required = false) String passord,
                        @RequestParam(value = "rolle", defaultValue = "USER") String rolle) {
        AppConfig config = configService.load();
        String navn = brukernavn == null ? "" : brukernavn.trim();

        if (navn.isBlank()) {
            return "redirect:/brukere";
        }

        // Admin-brukeren (fra first-run): kan endre passord, men ikke rolle
        if (navn.equalsIgnoreCase(config.getAdminUsername())) {
            if (passord != null && !passord.isBlank()) {
                config.setAdminPasswordHash(passwordEncoder.encode(passord));
            } else {
                model.addAttribute("feil", "Fyll inn et nytt passord for admin-brukeren.");
                return liste(model);
            }
        } else {
            String hash = config.getUsers().get(navn);
            if (hash == null) {
                model.addAttribute("feil", "Fant ikke brukeren: " + navn);
                return liste(model);
            }
            if (passord != null && !passord.isBlank()) {
                config.getUsers().put(navn, passwordEncoder.encode(passord));
            }
            // Sett/endre rolle (ADMIN eller USER)
            if ("ADMIN".equals(rolle)) {
                config.getRoller().put(navn, "ADMIN");
            } else {
                config.getRoller().remove(navn); // USER er default
            }
        }

        try {
            configService.save(config);
            logger.info("Bruker endret: {} (rolle: {})", navn, config.rolle(navn));
        } catch (Exception e) {
            logger.error("Kunne ikke lagre brukerendring: {}", e.getMessage());
            model.addAttribute("feil", "Kunne ikke lagre: " + e.getMessage());
            return liste(model);
        }
        return "redirect:/brukere";
    }

    /** Sletter en vanlig bruker. Admin kan ikke slettes. */
    @PostMapping("/slett")
    public String slett(Model model, @RequestParam("brukernavn") String brukernavn) {
        AppConfig config = configService.load();
        String navn = brukernavn == null ? "" : brukernavn.trim();

        if (navn.isBlank()) {
            return "redirect:/brukere";
        }
        if (navn.equalsIgnoreCase(config.getAdminUsername())) {
            model.addAttribute("feil", "Admin-brukeren kan ikke slettes.");
            return liste(model);
        }
        if (config.getUsers().remove(navn) == null) {
            model.addAttribute("feil", "Fant ikke brukeren: " + navn);
            return liste(model);
        }
        config.getRoller().remove(navn); // rydd opp i rolle-kartet

        try {
            configService.save(config);
            logger.info("Bruker slettet: {}", navn);
        } catch (Exception e) {
            logger.error("Kunne ikke lagre etter sletting: {}", e.getMessage());
            model.addAttribute("feil", "Kunne ikke lagre: " + e.getMessage());
            return liste(model);
        }
        return "redirect:/brukere";
    }
}
