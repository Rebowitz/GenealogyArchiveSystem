package org.genealogyarchive.preservation;

import org.genealogyarchive.files.ManagedVolume;
import org.genealogyarchive.files.PortableRelativePath;
import org.genealogyarchive.files.VolumeMarker;
import org.genealogyarchive.platform.SafeFiles;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Whether an object is at a location on a managed volume (V0019 audit, finding 3). Three answers, never
 * two: PRESENT, VERIFIED_ABSENT, or UNKNOWN. {@code Files.exists(path) == false} is not absence - it is also
 * what a permission error, an unreadable directory or a vanished mount look like.
 * <p>
 * The probe reads attributes component by component from the volume root without following links. Absence
 * is concluded only from "no such file" for a component whose parent was read, and only while the root still
 * carries this archive's marker for this configured volume, verified before the examination and again before
 * absence is concluded (an unmounted volume leaves an empty mount point behind; a substituted volume carries
 * another marker). Anything else - access
 * denied, any I/O error, a link or non-directory in the chain, a missing marker - is UNKNOWN, which every
 * caller treats as "cannot decide": nothing is recorded missing, no record is closed, nothing counts as
 * removed, and the work is retried later.
 */
final class Presence {

    enum State { PRESENT, VERIFIED_ABSENT, UNKNOWN }

    /**
     * @param attributes the object's attributes (PRESENT only)
     * @param problem    why the answer is UNKNOWN (UNKNOWN only): no file names, only the kind of failure
     */
    record Probe(State state, Path path, BasicFileAttributes attributes, String problem) {
        boolean present() {
            return state == State.PRESENT;
        }

        boolean verifiedAbsent() {
            return state == State.VERIFIED_ABSENT;
        }
    }

    /** Thrown where a caller needs a decision and the probe could not give one. */
    static final class InconclusiveException extends IOException {
        InconclusiveException(String location, String problem) {
            super("whether anything is at " + location + " could not be determined (" + problem
                    + "); nothing was decided from it");
        }
    }

    private Presence() {
    }

    static Probe probe(PreservationContext ctx, ManagedVolume volume, String location) {
        Path target;
        try {
            target = PortableRelativePath.of(location).toNative(volume.root());
        } catch (RuntimeException e) {
            return new Probe(State.UNKNOWN, null, null, "the location is not a valid portable path");
        }
        return probe(ctx, volume, target);
    }

    static Probe probe(PreservationContext ctx, ManagedVolume volume, Path target) {
        Path root = volume.root();
        Path relative;
        try {
            relative = root.relativize(target);
        } catch (IllegalArgumentException e) {
            return new Probe(State.UNKNOWN, target, null, "the location is not on this volume");
        }
        if (relative.startsWith("..") || relative.toString().isEmpty()) {
            return new Probe(State.UNKNOWN, target, null, "the location is not on this volume");
        }
        try {
            BasicFileAttributes rootAttributes = ctx.faults().stat(root);
            if (!rootAttributes.isDirectory()) {
                return new Probe(State.UNKNOWN, target, null, "the volume root is not a directory");
            }
        } catch (IOException | SecurityException e) {
            return new Probe(State.UNKNOWN, target, null, "the volume root cannot be read: " + kind(e));
        }
        // V0019 audit: the configured volume itself, not merely "a volume", must be there before anything about
        // it is examined (and again before absence is concluded, below).
        if (!isThisVolume(ctx, volume)) {
            return new Probe(State.UNKNOWN, target, null, "the configured volume's identity cannot be confirmed at its root");
        }
        Path current = root;
        int count = relative.getNameCount();
        for (int i = 0; i < count; i++) {
            current = current.resolve(relative.getName(i));
            BasicFileAttributes a;
            try {
                a = ctx.faults().stat(current);
            } catch (NoSuchFileException e) {
                return isThisVolume(ctx, volume)
                        ? new Probe(State.VERIFIED_ABSENT, target, null, null)
                        : new Probe(State.UNKNOWN, target, null, "the configured volume's identity cannot be confirmed at "
                        + "its root (it may be unmounted or replaced)");
            } catch (IOException | SecurityException e) {
                return new Probe(State.UNKNOWN, target, null, kind(e));
            }
            if (i == count - 1) {
                return new Probe(State.PRESENT, target, a, null);
            }
            try {
                if (a.isSymbolicLink() || a.isOther() || !a.isDirectory() || SafeFiles.isLinkOrReparsePoint(current)) {
                    return new Probe(State.UNKNOWN, target, null, "a directory on the way is a link, reparse point or file");
                }
            } catch (IOException | SecurityException e) {
                return new Probe(State.UNKNOWN, target, null, kind(e));
            }
        }
        return new Probe(State.UNKNOWN, target, null, "empty location");
    }

    /**
     * The marker at the root is this archive's marker for this volume, re-read now: a regular file (examined
     * through the fault seam, so tests can make it vanish or become unreadable) whose identities match those the
     * volume was opened with. Any other marker - another volume's, a copy, a malformed one - is not this volume.
     */
    static boolean isThisVolume(PreservationContext ctx, ManagedVolume volume) {
        try {
            if (!ctx.faults().stat(volume.root().resolve(VolumeMarker.FILE_NAME)).isRegularFile()) {
                return false;
            }
        } catch (IOException | SecurityException e) {
            return false;
        }
        return volume.markerIdentifiesThisVolume();
    }

    private static String kind(Exception e) {
        if (e instanceof java.nio.file.AccessDeniedException || e instanceof SecurityException) {
            return "access denied";
        }
        return e.getClass().getSimpleName();
    }

    /**
     * @return true if present, false if verifiably absent
     * @throws InconclusiveException if it cannot be determined
     */
    static boolean exists(PreservationContext ctx, ManagedVolume volume, String location) throws InconclusiveException {
        return decide(probe(ctx, volume, location), location);
    }

    static boolean exists(PreservationContext ctx, ManagedVolume volume, Path target) throws InconclusiveException {
        Probe p = probe(ctx, volume, target);
        return decide(p, describe(volume, target));
    }

    private static boolean decide(Probe p, String location) throws InconclusiveException {
        return switch (p.state()) {
            case PRESENT -> true;
            case VERIFIED_ABSENT -> false;
            case UNKNOWN -> throw new InconclusiveException(location, p.problem());
        };
    }

    private static String describe(ManagedVolume volume, Path target) {
        try {
            return PortableRelativePath.fromNative(volume.root(), target).value();
        } catch (RuntimeException e) {
            return "a location on the volume";
        }
    }
}
