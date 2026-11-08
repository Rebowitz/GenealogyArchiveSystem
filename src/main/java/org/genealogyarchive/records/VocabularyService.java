package org.genealogyarchive.records;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.archive.ArchiveService;
import org.genealogyarchive.history.ChangeKinds;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.history.FieldClass;
import org.genealogyarchive.service.ConflictException;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Record classifications and Role types (archive configuration, BUILD-SPEC s.25; D3-08). Codes are permanent:
 * an Administrator adds, relabels and retires terms, never deletes them. A retired term stays valid wherever it
 * is already used but is not offered for new use. Changes are recorded in the Archive's Change History.
 */
public final class VocabularyService {

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");

    private final RecordsContext ctx;

    public VocabularyService(RecordsContext ctx) {
        this.ctx = ctx;
    }

    public List<VocabularyTerm> terms(Actor actor, Vocabulary vocabulary) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        return ctx.vocabularies().list(vocabulary);
    }

    /** Terms offered for new use (not retired). */
    public List<VocabularyTerm> active(Actor actor, Vocabulary vocabulary) {
        return terms(actor, vocabulary).stream().filter(t -> !t.retired()).toList();
    }

    public Map<String, String> labels(Actor actor, Vocabulary vocabulary) {
        return terms(actor, vocabulary).stream().collect(Collectors.toMap(VocabularyTerm::code, VocabularyTerm::label));
    }

    /** Normalizes a typed code: upper case, spaces and hyphens become underscores. */
    public static String normalizeCode(String text) {
        if (text == null) {
            return null;
        }
        String s = text.strip().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return s.isEmpty() ? null : s;
    }

    public VocabularyTerm add(Actor actor, Vocabulary vocabulary, String code, String label, String description) {
        AccessPolicy.require(actor, Permission.MANAGE_VOCABULARIES);
        String c = normalizeCode(code);
        if (c == null || !CODE.matcher(c).matches()) {
            throw new RuleViolationException("A code is 1 to 32 letters, digits or underscores, starting with a letter.");
        }
        String l = Texts.required(label, "The label", 100);
        String d = Texts.optional(description, "The description", 2000, true);
        return ctx.tx().execute(status -> {
            if (!ctx.vocabularies().insert(vocabulary, c, l, d)) {
                throw new RuleViolationException("The code " + c + " already exists (codes are never reused).");
            }
            record(actor, vocabulary, c, ChangeKinds.CREATED, List.of(
                    FieldChange.set(field(vocabulary, c, "label"), FieldClass.CORRECTABLE, l),
                    FieldChange.set(field(vocabulary, c, "description"), FieldClass.CORRECTABLE, d)));
            return ctx.vocabularies().find(vocabulary, c).orElseThrow();
        });
    }

    public VocabularyTerm correct(Actor actor, Vocabulary vocabulary, String code, long expectedVersion, String label,
                                  String description, boolean retired) {
        AccessPolicy.require(actor, Permission.MANAGE_VOCABULARIES);
        String l = Texts.required(label, "The label", 100);
        String d = Texts.optional(description, "The description", 2000, true);
        return ctx.tx().execute(status -> {
            VocabularyTerm current = ctx.vocabularies().find(vocabulary, code)
                    .orElseThrow(() -> new NotFoundException("The term"));
            if (current.version() != expectedVersion) {
                throw new ConflictException("The term");
            }
            List<FieldChange> changes = List.of(
                    FieldChange.of(field(vocabulary, code, "label"), FieldClass.CORRECTABLE, current.label(), l),
                    FieldChange.of(field(vocabulary, code, "description"), FieldClass.CORRECTABLE, current.description(), d),
                    FieldChange.of(field(vocabulary, code, "retired"), FieldClass.CORRECTABLE, current.retired(), retired));
            if (changes.stream().noneMatch(FieldChange::changed)) {
                return current;
            }
            if (!ctx.vocabularies().update(vocabulary, code, expectedVersion, l, d, retired)) {
                throw new ConflictException("The term");
            }
            record(actor, vocabulary, code, retired && !current.retired() ? ChangeKinds.RETIRED : ChangeKinds.CORRECTED, changes);
            return ctx.vocabularies().find(vocabulary, code).orElseThrow();
        });
    }

    /**
     * Checks a code chosen for a new or changed value: it must exist, and - unless it is the value already held -
     * must not be retired. Returns the normalized code, or null for "none".
     */
    String requireUsable(Vocabulary vocabulary, String code, String current) {
        String c = normalizeCode(code);
        if (c == null) {
            return null;
        }
        Optional<VocabularyTerm> term = ctx.vocabularies().find(vocabulary, c);
        if (term.isEmpty()) {
            throw new RuleViolationException("Unknown " + (vocabulary == Vocabulary.CLASSIFICATION ? "classification" : "role")
                    + " " + c + ".");
        }
        if (term.get().retired() && !c.equals(current)) {
            throw new RuleViolationException("The " + (vocabulary == Vocabulary.CLASSIFICATION ? "classification" : "role")
                    + " " + c + " is retired and is not used for new entries.");
        }
        return c;
    }

    Map<String, VocabularyTerm> byCode(Vocabulary vocabulary) {
        return ctx.vocabularies().list(vocabulary).stream().collect(Collectors.toMap(VocabularyTerm::code, Function.identity()));
    }

    private void record(Actor actor, Vocabulary vocabulary, String code, String kind, List<FieldChange> changes) {
        UUID archiveId = ctx.archive().find().orElseThrow(() -> new IllegalStateException("The archive is not initialized"))
                .archiveId();
        ctx.recorder().record(actor, ArchiveService.OBJECT_TYPE, archiveId, kind, changes,
                (vocabulary == Vocabulary.CLASSIFICATION ? "Record classification " : "Record role ") + code);
    }

    private static String field(Vocabulary vocabulary, String code, String attribute) {
        return vocabulary.historyPrefix() + "_" + code.toLowerCase(Locale.ROOT) + "_" + attribute;
    }
}
