package com.azu.timetable.server.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonCodec {

    private JsonCodec() {
    }

    public static Object parse(String text) {
        return new Reader(text).readDocument();
    }

    public static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    public static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), deepCopy(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        return value;
    }

    private static final class Reader {

        private final String text;
        private int position;

        Reader(String text) {
            this.text = text;
        }

        Object readDocument() {
            Object value = readValue();
            skipWhitespace();
            if (position != text.length()) {
                throw new IllegalStateException("Trailing characters after JSON document");
            }
            return value;
        }

        private Object readValue() {
            skipWhitespace();
            if (position >= text.length()) {
                throw new IllegalStateException("Unexpected end of JSON input");
            }
            char token = text.charAt(position);
            return switch (token) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> expectWord("true", Boolean.TRUE);
                case 'f' -> expectWord("false", Boolean.FALSE);
                case 'n' -> expectWord("null", null);
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            position++;
            skipWhitespace();
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            if (consume('}')) {
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                result.put(key, readValue());
                skipWhitespace();
                if (consume('}')) {
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> readArray() {
            position++;
            skipWhitespace();
            List<Object> result = new ArrayList<>();
            if (consume(']')) {
                return result;
            }
            while (true) {
                result.add(readValue());
                skipWhitespace();
                if (consume(']')) {
                    return result;
                }
                expect(',');
            }
        }

        private String readString() {
            position++;
            StringBuilder out = new StringBuilder();
            while (position < text.length()) {
                char c = text.charAt(position++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (position >= text.length()) {
                    throw new IllegalStateException("Unterminated escape sequence");
                }
                char escape = text.charAt(position++);
                switch (escape) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> out.append(readHexEscape());
                    default -> throw new IllegalStateException("Invalid escape sequence \\" + escape);
                }
            }
            throw new IllegalStateException("Unterminated string literal");
        }

        private char readHexEscape() {
            if (position + 4 > text.length()) {
                throw new IllegalStateException("Incomplete unicode escape");
            }
            String hex = text.substring(position, position + 4);
            position += 4;
            return (char) Integer.parseInt(hex, 16);
        }

        private Object readNumber() {
            int start = position;
            if (consume('-')) {
                //
            }
            while (position < text.length() && isDigit(text.charAt(position))) {
                position++;
            }
            boolean floating = false;
            if (consume('.')) {
                floating = true;
                while (position < text.length() && isDigit(text.charAt(position))) {
                    position++;
                }
            }
            char current = position < text.length() ? text.charAt(position) : '\0';
            if (current == 'e' || current == 'E') {
                floating = true;
                position++;
                if (position < text.length() && (text.charAt(position) == '+' || text.charAt(position) == '-')) {
                    position++;
                }
                while (position < text.length() && isDigit(text.charAt(position))) {
                    position++;
                }
            }
            String digits = text.substring(start, position);
            if (digits.isEmpty() || digits.equals("-")) {
                throw new IllegalStateException("Invalid number literal");
            }
            if (floating) {
                return Double.parseDouble(digits);
            }
            return Long.parseLong(digits);
        }

        private Object expectWord(String word, Object result) {
            if (!text.startsWith(word, position)) {
                throw new IllegalStateException("Invalid literal at position " + position);
            }
            position += word.length();
            return result;
        }

        private void expect(char expected) {
            if (position >= text.length() || text.charAt(position) != expected) {
                throw new IllegalStateException("Expected '" + expected + "' at position " + position);
            }
            position++;
        }

        private boolean consume(char expected) {
            if (position < text.length() && text.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (position < text.length()) {
                char c = text.charAt(position);
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                    return;
                }
                position++;
            }
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }
    }
}