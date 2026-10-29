package org.genealogyarchive.archive;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UP-007 / BUILD-SPEC s.55: the portable archive configuration has no place to hold installation
 * configuration. (ArchiveConfigurationIT checks actual values against a real installation.)
 */
class ArchiveConfigurationTest {

    private static List<String> components(Class<?> recordClass) {
        return Arrays.stream(recordClass.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    @Test
    void portableConfigurationHasOnlyArchiveLevelFields() {
        assertThat(components(ArchiveConfiguration.class))
                .containsExactly("archiveId", "archiveName", "storageVolumes", "identifierSeries");
        assertThat(components(ArchiveConfiguration.Volume.class))
                .containsExactly("volumeId", "name", "purpose", "description", "required", "retired")
                .doesNotContain("root", "path");
        assertThat(components(ArchiveConfiguration.IdentifierSeries.class))
                .containsExactly("objectType", "prefix", "minDigits");
    }
}
