package com.businessplatform.common.i18n;

import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** The request language comes from {@code Accept-Language}: {@code en} or {@code km} ({@code kh} too), else en. */
@Configuration
public class LocaleConfig {

    @Bean(name = DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
    LocaleResolver localeResolver() {
        return new LocaleResolver() {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                return Messages.fromHeader(request.getHeader("Accept-Language"));
            }

            @Override
            public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
                throw new UnsupportedOperationException("The language follows the Accept-Language header");
            }
        };
    }
}
