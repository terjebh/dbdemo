package no.itfakultetet.dbdemo.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * First-run-veiviser: hvis appen ikke er konfigurert ennå, omdirigeres ALLE
 * forespørsler (utenom /setup, /login og statiske ressurser) til /setup.
 */
@Component
public class SetupInterceptor implements HandlerInterceptor {

    private final ConfigService configService;

    public SetupInterceptor(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (configService.isConfigured()) {
            return true;
        }
        String uri = request.getRequestURI();
        boolean åpen = uri.equals("/setup") || uri.startsWith("/setup/")
                || uri.equals("/login") || uri.startsWith("/css/")
                || uri.startsWith("/js/") || uri.equals("/favicon.ico")
                || uri.equals("/error");
        if (åpen) {
            return true;
        }
        response.sendRedirect("/setup");
        return false;
    }
}
