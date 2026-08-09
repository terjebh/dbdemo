package no.itfakultetet.dbdemo.config;

import no.itfakultetet.dbdemo.model.AppConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security-oppsett:
 * <ul>
 *   <li>Før first-run-oppsettet er gjennomført er ALLE sider åpne — da styrer
 *       {@link SetupInterceptor} og redirecter alt til /setup.</li>
 *   <li>Etter konfigurering: admin-brukeren leses fra {@link ConfigService}
 *       (opprettet i first-run-veiviseren), /setup og statiske ressurser er
 *       åpne, alt annet krever innlogging.</li>
 * </ul>
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
            if (config == null || !username.equals(config.getAdminUsername())
                    || config.getAdminPasswordHash() == null
                    || config.getAdminPasswordHash().isBlank()) {
                throw new UsernameNotFoundException("Ukjent bruker: " + username);
            }
            return User.withUsername(username)
                    .password(config.getAdminPasswordHash())
                    .roles("ADMIN")
                    .build();
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        boolean konfigurert = configService.isConfigured();

        http
            .authorizeHttpRequests(auth -> {
                if (!konfigurert) {
                    // Før oppsett: alt åpent, SetupInterceptor redirecter til /setup
                    auth.anyRequest().permitAll();
                } else {
                    auth
                        .requestMatchers("/setup", "/setup/**", "/css/**", "/js/**",
                                "/favicon.ico", "/error").permitAll()
                        .anyRequest().authenticated();
                }
            })
            .formLogin(form -> form
                .loginPage("/login").permitAll()
                .defaultSuccessUrl("/", true)
            )
            .logout(logout -> logout.logoutSuccessUrl("/login?logout"));
        return http.build();
    }
}
