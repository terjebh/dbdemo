package no.itfakultetet.dbdemo.config;

import no.itfakultetet.dbdemo.model.AppConfig;
import no.itfakultetet.dbdemo.model.DbConnection;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Legger meny-data på alle Thymeleaf-modeller:
 * <ul>
 *   <li>{@code tilgjengeligeSystemer} — databasesystemer med tilkoblingsinfo
 *       (pluss SQLite), brukt i system-velgeren i venstrespalten.</li>
 *   <li>{@code systemValg} — liste av {verdi, navn}-par for nedtrekksmenyen
 *       (unngår map-oppslag i Thymeleaf).</li>
 * </ul>
 */
@ControllerAdvice
public class MenyModelAdvice {

    private final ConfigService configService;

    public MenyModelAdvice(ConfigService configService) {
        this.configService = configService;
    }

    @ModelAttribute("tilgjengeligeSystemer")
    public List<String> tilgjengeligeSystemer(Authentication authentication) {
        AppConfig config = configService.load();
        String bruker = authentication == null ? "anonym" : authentication.getName();
        Map<String, DbConnection> egne = config.getBrukerTilkoblinger().get(bruker);
        return no.itfakultetet.dbdemo.controller.HomeController.tilgjengeligeSystemer(egne);
    }

    /** Liste av {verdi, navn}-par for system-velgeren i venstrespalten. */
    @ModelAttribute("systemValg")
    public List<Map<String, String>> systemValg(Authentication authentication) {
        Map<String, String> navn = systemNavn();
        List<Map<String, String>> valg = new ArrayList<>();
        for (String verdi : tilgjengeligeSystemer(authentication)) {
            Map<String, String> par = new LinkedHashMap<>();
            par.put("verdi", verdi);
            par.put("navn", navn.getOrDefault(verdi, verdi));
            valg.add(par);
        }
        return valg;
    }

    @ModelAttribute("systemNavn")
    public Map<String, String> systemNavn() {
        Map<String, String> navn = new LinkedHashMap<>();
        navn.put("postgres", "PostgreSQL");
        navn.put("microsoft", "Microsoft SQL");
        navn.put("oracle", "Oracle SQL");
        navn.put("mysql", "MySQL");
        navn.put("sqlite", "SQLite");
        return navn;
    }
}
