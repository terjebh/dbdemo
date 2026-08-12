package no.itfakultetet.dbdemo.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lagring av brukernes SQL-filer: én mappe per bruker under
 * {@code <config-mappe>/sql/}, slik at hver bruker bare ser sine egne filer.
 * Filnavn valideres strengt (kun bokstaver, tall, bindestrek, understrek) —
 * path-traversal-blokkert.
 */
@Service
public class SQLFilService {

    private static final Logger logger = LoggerFactory.getLogger(SQLFilService.class);

    private static final Pattern GYLDIG_NAVN = Pattern.compile("[A-Za-z0-9_\\-]{1,60}");

    /** Overstyres av Spring (--dbdemo.config.path) hvis satt — samme sti som ConfigService. */
    @org.springframework.beans.factory.annotation.Value("${dbdemo.config.path:}")
    public void setConfigPath(String configPath) {
        if (configPath != null && !configPath.isBlank()) {
            this.configPathOverstyring = Paths.get(configPath);
        }
    }

    private Path configPathOverstyring;

    /** Rot-mappe for SQL-filer: <config-mappe>/sql */
    private Path sqlRot() {
        Path config;
        if (configPathOverstyring != null) {
            config = configPathOverstyring;
        } else {
            String env = System.getenv("DBDEMO_CONFIG");
            config = (env != null && !env.isBlank())
                    ? Paths.get(env)
                    : Paths.get(System.getProperty("user.home"), ".dbdemo", "dbconfig.json");
        }
        return config.getParent().resolve("sql");
    }

    /** Mappen til en bestemt bruker sine SQL-filer. */
    private Path brukerMappe(String brukernavn) {
        if (brukernavn == null || !GYLDIG_NAVN.matcher(brukernavn).matches()) {
            throw new IllegalArgumentException("Ugyldig brukernavn");
        }
        return sqlRot().resolve(brukernavn);
    }

    /** Full sti til en SQL-fil (validerer begge ledd). */
    private Path sqlFil(String brukernavn, String filnavn) {
        if (filnavn == null || !GYLDIG_NAVN.matcher(filnavn).matches()) {
            throw new IllegalArgumentException("Ugyldig filnavn: «" + filnavn + "» — kun bokstaver, tall, bindestrek og understrek er tillatt.");
        }
        return brukerMappe(brukernavn).resolve(filnavn + ".sql");
    }

    /** Lister brukerens SQL-filer (uten .sql-suffix, sortert). */
    public List<String> listFiler(String brukernavn) {
        Path mappe = brukerMappe(brukernavn);
        if (!Files.isDirectory(mappe)) {
            return new ArrayList<>();
        }
        List<String> navn = new ArrayList<>();
        try (var stream = Files.list(mappe)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .forEach(p -> navn.add(p.getFileName().toString().replaceAll("\\.sql$", "")));
        } catch (Exception e) {
            logger.error("Kunne ikke liste SQL-filer for {}: {}", brukernavn, e.getMessage());
        }
        return navn.stream().sorted().toList();
    }

    /** Lagrer (eller overskriver) en SQL-fil for brukeren. */
    public void lagreFil(String brukernavn, String filnavn, String innhold) {
        Path fil = sqlFil(brukernavn, filnavn);
        try {
            Files.createDirectories(fil.getParent());
            Files.writeString(fil, innhold, StandardCharsets.UTF_8);
            logger.info("SQL-fil lagret: {} for {}", filnavn, brukernavn);
        } catch (IOException e) {
            logger.error("Kunne ikke lagre SQL-fil {}: {}", filnavn, e.getMessage());
            throw new IllegalArgumentException("Kunne ikke lagre filen: " + e.getMessage());
        }
    }

    /** Leser en SQL-fil for brukeren; null hvis den ikke finnes. */
    public String lesFil(String brukernavn, String filnavn) {
        Path fil = sqlFil(brukernavn, filnavn);
        if (!Files.exists(fil)) {
            return null;
        }
        try {
            return Files.readString(fil, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Kunne ikke lese SQL-fil {}: {}", filnavn, e.getMessage());
            throw new IllegalArgumentException("Kunne ikke lese filen: " + e.getMessage());
        }
    }

    /** Sletter en SQL-fil for brukeren. */
    public void slettFil(String brukernavn, String filnavn) {
        Path fil = sqlFil(brukernavn, filnavn);
        try {
            Files.deleteIfExists(fil);
            logger.info("SQL-fil slettet: {} for {}", filnavn, brukernavn);
        } catch (IOException e) {
            logger.error("Kunne ikke slette SQL-fil {}: {}", filnavn, e.getMessage());
            throw new IllegalArgumentException("Kunne ikke slette filen: " + e.getMessage());
        }
    }
}
