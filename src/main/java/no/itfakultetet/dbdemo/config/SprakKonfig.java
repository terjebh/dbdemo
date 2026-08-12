package no.itfakultetet.dbdemo.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Internasjonalisering (i18n):
 * <ul>
 *   <li>{@link MessageSource} leser {@code messages.properties} (norsk,
 *       default) + {@code messages_XX.properties} for andre språk.</li>
 *   <li>{@link CookieLocaleResolver} husker språkvalget i en cookie
 *       («lang») — samme cookie som flagg-knappen i JS skriver.</li>
 *   <li>{@link LocaleChangeInterceptor} støtter {@code ?lang=en} som
 *       alternativ måte å bytte språk på.</li>
 * </ul>
 * Oversettelser skjer i {@code src/main/resources/messages*.properties} —
 * formatet er Java-properties, som Weblate forstår nativt (SUSE-modellen).
 */
@Configuration
public class SprakKonfig implements WebMvcConfigurer {

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setDefaultLocale(new Locale("no"));
        return source;
    }

    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver();
        resolver.setCookieName("lang");
        resolver.setDefaultLocale(new Locale("no"));
        resolver.setCookieMaxAge(60 * 60 * 24 * 365); // 1 år
        return resolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
