package no.itfakultetet.dbdemo.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Terminal-emulator for nettleseren: kobler en WebSocket til en lokal
 * bash-prosess med pseudo-TTY (via {@code script -qefc}), slik at
 * interaktive klienter (ssh, psql, sqlplus, mariadb, mssql-cli …)
 * fungerer som i et ekte terminalvindu.
 * <p>
 * Sikkerhet: prosessen kjøres som applikasjonsbrukeren (ikke root) i
 * containeren, uten ekstra rettigheter. WebSocket-handshaken går gjennom
 * Spring Security (innlogging kreves), og hver klient får sin egen shell.
 */
@Component
public class TerminalWebSocketHandler implements WebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(TerminalWebSocketHandler.class);

    /** WebSocket-sesjon → aktiv prosess. */
    private final Map<String, Process> prosesser = new ConcurrentHashMap<>();
    /** WebSocket-sesjon → stdout-lesetråd. */
    private final Map<String, Thread> lesere = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Authentication auth = (Authentication) session.getAttributes().get("authentication");
        String bruker = auth == null ? "anonym" : auth.getName();
        logger.info("Terminal koblet til: {} (sesjon {})", bruker, session.getId());

        // script -qefc gir en pseudo-TTY (nødvendig for ssh/psql-prompt)
        ProcessBuilder pb = new ProcessBuilder("script", "-qefc", "bash", "/dev/null");
        pb.redirectErrorStream(true);
        // TERM er nødvendig for tput-baserte verktøy (psql/ssh-prompter);
        // uten den får brukeren «tput: No value for $TERM»-klager.
        // HOME kan være /root i containeren — brukerens hjemmemappe gir
        // ingen «Permission denied»-klage ved bash-start.
        pb.environment().put("TERM", "xterm-256color");
        pb.environment().put("COLORTERM", "truecolor");
        pb.environment().put("HOME", "/home/appuser");
        pb.environment().put("LANG", "C.UTF-8");
        Process prosess = pb.start();
        prosesser.put(session.getId(), prosess);

        // Les stdout (inkl. stderr) → send til nettleseren
        InputStream inn = prosess.getInputStream();
        Thread leser = new Thread(() -> {
            byte[] buf = new byte[4096];
            try {
                int n;
                while ((n = inn.read(buf)) != -1) {
                    if (n > 0) {
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage(
                                    new String(buf, 0, n, StandardCharsets.UTF_8)));
                        } else {
                            logger.debug("Terminal-sesjon lukket, dropper {} byte", n);
                        }
                    }
                }
            } catch (IOException e) {
                logger.debug("Terminal-lesing avsluttet: {}", e.getMessage());
            }
            try {
                int exit = prosess.waitFor();
                logger.info("Terminal-prosess avsluttet med kode {}", exit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "terminal-io-" + session.getId());
        leser.setDaemon(true);
        leser.start();
        lesere.put(session.getId(), leser);
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        Process prosess = prosesser.get(session.getId());
        if (prosess == null) {
            return;
        }
        String tekst = message.getPayload().toString();
        // Resize-meldinger fra xterm.js: {type:"resize",cols,rows} — endrer
        // PTY-størrelsen via stty, slik at terminalen er skrivbar i HELE
        // vinduet (uten dette blir PTY-en stående på 80×24).
        if (tekst.startsWith("{\"type\":\"resize\"")) {
            try {
                int cols = Integer.parseInt(hentJsonVerdi(tekst, "cols"));
                int rows = Integer.parseInt(hentJsonVerdi(tekst, "rows"));
                if (cols > 0 && rows > 0) {
                    endreStorrelse(session.getId(), cols, rows);
                }
            } catch (Exception e) {
                logger.debug("Ugyldig resize-melding: {}", tekst);
            }
            return;
        }
        // Klient-input → prosessens stdin (terminatortastene sendes som escape-sekvenser)
        OutputStream ut = prosess.getOutputStream();
        ut.write(tekst.getBytes(StandardCharsets.UTF_8));
        ut.flush();
    }

    /** Henter en enkel streng-verdi fra JSON på formen {"key":"verdi"}. */
    private String hentJsonVerdi(String json, String nokkel) {
        String sok = "\"" + nokkel + "\":";
        int i = json.indexOf(sok);
        if (i < 0) throw new IllegalArgumentException("Mangler " + nokkel);
        int start = i + sok.length();
        while (start < json.length() && !Character.isDigit(json.charAt(start))) start++;
        int slutt = start;
        while (slutt < json.length() && Character.isDigit(json.charAt(slutt))) slutt++;
        return json.substring(start, slutt);
    }

    /** WebSocket-sesjon → PTY-enhet (f.eks. /dev/pts/5). */
    private final Map<String, String> ttyEnheter = new ConcurrentHashMap<>();

    /** Finner PTY-en til bash (barn av script) via /proc og endrer størrelsen. */
    private void endreStorrelse(String sesjonId, int cols, int rows) {
        String tty = ttyEnheter.get(sesjonId);
        if (tty == null) {
            tty = finnTty(sesjonId);
            if (tty == null) {
                logger.debug("Fant ikke PTY for sesjon {}", sesjonId);
                return;
            }
            ttyEnheter.put(sesjonId, tty);
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("stty", "-F", tty,
                    "rows", String.valueOf(rows), "cols", String.valueOf(cols));
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().readAllBytes(); // vent til ferdig
            p.waitFor();
            logger.debug("PTY {} satt til {}x{}", tty, cols, rows);
        } catch (Exception e) {
            logger.debug("Kunne ikke endre PTY-størrelse {}: {}", tty, e.getMessage());
        }
    }

    /** Leser /proc/<script-pid>/task/<pid>/children for å finne bash-barnet, deretter dets tty. */
    private String finnTty(String sesjonId) {
        Process prosess = prosesser.get(sesjonId);
        if (prosess == null) return null;
        long scriptPid = prosess.pid();
        try {
            // script sitt barn (bash) har PTY-en som sin terminal (fd 0)
            java.nio.file.Path childrenFil = java.nio.file.Path.of(
                    "/proc/" + scriptPid + "/task/" + scriptPid + "/children");
            String barn = java.nio.file.Files.readString(childrenFil).trim();
            if (barn.isEmpty()) return null;
            long bashPid = Long.parseLong(barn.split("\\s+")[0]);
            // bash sin fd 0 peker på PTY-en, f.eks. /dev/pts/5
            java.nio.file.Path tty = java.nio.file.Files.readSymbolicLink(
                    java.nio.file.Path.of("/proc/" + bashPid + "/fd/0"));
            return tty.toString();
        } catch (Exception e) {
            logger.debug("Kunne ikke finne PTY via /proc: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.debug("Terminal-transportfeil: {}", exception.getMessage());
        lukk(session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        lukk(session.getId());
    }

    private void lukk(String sesjonId) {
        Process prosess = prosesser.remove(sesjonId);
        if (prosess != null) {
            prosess.destroy();
        }
        Thread leser = lesere.remove(sesjonId);
        if (leser != null) {
            leser.interrupt();
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
