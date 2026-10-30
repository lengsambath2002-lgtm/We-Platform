package com.businessplatform.billing.khqr;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Copied from VirtualBiz (payment/khqr/KhqrBuilder, unchanged there). Builds a dynamic KHQR string (Bakong's EMVCo merchant-presented QR profile) for an individual
 * Bakong account: payload format (00), dynamic point of initiation (01), Bakong account (29/00),
 * MCC (52), currency (53: 840 USD, 116 KHR), amount (54), country (58), merchant name (59) and
 * city (60), bill number / store / terminal (62), creation and expiry timestamps (99) and the
 * CRC-16/CCITT-FALSE checksum (63). Pure; {@link #md5(String)} is what Bakong's
 * "check transaction by MD5" API takes.
 */
public final class KhqrBuilder {

    public static final String USD = "840";
    public static final String KHR = "116";

    /** What goes in the QR. Name ≤ 25 and city ≤ 15 characters, ASCII. */
    public record Request(String bakongAccountId, String merchantName, String merchantCity, String currency,
            BigDecimal amount, String billNumber, String storeLabel, String terminalLabel,
            Instant createdAt, Instant expiresAt) {
    }

    private KhqrBuilder() {
    }

    public static String build(Request r) {
        StringBuilder sb = new StringBuilder();
        sb.append(tlv("00", "01"));
        sb.append(tlv("01", "12"));
        sb.append(tlv("29", tlv("00", require(r.bakongAccountId(), 32, "bakongAccountId"))));
        sb.append(tlv("52", "5999"));
        sb.append(tlv("53", r.currency()));
        sb.append(tlv("54", amount(r.amount(), r.currency())));
        sb.append(tlv("58", "KH"));
        sb.append(tlv("59", ascii(r.merchantName(), 25)));
        sb.append(tlv("60", ascii(r.merchantCity(), 15)));
        StringBuilder add = new StringBuilder();
        if (r.billNumber() != null && !r.billNumber().isBlank()) {
            add.append(tlv("01", ascii(r.billNumber(), 25)));
        }
        if (r.storeLabel() != null && !r.storeLabel().isBlank()) {
            add.append(tlv("03", ascii(r.storeLabel(), 25)));
        }
        if (r.terminalLabel() != null && !r.terminalLabel().isBlank()) {
            add.append(tlv("07", ascii(r.terminalLabel(), 25)));
        }
        if (!add.isEmpty()) {
            sb.append(tlv("62", add.toString()));
        }
        String ts = tlv("00", Long.toString(r.createdAt().toEpochMilli()))
                + (r.expiresAt() == null ? "" : tlv("01", Long.toString(r.expiresAt().toEpochMilli())));
        sb.append(tlv("99", ts));
        sb.append("6304");
        sb.append(crc16(sb.toString()));
        return sb.toString();
    }

    /** CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF), 4 upper-case hex digits. */
    public static String crc16(String data) {
        int crc = 0xFFFF;
        for (byte b : data.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }

    /** True when the trailing CRC matches the rest of the string. */
    public static boolean crcValid(String khqr) {
        if (khqr == null || khqr.length() < 8 || !khqr.startsWith("6304", khqr.length() - 8)) {
            return false;
        }
        String body = khqr.substring(0, khqr.length() - 4);
        return crc16(body).equals(khqr.substring(khqr.length() - 4));
    }

    public static String md5(String khqr) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                    .digest(khqr.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 unavailable", ex);
        }
    }

    static String tlv(String tag, String value) {
        if (value.length() > 99) {
            throw new IllegalArgumentException("KHQR field " + tag + " longer than 99");
        }
        return tag + String.format("%02d", value.length()) + value;
    }

    /** USD with 2 decimals ("12.50"); riel as a whole number. */
    static String amount(BigDecimal amount, String currency) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("KHQR amount must be positive");
        }
        return KHR.equals(currency)
                ? amount.setScale(0, RoundingMode.HALF_UP).toPlainString()
                : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String require(String v, int max, String name) {
        if (v == null || v.isBlank() || v.length() > max) {
            throw new IllegalArgumentException("KHQR " + name + " missing or too long");
        }
        return v;
    }

    private static String ascii(String v, int max) {
        String s = v == null ? "" : v.replaceAll("[^\\x20-\\x7E]", "").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}
