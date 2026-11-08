package org.genealogyarchive.records;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.CatalogRepository;
import org.genealogyarchive.history.ChangeKinds;
import org.genealogyarchive.history.ChangeRecord;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.history.FieldClass;
import org.genealogyarchive.history.HistoryService;
import org.genealogyarchive.identity.FriendlyId;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.service.ConflictException;
import org.genealogyarchive.service.NotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositories and Sources (BUILD-SPEC s.19, s.22). A Source's Repository is optional (O-003); its historical
 * creator, Access Provider and jurisdiction are kept as written, separately from the Repository (O-004...O-006,
 * D3-06). Every change is attributed and recorded in Change History in the same transaction.
 */
public final class CatalogService {

    private final RecordsContext ctx;

    public CatalogService(RecordsContext ctx) {
        this.ctx = ctx;
    }

    // ---- Repositories ------------------------------------------------------------------------------

    public Repository createRepository(Actor actor, String name, String description, String location) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        String n = Texts.required(name, "The Repository name", 300);
        String d = Texts.optional(description, "The description", 4000, true);
        String l = Texts.optional(location, "The address or location", 1000, true);
        return ctx.tx().execute(status -> {
            UUID id = GlobalIds.newId();
            FriendlyId fid = ctx.friendlyIds().allocate(CatalogRepository.REPOSITORY_TYPE);
            ctx.catalog().insertRepository(id, fid.number(), n, d, l, ctx.clock().instant(), actor.userId());
            ctx.recorder().record(actor, CatalogRepository.REPOSITORY_TYPE, id, ChangeKinds.CREATED, List.of(
                    FieldChange.set("friendly_id", FieldClass.IMMUTABLE, fid.value()),
                    FieldChange.set("name", FieldClass.CORRECTABLE, n),
                    FieldChange.set("description", FieldClass.CORRECTABLE, d),
                    FieldChange.set("location", FieldClass.CORRECTABLE, l)), null);
            return ctx.catalog().findRepository(id).orElseThrow();
        });
    }

    public Repository repository(Actor actor, UUID id) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        return ctx.catalog().findRepository(id).orElseThrow(() -> new NotFoundException("The repository"));
    }

    public Repository repositoryByFriendlyId(Actor actor, String text) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        long n = RecordAccess.friendlyNumber(text, CatalogRepository.REPOSITORY_PREFIX)
                .orElseThrow(() -> new NotFoundException("The repository"));
        return ctx.catalog().findRepositoryByNumber(n).orElseThrow(() -> new NotFoundException("The repository"));
    }

    public List<Repository> repositories(Actor actor) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        return ctx.catalog().listRepositories(1000);
    }

    public Repository correctRepository(Actor actor, UUID id, long expectedVersion, String name, String description,
                                        String location, String reason) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        String n = Texts.required(name, "The Repository name", 300);
        String d = Texts.optional(description, "The description", 4000, true);
        String l = Texts.optional(location, "The address or location", 1000, true);
        return ctx.tx().execute(status -> {
            Repository current = ctx.catalog().lockRepository(id).orElseThrow(() -> new NotFoundException("The repository"));
            if (current.version() != expectedVersion) {
                throw new ConflictException("The repository");
            }
            List<FieldChange> changes = List.of(
                    FieldChange.of("name", FieldClass.CORRECTABLE, current.name(), n),
                    FieldChange.of("description", FieldClass.CORRECTABLE, current.description(), d),
                    FieldChange.of("location", FieldClass.CORRECTABLE, current.locationText(), l));
            if (changes.stream().noneMatch(FieldChange::changed)) {
                return current;
            }
            if (!ctx.catalog().updateRepository(id, expectedVersion, n, d, l)) {
                throw new ConflictException("The repository");
            }
            ctx.recorder().record(actor, CatalogRepository.REPOSITORY_TYPE, id, ChangeKinds.CORRECTED, changes, reason);
            return ctx.catalog().findRepository(id).orElseThrow();
        });
    }

    public List<ChangeRecord> repositoryHistory(Actor actor, UUID id, HistoryService history) {
        repository(actor, id);
        return history.forObject(actor, CatalogRepository.REPOSITORY_TYPE, id);
    }

    // ---- Sources -----------------------------------------------------------------------------------

    /** What a researcher enters for a Source; blank fields are simply not known. */
    public record SourceInput(String title, UUID repositoryId, String sourceType, String volumeDesignation,
                              String creatorText, String accessProviderText, String jurisdictionText, String notes) {
    }

    private CatalogRepository.SourceFields fields(SourceInput in) {
        return new CatalogRepository.SourceFields(
                Texts.required(in.title(), "The Source title", 500), in.repositoryId(),
                Texts.optional(in.sourceType(), "The source type", 200),
                Texts.optional(in.volumeDesignation(), "The book or volume", 200),
                Texts.optional(in.creatorText(), "The historical creator", 1000, true),
                Texts.optional(in.accessProviderText(), "The access provider", 1000, true),
                Texts.optional(in.jurisdictionText(), "The jurisdiction", 1000, true),
                Texts.optional(in.notes(), "The notes", 8000, true));
    }

    public Source createSource(Actor actor, SourceInput input) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        CatalogRepository.SourceFields f = fields(input);
        return ctx.tx().execute(status -> {
            String repository = repositoryLabel(f.repositoryId());
            UUID id = GlobalIds.newId();
            FriendlyId fid = ctx.friendlyIds().allocate(CatalogRepository.SOURCE_TYPE);
            ctx.catalog().insertSource(id, fid.number(), f, ctx.clock().instant(), actor.userId());
            ctx.recorder().record(actor, CatalogRepository.SOURCE_TYPE, id, ChangeKinds.CREATED,
                    sourceChanges(new CatalogRepository.SourceFields(null, null, null, null, null, null, null, null), null,
                            f, repository, fid.value()), null);
            return ctx.catalog().findSource(id).orElseThrow();
        });
    }

    public Source source(Actor actor, UUID id) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        return ctx.catalog().findSource(id).orElseThrow(() -> new NotFoundException("The source"));
    }

    public Optional<Source> findSource(Actor actor, UUID id) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        return id == null ? Optional.empty() : ctx.catalog().findSource(id);
    }

    public Optional<Repository> findRepository(Actor actor, UUID id) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        return id == null ? Optional.empty() : ctx.catalog().findRepository(id);
    }

    public Source sourceByFriendlyId(Actor actor, String text) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        long n = RecordAccess.friendlyNumber(text, CatalogRepository.SOURCE_PREFIX)
                .orElseThrow(() -> new NotFoundException("The source"));
        return ctx.catalog().findSourceByNumber(n).orElseThrow(() -> new NotFoundException("The source"));
    }

    public List<Source> sources(Actor actor, UUID repositoryId) {
        AccessPolicy.require(actor, Permission.VIEW_ARCHIVE);
        return ctx.catalog().listSources(repositoryId, 1000);
    }

    public Source correctSource(Actor actor, UUID id, long expectedVersion, SourceInput input, String reason) {
        AccessPolicy.require(actor, Permission.EDIT_RECORDS);
        CatalogRepository.SourceFields f = fields(input);
        return ctx.tx().execute(status -> {
            Source current = ctx.catalog().lockSource(id).orElseThrow(() -> new NotFoundException("The source"));
            if (current.version() != expectedVersion) {
                throw new ConflictException("The source");
            }
            CatalogRepository.SourceFields before = new CatalogRepository.SourceFields(current.title(),
                    current.repositoryId(), current.sourceType(), current.volumeDesignation(), current.creatorText(),
                    current.accessProviderText(), current.jurisdictionText(), current.notes());
            List<FieldChange> changes = sourceChanges(before, repositoryLabel(current.repositoryId()), f,
                    repositoryLabel(f.repositoryId()), null);
            if (changes.stream().noneMatch(FieldChange::changed)) {
                return current;
            }
            if (!ctx.catalog().updateSource(id, expectedVersion, f)) {
                throw new ConflictException("The source");
            }
            ctx.recorder().record(actor, CatalogRepository.SOURCE_TYPE, id, ChangeKinds.CORRECTED, changes, reason);
            return ctx.catalog().findSource(id).orElseThrow();
        });
    }

    public List<ChangeRecord> sourceHistory(Actor actor, UUID id, HistoryService history) {
        source(actor, id);
        return history.forObject(actor, CatalogRepository.SOURCE_TYPE, id);
    }

    /** The Repository's friendly ID for history, or null; an unknown ID is refused (no dangling reference). */
    private String repositoryLabel(UUID repositoryId) {
        if (repositoryId == null) {
            return null;
        }
        return ctx.catalog().findRepository(repositoryId).map(Repository::friendlyId)
                .orElseThrow(() -> new NotFoundException("The repository"));
    }

    private static List<FieldChange> sourceChanges(CatalogRepository.SourceFields b, String repoBefore,
                                                   CatalogRepository.SourceFields a, String repoAfter, String friendlyId) {
        List<FieldChange> c = new ArrayList<>();
        if (friendlyId != null) {
            c.add(FieldChange.set("friendly_id", FieldClass.IMMUTABLE, friendlyId));
        }
        c.add(FieldChange.of("title", FieldClass.CORRECTABLE, b.title(), a.title()));
        c.add(FieldChange.of("repository", FieldClass.CORRECTABLE, repoBefore, repoAfter));
        c.add(FieldChange.of("source_type", FieldClass.CORRECTABLE, b.sourceType(), a.sourceType()));
        c.add(FieldChange.of("volume_designation", FieldClass.CORRECTABLE, b.volumeDesignation(), a.volumeDesignation()));
        c.add(FieldChange.of("creator", FieldClass.CORRECTABLE, b.creatorText(), a.creatorText()));
        c.add(FieldChange.of("access_provider", FieldClass.CORRECTABLE, b.accessProviderText(), a.accessProviderText()));
        c.add(FieldChange.of("jurisdiction", FieldClass.CORRECTABLE, b.jurisdictionText(), a.jurisdictionText()));
        c.add(FieldChange.of("notes", FieldClass.CORRECTABLE, b.notes(), a.notes()));
        return c;
    }
}
