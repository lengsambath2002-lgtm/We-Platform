package com.businessplatform.mail;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.util.HtmlUtils;

/**
 * One email in the Business Platform look (as VirtualBiz's): HTML with a table layout and inline styles, so mail apps
 * show it as meant, and the same words as plain text. Build it top to bottom: a badge, a heading, lines, a button,
 * small print, and a divider between the English and the Khmer version. Everything given is escaped.
 */
public final class Email {

    private static final String INK = "#141a2b";
    private static final String MUTED = "#5f6677";
    private static final String LINE = "#e6e8ee";
    private static final String BRAND = "#2f5bea";

    private final List<String> html = new ArrayList<>();
    private final List<String> text = new ArrayList<>();
    private String preheader = "";

    /** The grey line mail apps show after the subject. */
    public Email preheader(String s) {
        this.preheader = s == null ? "" : s;
        return this;
    }

    /** The organisation's (or the product's) mark and name above the heading. */
    public Email badge(String name) {
        html.add("<tr><td style=\"padding:0 0 16px\"><table role=\"presentation\"><tr><td><div style=\"width:44px;height:44px;"
                + "border-radius:12px;background:" + BRAND + ";color:#ffffff;font:700 20px/44px Arial,sans-serif;text-align:center\">"
                + esc(initial(name)) + "</div></td><td style=\"padding-left:12px;font:600 15px Arial,sans-serif;color:" + INK + "\">"
                + esc(name) + "</td></tr></table></td></tr>");
        return this;
    }

    public Email heading(String s) {
        html.add("<tr><td style=\"padding:0 0 12px;font:700 21px/1.35 Arial,'Noto Sans Khmer',sans-serif;color:" + INK + "\">"
                + esc(s) + "</td></tr>");
        text.add(s);
        text.add("");
        return this;
    }

    public Email line(String s) {
        if (s == null || s.isBlank()) {
            return this;
        }
        html.add("<tr><td style=\"padding:0 0 12px;font:15px/1.6 Arial,'Noto Sans Khmer',sans-serif;color:" + INK + "\">"
                + esc(s) + "</td></tr>");
        text.add(s);
        return this;
    }

    /** A large code (verification and sign-in codes). */
    public Email code(String code) {
        html.add("<tr><td style=\"padding:4px 0 16px;font:700 32px/1 'Courier New',monospace;letter-spacing:8px;color:" + INK
                + "\">" + esc(code) + "</td></tr>");
        text.add("");
        text.add(code);
        text.add("");
        return this;
    }

    public Email button(String label, String url) {
        if (label == null || url == null) {
            return this;
        }
        html.add("<tr><td style=\"padding:8px 0 20px\"><table role=\"presentation\"><tr><td style=\"border-radius:999px;background:"
                + INK + "\"><a href=\"" + esc(url) + "\" style=\"display:inline-block;padding:12px 26px;font:600 15px Arial,"
                + "'Noto Sans Khmer',sans-serif;color:#ffffff;text-decoration:none;border-radius:999px\">" + esc(label)
                + "</a></td></tr></table></td></tr>");
        text.add("");
        text.add(label + ": " + url);
        text.add("");
        return this;
    }

    public Email small(String s) {
        if (s == null || s.isBlank()) {
            return this;
        }
        html.add("<tr><td style=\"padding:0 0 8px;font:12.5px/1.5 Arial,'Noto Sans Khmer',sans-serif;color:" + MUTED + "\">"
                + esc(s) + "</td></tr>");
        text.add(s);
        return this;
    }

    /** A thin rule (between the English and Khmer versions). */
    public Email divider() {
        html.add("<tr><td style=\"padding:12px 0 20px\"><div style=\"border-top:1px solid " + LINE + "\"></div></td></tr>");
        text.add("");
        text.add("———");
        text.add("");
        return this;
    }

    public String text() {
        return String.join("\n", text) + "\n\n— " + MailService.PRODUCT + "\n";
    }

    public String html() {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">"
                + "</head><body style=\"margin:0;background:#f3f4f7\">"
                + "<div style=\"display:none;max-height:0;overflow:hidden;color:transparent\">" + esc(preheader) + "</div>"
                + "<table role=\"presentation\" width=\"100%\" style=\"background:#f3f4f7\"><tr><td align=\"center\" style=\"padding:28px 12px\">"
                + "<table role=\"presentation\" width=\"100%\" style=\"max-width:520px;background:#ffffff;border-radius:18px;"
                + "border:1px solid " + LINE + "\"><tr><td style=\"padding:28px 28px 16px\"><table role=\"presentation\" width=\"100%\">"
                + String.join("", html)
                + "</table></td></tr></table>"
                + "<p style=\"font:12px Arial,sans-serif;color:" + MUTED + ";margin:16px 0 0\">" + esc(MailService.PRODUCT)
                + " · Phnom Penh, Cambodia</p></td></tr></table></body></html>";
    }

    private static String initial(String name) {
        return name == null || name.isBlank() ? "B" : name.strip().substring(0, 1).toUpperCase();
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
    }
}
