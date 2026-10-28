package org.genealogyarchive.archive;

import java.util.List;
import java.util.UUID;

/**
 * Archive-level configuration as it would travel with the archive (BUILD-SPEC s.55, UP-007): the
 * Archive ID, archive preferences, Storage Volume identities and descriptions, and registered
 * friendly-identifier series. It deliberately has no field that could hold installation
 * configuration (database connection, secrets, native storage roots, server or logging settings).
 * Complete Archive Export and Transfer Packages (Phase 8) will serialize this view.
 */
public record ArchiveConfiguration(
        UUID archiveId,
        String archiveName,
        List<Volume> storageVolumes,
        List<IdentifierSeries> identifierSeries) {

    public ArchiveConfiguration {
        storageVolumes = List.copyOf(storageVolumes);
        identifierSeries = List.copyOf(identifierSeries);
    }

    /** A Storage Volume's portable identity and description; never its native root. */
    public record Volume(UUID volumeId, String name, String purpose, String description, boolean required, boolean retired) {
    }

    /** A friendly-identifier series registered for an object type. */
    public record IdentifierSeries(String objectType, String prefix, int minDigits) {
    }
}
