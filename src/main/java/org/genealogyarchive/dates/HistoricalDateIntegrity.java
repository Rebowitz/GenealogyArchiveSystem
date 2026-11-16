package org.genealogyarchive.dates;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The envelope integrity scan (HD-6): recomputes each stored envelope from its interpretation and reports any
 * mismatch or invalid stored interpretation. It never repairs anything; a mismatch is an integrity finding to be
 * investigated (fail visibly).
 */
public final class HistoricalDateIntegrity {

    public record Finding(UUID historicalDateId, String problem) {
    }

    private HistoricalDateIntegrity() {
    }

    public static List<Finding> check(Iterable<StoredHistoricalDate> dates) {
        List<Finding> findings = new ArrayList<>();
        for (StoredHistoricalDate d : dates) {
            DateValidation.Result valid = DateValidation.validate(d.interpretation());
            if (!valid.valid()) {
                findings.add(new Finding(d.id(), "stored interpretation is not valid: " + valid.errors()));
                continue;
            }
            DateEnvelope expected = DateEnvelopes.of(d.interpretation());
            if (!expected.equals(d.envelope())) {
                findings.add(new Finding(d.id(), "stored envelope " + d.envelope() + " differs from recomputed "
                        + expected));
            }
        }
        return findings;
    }
}
