package no.itfakultetet.dbdemo.config;

import no.itfakultetet.dbdemo.controller.TerminalWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Registrerer terminalens WebSocket-endepunkt ({@code /ws/terminal}) og
 * kopierer den innloggede brukeren inn i sesjons-attributtene, slik at
 * {@link TerminalWebSocketHandler} vet hvem som kjører shell-en.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TerminalWebSocketHandler terminalHandler;

    public WebSocketConfig(TerminalWebSocketHandler terminalHandler) {
        this.terminalHandler = terminalHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(terminalHandler, "/ws/terminal")
                .setAllowedOrigins("*")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request,
                                                   ServerHttpResponse response,
                                                   WebSocketHandler wsHandler,
                                                   Map<String, Object> attributes) {
                        // Krever innlogging: hvis ingen Authentication finnes → avvis
                        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                        if (auth == null || !auth.isAuthenticated()
                                || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
                            return false;
                        }
                        attributes.put("authentication", auth);
                        return true;
                    }

                    @Override
                    public void afterHandshake(ServerHttpRequest request,
                                               ServerHttpResponse response,
                                               WebSocketHandler wsHandler,
                                               Exception exception) {
                        // ingenting å gjøre
                    }
                })
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override
                    protected Principal determineUser(ServerHttpRequest request,
                                                      WebSocketHandler wsHandler,
                                                      Map<String, Object> attributes) {
                        Authentication auth = (Authentication) attributes.get("authentication");
                        return auth;
                    }
                });
    }
}
