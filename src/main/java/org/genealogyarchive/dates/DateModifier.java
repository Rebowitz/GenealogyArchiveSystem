package org.genealogyarchive.dates;

/** How a point is qualified. Modifiers never move the stored envelope (D4-05); search treats them separately. */
public enum DateModifier {
    EXACT,
    ABOUT,
    ESTIMATED,
    CALCULATED;

    /** True for ABOUT, ESTIMATED and CALCULATED. */
    public boolean approximate() {
        return this != EXACT;
    }
}
