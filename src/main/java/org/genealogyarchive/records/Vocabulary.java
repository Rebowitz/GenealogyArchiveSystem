package org.genealogyarchive.records;

/** The Phase 3 controlled vocabularies (archive configuration, D3-08). */
public enum Vocabulary {
    /** Record classification, e.g. DEED, DTRUST. */
    CLASSIFICATION("classification"),
    /** Record Role type, e.g. GRANTOR, WITNESS. */
    ROLE_TYPE("record_role");

    private final String historyPrefix;

    Vocabulary(String historyPrefix) {
        this.historyPrefix = historyPrefix;
    }

    /** Prefix of the Change History field names that record changes to this vocabulary's terms. */
    public String historyPrefix() {
        return historyPrefix;
    }
}
