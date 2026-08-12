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
        // HOME kan være /root i containeren — brukerens hjemmemappe gir
        // ingen «Permission denied»-klage ved bash-start
        pb.environment().put("HOME", "/home/appuser");
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
        // Klient-input → prosessens stdin (terminatortastene sendes som escape-sekvenser)
        String tekst = message.getPayload().toString();
        OutputStream ut = prosess.getOutputStream();
        ut.write(tekst.getBytes(StandardCharsets.UTF_8));
        ut.flush();
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
