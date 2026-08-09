package no.itfakultetet.dbdemo.controller;

import no.itfakultetet.dbdemo.config.ConfigService;
import no.itfakultetet.dbdemo.model.DbConnection;
import org.springframework.stereotype.Component;

/**
 * Hjelpeklasse for controllerne: henter DbConnection fra ConfigService
 * og gir pen feilmelding hvis en RDBMS ikke er konfigurert.
 */
@Component
public class ConnectionHelper {

    private final ConfigService configService;

    public ConnectionHelper(ConfigService configService) {
        this.configService = configService;
    }

    /** Henter tilkoblingen for en RDBMS-sti; kaster IllegalArgumentException hvis ikke konfigurert. */
    public DbConnection hentEllerFeil(String rdbmsSti) {
        DbConnection conn = configService.getConnection(rdbmsSti);
        if (conn == null) {
            throw new IllegalArgumentException("Ukjent databasehåndteringssystem: " + rdbmsSti);
        }
        if (!conn.isEnabled() || !conn.isValid()) {
            throw new IllegalArgumentException("Databasehåndteringssystemet er ikke konfigurert: " + rdbmsSti
                    + ". Gå til /setup for å fylle inn tilkoblingsinformasjon.");
        }
        return conn;
    }

    /** Visningsnavn for en RDBMS-sti. */
    public static String rdbmsNavn(String rdbmsSti) {
        return switch (rdbmsSti) {
            case "postgres" -> "PostgreSQL";
            case "microsoft" -> "Microsoft SQL Server";
            case "oracle" -> "Oracle";
            case "mysql" -> "MySQL/MariaDB";
            default -> "unknown";
        };
    }
}
