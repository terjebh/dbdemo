package no.itfakultetet.dbdemo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Tilkoblingsinformasjon for én database (én RDBMS).
 * <p>
 * Feltet {@code database} betyr:
 * <ul>
 *   <li>PostgreSQL / MySQL: databasenavn (f.eks. {@code hr})</li>
 *   <li>MS SQL Server: databaseName (f.eks. {@code hr})</li>
 *   <li>Oracle: service-navn / SID (f.eks. {@code HR})</li>
 * </ul>
 */
public class DbConnection {

    /** RDBMS-nøkkel: postgres, microsoft, oracle eller mysql. */
    private String rdbms = "";
    private boolean enabled = true;
    private String host = "";
    private int port;
    private String database = "";
    private String username = "";
    private String password = "";

    public String getRdbms() {
        return rdbms;
    }

    public void setRdbms(String rdbms) {
        this.rdbms = rdbms;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /** Er tilkoblingen fullstendig utfylt (kan brukes)? */
    @JsonIgnore
    public boolean isValid() {
        return rdbms != null && !rdbms.isBlank()
                && enabled && host != null && !host.isBlank()
                && username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }
}
