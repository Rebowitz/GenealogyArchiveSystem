package org.genealogyarchive.history;

import java.util.List;

/** Minimal, dependency-free JSON writing for the Change History field list. */
final class JsonText {

    private JsonText() {
    }

    static String fieldChanges(List<FieldChange> changes) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < changes.size(); i++) {
            FieldChange c = changes.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"field\":").append(string(c.field()))
                    .append(",\"class\":").append(string(c.fieldClass().name()))
                    .append(",\"before\":").append(string(c.before()))
                    .append(",\"after\":").append(string(c.after()))
                    .append('}');
        }
        return sb.append(']').toString();
    }

    static String string(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
