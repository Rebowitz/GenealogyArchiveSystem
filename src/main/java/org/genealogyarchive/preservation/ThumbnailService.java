package org.genealogyarchive.preservation;

import org.genealogyarchive.files.ManagedVolume;
import org.genealogyarchive.platform.SafeFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

/**
 * Rebuildable thumbnails in the installation-local cache (BUILD-SPEC s.62, OP-002). Uses only the
 * JDK's image readers (JPEG, PNG, GIF, BMP, TIFF). Reads the preservation original read-only and never
 * changes it; a failure leaves the original untouched and the job can be retried. Display rotation is
 * applied by the browser, so a rotation change needs no new thumbnail.
 */
public final class ThumbnailService {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailService.class);
    static final int SIZE = 320;
    /** Refuse to decode images whose pixel count would exhaust worker memory. */
    static final long MAX_SOURCE_PIXELS = 200_000_000L;

    private final PreservationContext ctx;

    public ThumbnailService(PreservationContext ctx) {
        this.ctx = ctx;
    }

    /** Cache location of a File's thumbnail. */
    public static Path location(Path cacheDirectory, UUID fileId) {
        String id = fileId.toString().toLowerCase(Locale.ROOT);
        return cacheDirectory.resolve("thumbnails").resolve(id.substring(34, 36)).resolve(id + ".jpg");
    }

    /**
     * Generates the thumbnail of a File.
     *
     * @return true if a thumbnail was written; false if the type is not thumbnailable or no cache is configured
     * @throws IOException if reading or writing failed (the job is retried and stays diagnosable)
     */
    public boolean generate(UUID fileId) throws IOException {
        if (ctx.settings().cacheDirectory().isEmpty()) {
            return false;
        }
        ArchivalFile file = ctx.files().find(fileId).orElseThrow();
        if (file.lifecycle() == FileLifecycle.DESTROYED || !MediaTypes.thumbnailable(file.mediaType())) {
            return false;
        }
        FileContent content = ctx.contents().find(file.contentId()).orElseThrow();
        if (content.state() != ContentState.ACTIVE || content.currentPath() == null) {
            return false;
        }
        ManagedVolume volume = ctx.openVolume(content.volumeId());
        java.util.Optional<Path> located = volume.locate(content.currentPath());
        if (located.isEmpty() || !java.nio.file.Files.isRegularFile(located.get(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return false; // missing originals are reported by integrity scans and downloads
        }
        BufferedImage image = readScaled(located.get());
        if (image == null) {
            return false;
        }
        Path target = location(ctx.settings().cacheDirectory().get(), fileId);
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            if (!ImageIO.write(image, "jpg", temp.toFile())) {
                throw new IOException("no JPEG writer available");
            }
            // The cache is rebuildable; replacing an older thumbnail is intended.
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
        return true;
    }

    private static BufferedImage readScaled(Path source) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(Channels.newInputStream(SafeFiles.openForRead(source)))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long w = reader.getWidth(0);
                long h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w * h > MAX_SOURCE_PIXELS) {
                    log.info("Thumbnail skipped: image of {}x{} pixels exceeds the safe decoding limit", w, h);
                    return null;
                }
                ImageReadParam param = reader.getDefaultReadParam();
                int step = (int) Math.max(1, Math.max(w, h) / (SIZE * 2L));
                param.setSourceSubsampling(step, step, 0, 0);
                BufferedImage decoded = reader.read(0, param);
                double scale = Math.min(1.0, (double) SIZE / Math.max(decoded.getWidth(), decoded.getHeight()));
                int tw = Math.max(1, (int) Math.round(decoded.getWidth() * scale));
                int th = Math.max(1, (int) Math.round(decoded.getHeight() * scale));
                BufferedImage thumb = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = thumb.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(decoded, 0, 0, tw, th, java.awt.Color.WHITE, null);
                } finally {
                    g.dispose();
                }
                return thumb;
            } finally {
                reader.dispose();
            }
        }
    }

    static boolean exists(Path cacheDirectory, UUID fileId) {
        return Files.isRegularFile(location(cacheDirectory, fileId), LinkOption.NOFOLLOW_LINKS);
    }
}
