package no.itfakultetet.dbdemo.config;

import no.itfakultetet.dbdemo.model.AppConfig;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/**
 * Legger meny-data på alle Thymeleaf-modeller:
 * <ul>
 *   <li>{@code tilgjengeligeSystemer} — databasesystemer med tilkoblingsinfo
 *       (pluss SQLite), brukt i toppmenyen slik at brukeren kan bytte system
 *       fra hvor som helst.</li>
 * </ul>
 */
@ControllerAdvice
public class MenyModelAdvice {

    private final ConfigService configService;

    public MenyModelAdvice(ConfigService configService) {
        this.configService = configService;
    }

    @ModelAttribute("tilgjengeligeSystemer")
    public List<String> tilgjengeligeSystemer() {
        AppConfig config = configService.load();
        return no.itfakultetet.dbdemo.controller.HomeController.tilgjengeligeSystemer(config);
    }

    @ModelAttribute("systemNavn")
    public java.util.Map<String, String> systemNavn() {
        return java.util.Map.of(
                "postgres", "PostgreSQL",
                "microsoft", "Microsoft SQL",
                "oracle", "Oracle SQL",
                "mysql", "MySQL",
                "sqlite", "SQLite");
    }
}
