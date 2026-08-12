package no.itfakultetet.dbdemo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.itfakultetet.dbdemo.model.AppConfig;
import no.itfakultetet.dbdemo.model.DbConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/**
 * Laster og lagrer applikasjonskonfigurasjonen som JSON.
 * <p>
 * Konfigurasjonsfilen ligger UTENFOR repoet: {@code ~/.dbdemo/dbconfig.json}
 * (overstyr med miljøvariabelen {@code DBDEMO_CONFIG}). Filen får
 * {@code 600}-rettigheter (kun eier kan lese/skrive).
 */
@Service
public class ConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path configPath;

    public ConfigService(@org.springframework.beans.factory.annotation.Value(
            "${dbdemo.config.path:}") String configPathEnv) {
        if (configPathEnv != null && !configPathEnv.isBlank()) {
            this.configPath = Paths.get(configPathEnv);
        } else {
            this.configPath = Paths.get(System.getProperty("user.home"), ".dbdemo", "dbconfig.json");
        }
        logger.info("Konfigurasjonsfil: {}", this.configPath);
    }

    public Path getConfigPath() {
        return configPath;
    }

    /** Er appen konfigurert (finnes en gyldig config med admin-bruker)? */
    public boolean isConfigured() {
        if (!Files.exists(configPath)) {
            return false;
        }
        try {
            AppConfig cfg = load();
            return cfg != null && cfg.isConfigured();
        } catch (Exception e) {
            logger.error("Kunne ikke lese konfigurasjon: {}", e.getMessage());
            return false;
        }
    }

    /** Laster konfigurasjonen; returnerer tom config hvis filen ikke finnes. */
    public AppConfig load() {
        if (!Files.exists(configPath)) {
            return new AppConfig();
        }
        try {
            return objectMapper.readValue(configPath.toFile(), AppConfig.class);
        } catch (IOException e) {
            logger.error("Kunne ikke lese {}: {}", configPath, e.getMessage());
            return new AppConfig();
        }
    }

    /** Lagrer konfigurasjonen med 600-rettigheter. */
    public void save(AppConfig config) throws IOException {
        Files.createDirectories(configPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), config);
        setOwnerOnly(configPath);
        logger.info("Konfigurasjon lagret til {}", configPath);
    }

    /**
     * Henter tilkoblingen for en RDBMS sett fra én brukers ståsted
     * (egne tilkoblinger først, deretter felles/global som fallback).
     */
    public DbConnection getConnection(String rdbms, String brukernavn) {
        return load().getConnection(rdbms, brukernavn);
    }

    /** Henter tilkoblingen for en RDBMS fra felles/global config (første-admin). */
    public DbConnection getConnection(String rdbms) {
        return load().getConnection(rdbms);
    }

    private void setOwnerOnly(Path path) {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, perms);
        } catch (Exception e) {
            logger.debug("Kunne ikke sette filrettigheter på {}: {}", path, e.getMessage());
        }
    }
}
