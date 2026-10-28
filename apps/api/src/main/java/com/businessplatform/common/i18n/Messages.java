package com.businessplatform.common.i18n;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Gettext-style translation of API messages: the English text written in the code is the key, and
 * {@code i18n/messages_<lang>.txt} (UTF-8, one {@code English text = translation} per line, {@code #}
 * comments) holds the translation. Unknown text falls back to the English. {@code {name}} placeholders
 * are filled in after translating.
 */
@Component
public class Messages {

    /** The default language. */
    public static final Locale ENGLISH = Locale.ENGLISH;
    /** Khmer; clients may also send the common (non-ISO) {@code kh}. */
    public static final Locale KHMER = Locale.forLanguageTag("km");

    static final List<String> TRANSLATED = List.of("km");
    static final String SEPARATOR = " = ";

    private final Map<String, Map<String, String>> byLanguage = new HashMap<>();

    public Messages() {
        for (String lang : TRANSLATED) {
            byLanguage.put(lang, load("i18n/messages_" + lang + ".txt"));
        }
    }

    /** {@code english} in the request's language. */
    public String text(String english) {
        return text(english, Map.of());
    }

    /** {@code english} in the request's language, with {@code {name}} placeholders filled from {@code args}. */
    public String text(String english, Map<String, ?> args) {
        return in(requestLanguage(), english, args);
    }

    /** {@code english} in {@code lang} ({@code en} or {@code km}), placeholders filled from {@code args}. */
    public String in(String lang, String english, Map<String, ?> args) {
        if (english == null) {
            return null;
        }
        String out = byLanguage.getOrDefault(lang, Map.of()).getOrDefault(english, english);
        for (Map.Entry<String, ?> a : args.entrySet()) {
            out = out.replace("{" + a.getKey() + "}", String.valueOf(a.getValue()));
        }
        return out;
    }

    /** {@code text} in {@code lang}; arguments that are themselves {@link Text} are translated too. */
    public String in(String lang, Text text) {
        if (text == null) {
            return null;
        }
        if (!text.translate()) {
            return text.english();
        }
        Map<String, Object> args = new HashMap<>();
        text.args().forEach((k, v) -> args.put(k, v instanceof Text t ? in(lang, t) : v));
        return in(lang, text.english(), args);
    }

    /** Whether {@code english} has a translation in {@code lang}. */
    public boolean has(String lang, String english) {
        return byLanguage.getOrDefault(lang, Map.of()).containsKey(english);
    }

    /** {@code km} or {@code en} for the current request (Spring sets it from {@code Accept-Language}). */
    public static String requestLanguage() {
        return language(LocaleContextHolder.getLocale());
    }

    /** {@code km} for Khmer ({@code km} or {@code kh}), otherwise {@code en}. */
    public static String language(Locale locale) {
        String lang = locale == null ? "" : locale.getLanguage();
        return "km".equals(lang) || "kh".equals(lang) ? "km" : "en";
    }

    /** The first supported language in an {@code Accept-Language} header, by weight; English otherwise. */
    public static Locale fromHeader(String header) {
        if (header == null || header.isBlank()) {
            return ENGLISH;
        }
        try {
            for (Locale.LanguageRange r : Locale.LanguageRange.parse(header)) {
                if (r.getWeight() <= 0) {
                    continue;
                }
                String lang = r.getRange().split("-", 2)[0].toLowerCase(Locale.ROOT);
                if ("km".equals(lang) || "kh".equals(lang)) {
                    return KHMER;
                }
                if ("en".equals(lang)) {
                    return ENGLISH;
                }
            }
        } catch (IllegalArgumentException malformed) {
            // A broken header is not worth refusing the request over.
        }
        return ENGLISH;
    }

    static Map<String, String> load(String resource) {
        Map<String, String> out = new HashMap<>();
        InputStream in = Messages.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Missing " + resource);
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int n = 0;
            while ((line = r.readLine()) != null) {
                n++;
                String l = line.strip();
                if (l.isEmpty() || l.startsWith("#")) {
                    continue;
                }
                int at = l.indexOf(SEPARATOR);
                if (at <= 0) {
                    throw new IllegalStateException(resource + ":" + n + ": expected 'English = translation'");
                }
                String key = l.substring(0, at).strip();
                if (out.put(key, l.substring(at + SEPARATOR.length()).strip()) != null) {
                    throw new IllegalStateException(resource + ":" + n + ": duplicate '" + key + "'");
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + resource, e);
        }
        return out;
    }
}
