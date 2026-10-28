package org.genealogyarchive.platform;

import org.genealogyarchive.testsupport.DirectoryLinks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

/** GAS-P0-002: snapshot checks, not claims of race-free operational file access. */
class PhysicalPathsTest {
    @TempDir Path temp;

    @Test
    void existingAndNotYetCreatedTargetsUnderOrdinaryRootAreContained() throws IOException {
        Path root = Files.createDirectory(temp.resolve("volume"));
        Path existing = Files.writeString(root.resolve("present"), "probe");
        PhysicalPaths.requireContained(root, existing);
        PhysicalPaths.requireContained(root, root.resolve("future/nested/file"));
        assertThat(PhysicalPaths.existingAncestorPath(root.resolve("future")))
                .isEqualTo(root.toRealPath().resolve("future"));
    }

    @Test
    void linkToOutsideIsRejectedForExistingAndFutureObjects() throws Exception {
        Path root = Files.createDirectory(temp.resolve("volume"));
        Path outside = Files.createDirectory(temp.resolve("outside"));
        Files.writeString(outside.resolve("present"), "probe");
        Path link = DirectoryLinks.create(root.resolve("alias"), outside);
        try {
            assertThatThrownBy(() -> PhysicalPaths.requireContained(root, link.resolve("present")))
                    .isInstanceOf(IOException.class).hasMessageContaining("outside");
            assertThatThrownBy(() -> PhysicalPaths.requireContained(root, link.resolve("future/file")))
                    .isInstanceOf(IOException.class).hasMessageContaining("outside");
        } finally {
            Files.delete(link);
        }
    }

    @Test
    void rootAliasAndLinkRemainingInsideRootAreAllowed() throws Exception {
        Path root = Files.createDirectory(temp.resolve("volume"));
        Path inside = Files.createDirectory(root.resolve("inside"));
        Path rootAlias = DirectoryLinks.create(temp.resolve("rootAlias"), root);
        Path childAlias = DirectoryLinks.create(root.resolve("childAlias"), inside);
        try {
            PhysicalPaths.requireContained(rootAlias, rootAlias.resolve("future"));
            PhysicalPaths.requireContained(root, childAlias.resolve("future"));
        } finally {
            Files.delete(childAlias);
            Files.delete(rootAlias);
        }
    }
}
