package no.itfakultetet.dbdemo.config;

import no.itfakultetet.dbdemo.model.AppConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Spring Security-oppsett:
 * <ul>
 *   <li>Før first-run-oppsettet er gjennomført er ALLE sider åpne — da styrer
 *       {@link SetupInterceptor} og redirecter alt til /setup.</li>
 *   <li>Etter konfigurering:
 *       <ul>
 *         <li>Admin (opprettet i first-run-veiviseren): alt, inkl. /setup og /brukere</li>
 *         <li>Vanlige brukere (opprettet av admin): kun SQL-sidene (/select, /rest)</li>
 *       </ul>
 *   </li>
 * </ul>
 * <p>
 * Autorisasjonen evalueres PER REQUEST (AuthorizationManager) — ikke én gang
 * ved oppstart — slik at appen kan starte ukonfigurert (alt åpent) og deretter
 * stramme inn automatisk når konfigurasjonen lagres, uten restart.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ConfigService configService;

    public SecurityConfig(ConfigService configService) {
        this.configService = configService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            AppConfig config = configService.load();
            if (config == null) {
                throw new UsernameNotFoundException("Ingen konfigurasjon: " + username);
            }
            // Admin-brukeren fra first-run-oppsettet
            if (username.equals(config.getAdminUsername())
                    && config.getAdminPasswordHash() != null
                    && !config.getAdminPasswordHash().isBlank()) {
                return User.withUsername(username)
                        .password(config.getAdminPasswordHash())
                        .roles("ADMIN")
                        .build();
            }
            // Vanlige brukere opprettet av admin
            String hash = config.getUsers().get(username);
            if (hash != null && !hash.isBlank()) {
                return User.withUsername(username)
                        .password(hash)
                        .roles("USER")
                        .build();
            }
            throw new UsernameNotFoundException("Ukjent bruker: " + username);
        };
    }

    /** Åpent før oppsett; krever admin etterpå. */
    private AuthorizationManager<RequestAuthorizationContext> adminEllerFørOppsett() {
        return (authentication, ctx) -> {
            if (!configService.isConfigured()) {
                return new AuthorizationDecision(true);
            }
            return new AuthorizationDecision(erInnlogget(authentication.get())
                    && authentication.get().getAuthorities().stream()
                        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
        };
    }

    /** Åpent før oppsett; krever innlogging etterpå. */
    private AuthorizationManager<RequestAuthorizationContext> innloggetEllerFørOppsett() {
        return (authentication, ctx) -> {
            if (!configService.isConfigured()) {
                return new AuthorizationDecision(true);
            }
            return new AuthorizationDecision(erInnlogget(authentication.get()));
        };
    }

    /**
     * Ekte innlogging? Merk: AnonymousAuthenticationToken har
     * isAuthenticated() == true, men er IKKE en reell bruker — den må
     * behandles som uinnlogget.
     */
    private boolean erInnlogget(org.springframework.security.core.Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Admin-områder: oppsett/tilkoblinger og brukeradministrasjon
                .requestMatchers("/setup", "/setup/**", "/brukere", "/brukere/**")
                    .access(adminEllerFørOppsett())
                // Statiske ressurser er åpne
                .requestMatchers("/css/**", "/js/**", "/vendor/**",
                        "/favicon.ico", "/error").permitAll()
                // Alt annet (SQL-sidene, REST, hjem) krever innlogging
                .anyRequest().access(innloggetEllerFørOppsett())
            )
            .formLogin(form -> form
                .loginPage("/login").permitAll()
                .defaultSuccessUrl("/", true)
            )
            .logout(logout -> logout.logoutSuccessUrl("/login?logout"));
        return http.build();
    }
}
