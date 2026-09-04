package no.itfakultetet.dbdemo.config;

import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.Map;

/**
 * Rydder opp ved session-utløp/logout: lukker alle åpne transaksjons-
 * tilkoblinger (uten COMMIT → implisitt ROLLBACK i databasen), slik at
 * ingen tilkobling blir hengende etter en bruker som forsvinner.
 */
@Component
@WebListener
public class SesjonsRydder implements HttpSessionListener {

    private static final Logger logger = LoggerFactory.getLogger(SesjonsRydder.class);

    /** Samme attributtnøkkel som QueryRestController bruker. */
    public static final String TX_ATTRIBUTT = "transaksjonTilkoblinger";

    @Override
    @SuppressWarnings("unchecked")
    public void sessionDestroyed(HttpSessionEvent se) {
        HttpSession session = se.getSession();
        Object verdi = session.getAttribute(TX_ATTRIBUTT);
        if (!(verdi instanceof Map<?, ?>)) return;
        Map<Integer, Connection> kart = (Map<Integer, Connection>) verdi;
        int lukket = 0;
        for (Connection c : kart.values()) {
            try {
                if (c != null && !c.isClosed()) {
                    // Uten eksplisitt COMMIT rulles transaksjonen tilbake
                    // når tilkoblingen lukkes — riktig oppførsel ved logout.
                    c.close();
                    lukket++;
                }
            } catch (Exception e) {
                logger.debug("Kunne ikke lukke transaksjonstilkobling: {}", e.getMessage());
            }
        }
        if (lukket > 0) {
            logger.info("Lukket {} åpne transaksjonstilkobling(er) ved session-slutt", lukket);
        }
        session.removeAttribute(TX_ATTRIBUTT);
    }
}
