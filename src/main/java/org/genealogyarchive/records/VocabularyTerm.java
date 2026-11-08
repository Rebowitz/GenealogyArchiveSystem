package org.genealogyarchive.records;

/** A vocabulary term: a permanent code, a correctable label and description, and a retired flag. */
public record VocabularyTerm(Vocabulary vocabulary, String code, String label, String description, boolean retired,
                             long version) {
}
