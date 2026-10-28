package org.genealogyarchive.testsupport;

import org.genealogyarchive.platform.HostPlatform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Native Windows junctions need no symlink privilege; Linux exercises symbolic links. No skips. */
public final class DirectoryLinks {
    private DirectoryLinks() { }
    public static Path create(Path link, Path target) throws IOException, InterruptedException {
        if (HostPlatform.current() == HostPlatform.WINDOWS) {
            Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J",
                    link.toString(), target.toString()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                throw new IOException("Cannot create Windows junction for containment test: " + output);
            }
        } else {
            Files.createSymbolicLink(link, target);
        }
        return link;
    }
}
