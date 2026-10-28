package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Messages;

/** Every English message in the code has a Khmer line in {@code i18n/messages_km.txt}, so none ships untranslated. */
class MessageCoverageTest {

    private static final String STR = "\"((?:[^\"\\\\]|\\\\.)*)\"";
    private static final String ARG = "[^,()\"]+";
    /** An argument that is not the message (a field name, maybe a literal); not captured. */
    private static final String SKIP = "(?:\"(?:[^\"\\\\]|\\\\.)*\"|" + ARG + ")";

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    /** Where English messages are written; each captures the message literal. Calls may wrap lines. */
    private static final List<Pattern> SITES = Stream.of(
            "BusinessException\\.field\\(\\s*" + ARG + ",\\s*" + SKIP + ",\\s*" + STR,
            "new\\s+BusinessException\\(\\s*" + ARG + ",\\s*" + STR,
            "new\\s+ApiError\\.FieldError\\(\\s*" + SKIP + ",\\s*" + STR,
            "\\bmessage\\s*=\\s*" + STR,
            "\\brespond\\(\\s*ErrorCode\\.\\w+,\\s*" + STR,
            "\\bmessages\\.text\\(\\s*" + STR,
            "\\bmessages\\.in\\(\\s*\\w+,\\s*" + STR,
            "\\bmessages\\.text\\(\\s*\\w+\\s*\\?\\s*" + STR + "\\s*:\\s*" + STR,
            "\\bText\\.of\\(\\s*" + STR)
            .map(p -> Pattern.compile(p, Pattern.DOTALL)).toList();

    @Test
    void every_english_message_has_a_khmer_translation() throws IOException {
        Set<String> found = english();
        assertThat(found).as("the scan still finds messages")
                .contains("This address is taken",
                        "Use 10 or more characters with letters and numbers, not your email name", "A subtask belongs to a top-level task of the same project",
                        "2–10 capital letters or digits, starting with a letter", "Required", "{min}–{max} characters",
                        "At most {max} items", "Validation failed", "Accept the invitation", "Approved: {title}",
                        "Moved to {status}")
                .hasSizeGreaterThan(50);
        Messages messages = new Messages();
        List<String> missing = found.stream().filter(m -> !messages.has("km", m)).toList();
        assertThat(missing).as("English messages without a line in i18n/messages_km.txt").isEmpty();
    }

    @Test
    void every_error_code_has_a_khmer_translation() {
        Messages messages = new Messages();
        assertThat(Arrays.stream(ErrorCode.values()).map(Enum::name).filter(c -> !messages.has("km", c)).toList())
                .isEmpty();
    }

    @Test
    void placeholders_survive_translation_and_kh_means_khmer() {
        Messages messages = new Messages();
        assertThat(messages.in("km", "{min}–{max} characters", Map.of("min", 2, "max", 80)))
                .isEqualTo("ពី 2 ដល់ 80 តួអក្សរ");
        assertThat(messages.in("en", "{min}–{max} characters", Map.of("min", 2, "max", 80)))
                .isEqualTo("2–80 characters");
        assertThat(messages.in("km", "Some text nobody translated", Map.of())).isEqualTo("Some text nobody translated");
        assertThat(Messages.language(Messages.fromHeader("kh"))).isEqualTo("km");
        assertThat(Messages.language(Messages.fromHeader("km-KH,en;q=0.5"))).isEqualTo("km");
        assertThat(Messages.language(Messages.fromHeader("fr,en;q=0.8,km;q=0.5"))).isEqualTo("en");
        assertThat(Messages.language(Messages.fromHeader("not a header;;"))).isEqualTo("en");
        assertThat(Messages.language(Messages.fromHeader(null))).isEqualTo("en");
    }

    private static Set<String> english() throws IOException {
        Set<String> out = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                // Javadoc and block comments may quote code; they are not messages.
                String src = BLOCK_COMMENT.matcher(Files.readString(f, StandardCharsets.UTF_8)).replaceAll(" ");
                for (Pattern p : SITES) {
                    Matcher m = p.matcher(src);
                    while (m.find()) {
                        for (int g = 1; g <= m.groupCount(); g++) {
                            out.add(unescape(m.group(g)));
                        }
                    }
                }
            }
        }
        return out;
    }

    private static String unescape(String literal) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < literal.length(); i++) {
            char c = literal.charAt(i);
            if (c != '\\' || i + 1 == literal.length()) {
                b.append(c);
                continue;
            }
            char n = literal.charAt(++i);
            switch (n) {
                case 'n' -> b.append('\n');
                case 't' -> b.append('\t');
                case 'u' -> {
                    b.append((char) Integer.parseInt(literal.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> b.append(n);
            }
        }
        return b.toString();
    }
}
