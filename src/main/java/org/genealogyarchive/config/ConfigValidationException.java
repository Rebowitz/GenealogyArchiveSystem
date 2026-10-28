package org.genealogyarchive.config;

import java.util.List;

/**
 * Installation configuration is missing, unreadable or invalid. Carries every problem found so an
 * administrator can fix them in one pass. Messages never include secret values.
 */
public class ConfigValidationException extends RuntimeException {

    private final List<String> problems;

    public ConfigValidationException(List<String> problems) {
        super("Installation configuration is invalid:\n  - " + String.join("\n  - ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
