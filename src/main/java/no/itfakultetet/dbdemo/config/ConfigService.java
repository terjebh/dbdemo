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
import java.util.LinkedHashMap;
import java.util.Map;
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

    /**
     * Laster konfigurasjonen; returnerer tom config hvis filen ikke finnes.
     * Migrerer gamle configs: global «connections» → brukerens egne.
     */
    public AppConfig load() {
        if (!Files.exists(configPath)) {
            return new AppConfig();
        }
        try {
            AppConfig cfg = objectMapper.readValue(configPath.toFile(), AppConfig.class);
            // Migrering: før per-bruker-tilkoblinger lå alt i «connections»
            // (global). Nå skal alt være per bruker — overfør global til
            // første-admin hvis brukeren ikke allerede har egne.
            if (cfg.getAdminUsername() != null && !cfg.getAdminUsername().isBlank()
                    && cfg.getConnections() != null && !cfg.getConnections().isEmpty()) {
                String admin = cfg.getAdminUsername().trim();
                Map<String, DbConnection> egne = cfg.getBrukerTilkoblinger().get(admin);
                if (egne == null || egne.isEmpty()) {
                    cfg.getBrukerTilkoblinger().put(admin, new LinkedHashMap<>(cfg.getConnections()));
                    logger.info("Migrerte globale tilkoblinger til bruker {}", admin);
                }
            }
            return cfg;
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
