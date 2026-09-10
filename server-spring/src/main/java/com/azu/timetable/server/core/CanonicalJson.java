package com.azu.timetable.server.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class CanonicalJson {

    private CanonicalJson() {
    }

    public static String canonical(Object root) {
        return write(root, 1).replace("\n", "\r\n");
    }

    public static String version(Object root) {
        return sha256(canonical(root));
    }

    public static String versionOf(String canonicalText) {
        return sha256(canonicalText);
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String write(Object node, int level) {
        if (node == null) {
            return "null";
        }
        if (node instanceof Boolean value) {
            return value ? "true" : "false";
        }
        if (node instanceof String value) {
            return JsonCodec.quote(value);
        }
        if (node instanceof Number value) {
            return number(value);
        }
        if (node instanceof List<?> value) {
            return array(value, level);
        }
        if (node instanceof Map<?, ?> value) {
            return object(value, level);
        }
        return JsonCodec.quote(String.valueOf(node));
    }

    private static String object(Map<?, ?> node, int level) {
        if (node.isEmpty()) {
            return "{}";
        }
        List<String> keys = new ArrayList<>();
        for (Object key : node.keySet()) {
            keys.add(String.valueOf(key));
        }
        Collections.sort(keys);
        StringBuilder out = new StringBuilder("{\n");
        String pad = "  ".repeat(level);
        String close = "  ".repeat(level - 1);
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            out.append(pad).append(JsonCodec.quote(key)).append(": ")
                    .append(write(node.get(key), level + 1));
            if (i < keys.size() - 1) {
                out.append(',');
            }
            out.append('\n');
        }
        return out.append(close).append('}').toString();
    }

    private static String array(List<?> node, int level) {
        if (node.isEmpty()) {
            return "[]";
        }
        StringBuilder out = new StringBuilder("[\n");
        String pad = "  ".repeat(level);
        String close = "  ".repeat(level - 1);
        for (int i = 0; i < node.size(); i++) {
            out.append(pad).append(write(node.get(i), level + 1));
            if (i < node.size() - 1) {
                out.append(',');
            }
            out.append('\n');
        }
        return out.append(close).append(']').toString();
    }

    private static String number(Number value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short
                || value instanceof Byte || value instanceof BigInteger) {
            return value.toString();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return Double.toString(value.doubleValue());
    }
}