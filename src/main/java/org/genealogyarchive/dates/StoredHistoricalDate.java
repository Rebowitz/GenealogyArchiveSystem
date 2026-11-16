package org.genealogyarchive.dates;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A Historical Date as stored (ADR-0012 s.2): the source expression (the authority), an optional confirmed
 * interpretation, and its derived envelope. {@code interpretedExpression} is the expression as it stood when the
 * interpretation was confirmed; when the expression is later corrected the two differ, and the interpretation is
 * shown as "confirmed against earlier wording" until it is confirmed again (clarifications s.2.5).
 */
public record StoredHistoricalDate(UUID id, String expression, String interpretedExpression,
                                   DateInterpretation interpretation, InterpretationOrigin origin, UUID confirmedBy,
                                   Instant confirmedAt, DateEnvelope envelope, Instant createdAt, UUID createdBy,
                                   long version) {

    /** A new date holding only its expression (NOT_INTERPRETED). Always valid. */
    public static StoredHistoricalDate uninterpreted(UUID id, String expression, Instant now, UUID by) {
        return new StoredHistoricalDate(id, requireExpression(expression), null, DateInterpretation.NOT_INTERPRETED,
                null, null, null, DateEnvelope.NONE, now, by, 1);
    }

    /**
     * A new date with a confirmed interpretation and its computed envelope.
     *
     * @throws IllegalArgumentException if the interpretation is invalid or NOT_INTERPRETED
     */
    public static StoredHistoricalDate confirmed(UUID id, String expression, DateInterpretation interpretation,
                                                 InterpretationOrigin origin, Instant now, UUID by) {
        Objects.requireNonNull(origin, "origin");
        if (interpretation.form() == DateForm.NOT_INTERPRETED) {
            throw new IllegalArgumentException("A confirmed interpretation cannot be NOT_INTERPRETED");
        }
        String e = requireExpression(expression);
        return new StoredHistoricalDate(id, e, e, interpretation, origin, by, now, DateEnvelopes.of(interpretation),
                now, by, 1);
    }

    /** True when the expression was corrected after the interpretation was confirmed. */
    public boolean interpretationStale() {
        return interpretedExpression != null && !interpretedExpression.equals(expression);
    }

    private static String requireExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("A Historical Date needs its expression as written");
        }
        if (expression.strip().length() > 500) {
            throw new IllegalArgumentException("The date as written is limited to 500 characters");
        }
        return expression;
    }
}
