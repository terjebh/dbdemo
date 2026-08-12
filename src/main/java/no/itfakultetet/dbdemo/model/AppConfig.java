package no.itfakultetet.dbdemo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Applikasjonskonfigurasjon — lagres som JSON utenfor repoet
 * (se {@code ConfigService}). Inneholder app-admin-brukeren og
 * tilkoblinger for de fire støttede RDBMS-ene.
 */
public class AppConfig {

    private String adminUsername = "";
    /** BCrypt-hash av admin-passordet — aldri klartekst. */
    private String adminPasswordHash = "";

    /** Nøkkel = rdbms_sti: postgres, microsoft, oracle, mysql. */
    private Map<String, DbConnection> connections = new LinkedHashMap<>();

    /**
     * Per-bruker tilkoblinger: brukernavn → (rdbms → DbConnection).
     * Hver bruker kan ha sine egne database-tilkoblinger. Første-adminens
     * tilkoblinger ligger i {@link #connections} (bakoverkompatibelt) og
     * speiles inn her ved lagring.
     */
    private Map<String, Map<String, DbConnection>> brukerTilkoblinger = new LinkedHashMap<>();

    public Map<String, Map<String, DbConnection>> getBrukerTilkoblinger() {
        if (brukerTilkoblinger == null) {
            brukerTilkoblinger = new LinkedHashMap<>();
        }
        return brukerTilkoblinger;
    }

    public void setBrukerTilkoblinger(Map<String, Map<String, DbConnection>> brukerTilkoblinger) {
        this.brukerTilkoblinger = brukerTilkoblinger;
    }

    /** Tilkoblingene til én bruker (tomt kart hvis ingen egne ennå). */
    public Map<String, DbConnection> brukerTilkoblinger(String brukernavn) {
        Map<String, DbConnection> map = getBrukerTilkoblinger().get(brukernavn);
        if (map == null) {
            map = new LinkedHashMap<>();
            getBrukerTilkoblinger().put(brukernavn, map);
        }
        return map;
    }

    /**
     * Henter tilkoblingen for én bruker (kun brukerens EGNE tilkoblinger).
     * Returnerer null hvis brukeren ikke har konfigurert denne RDBMS-en.
     * Merk: det finnes ingen global/ felles fallback — hver bruker (også
     * admin-brukere) har sine egne tilkoblinger som kun gjelder for dem.
     */
    public DbConnection getConnection(String rdbms, String brukernavn) {
        Map<String, DbConnection> egne = getBrukerTilkoblinger().get(brukernavn);
        if (egne != null) {
            return egne.get(rdbms);
        }
        return null;
    }

    /**
     * Vanlige brukere (ikke admin): brukernavn → BCrypt-hash.
     * Admin-brukeren ligger i adminUsername/adminPasswordHash.
     */
    private Map<String, String> users = new LinkedHashMap<>();

    /**
     * Roller for vanlige brukere: brukernavn → "ADMIN" eller "USER".
     * Brukere som ikke står i kartet er USER (default). Admin-brukeren
     * (adminUsername) er alltid ADMIN.
     */
    private Map<String, String> roller = new LinkedHashMap<>();

    public Map<String, String> getUsers() {
        if (users == null) {
            users = new LinkedHashMap<>();
        }
        return users;
    }

    public void setUsers(Map<String, String> users) {
        this.users = users;
    }

    public Map<String, String> getRoller() {
        if (roller == null) {
            roller = new LinkedHashMap<>();
        }
        return roller;
    }

    public void setRoller(Map<String, String> roller) {
        this.roller = roller;
    }

    /** Rollen til en bruker: ADMIN/USER (admin-brukeren er alltid ADMIN). */
    public String rolle(String brukernavn) {
        if (brukernavn != null && brukernavn.equalsIgnoreCase(adminUsername)) {
            return "ADMIN";
        }
        String r = roller.get(brukernavn);
        return "ADMIN".equals(r) ? "ADMIN" : "USER";
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminPasswordHash() {
        return adminPasswordHash;
    }

    public void setAdminPasswordHash(String adminPasswordHash) {
        this.adminPasswordHash = adminPasswordHash;
    }

    public Map<String, DbConnection> getConnections() {
        return connections;
    }

    public void setConnections(Map<String, DbConnection> connections) {
        this.connections = connections;
    }

    public DbConnection getConnection(String rdbms) {
        return connections.get(rdbms);
    }

    public void putConnection(String rdbms, DbConnection conn) {
        connections.put(rdbms, conn);
    }

    /** Er appen ferdig konfigurert (admin-bruker satt)? */
    @JsonIgnore
    public boolean isConfigured() {
        return adminUsername != null && !adminUsername.isBlank()
                && adminPasswordHash != null && !adminPasswordHash.isBlank();
    }
}
