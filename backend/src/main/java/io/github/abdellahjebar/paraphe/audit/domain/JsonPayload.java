package io.github.abdellahjebar.paraphe.audit.domain;

/**
 * Builds a flat JSON object of string values, with proper escaping.
 *
 * <p>Enough for audit payloads and keeps the domain free of a JSON library. Anything nested or
 * numeric belongs in a real JSON mapper at the edge.
 */
public final class JsonPayload {

    private JsonPayload() {
    }

    /** {@code JsonPayload.of("status", "VAO", "effect", "APPROVING_WITH_RESERVES")}. */
    public static String of(String... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("expected key/value pairs");
        }
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < keysAndValues.length; i += 2) {
            if (i > 0) {
                json.append(',');
            }
            json.append(quote(keysAndValues[i])).append(':').append(quote(keysAndValues[i + 1]));
        }
        return json.append('}').toString();
    }

    static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : String.valueOf(text).toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u%04x".formatted((int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
