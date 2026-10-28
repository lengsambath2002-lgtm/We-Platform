package com.businessplatform.common.i18n;

import java.util.Map;

/**
 * Text to show someone later, in their language: an English template (the key into
 * {@code messages_km.txt}) and its {@code {name}} arguments. An argument may itself be a {@code Text}
 * (e.g. a status name) and is translated too; {@link #raw} wraps user content that is never translated.
 */
public record Text(String english, Map<String, ?> args, boolean translate) {

    public static Text of(String english) {
        return new Text(english, Map.of(), true);
    }

    public static Text of(String english, Map<String, ?> args) {
        return new Text(english, args, true);
    }

    /** User content (a task title, a comment): shown as it is. Null stays null. */
    public static Text raw(String value) {
        return value == null ? null : new Text(value, Map.of(), false);
    }
}
