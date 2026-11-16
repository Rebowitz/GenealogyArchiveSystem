package org.genealogyarchive.dates;

/**
 * The derived calendar-unit envelope (D4-05; clarifications s.2.3): the smallest single interval of days, as Julian
 * Day Numbers, containing every day the interpretation can denote under every calendar and year-start reading it
 * leaves open. Null bounds are open ends; both null means no envelope (UNKNOWN, NOT_INTERPRETED).
 *
 * <p>It is not the date, not a probability, not a set of possible days (it is a convex hull) and not
 * authoritative. Modifiers and "?" never move it; they set {@code approximate} and {@code uncertain}.
 */
public record DateEnvelope(Long earliestJdn, Long latestJdn, boolean approximate, boolean uncertain) {

    public static final DateEnvelope NONE = new DateEnvelope(null, null, false, false);

    public boolean isNone() {
        return earliestJdn == null && latestJdn == null;
    }
}
