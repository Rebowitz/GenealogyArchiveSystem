package org.genealogyarchive.testsupport;

import org.genealogyarchive.preservation.FaultPoints;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Test fault points: arm an action at a protocol point; it fires once (or always, if sticky). */
public final class ScriptedFaults implements FaultPoints {

    /** What happens at an armed point. */
    @FunctionalInterface
    public interface Action {
        void fire(UUID operationId) throws IOException;
    }

    /** Simulates the process dying at this point: nothing after it runs. */
    public static final class SimulatedCrash extends RuntimeException {
        public SimulatedCrash(Point point) {
            super("simulated crash at " + point);
        }
    }

    private record Armed(Action action, boolean sticky) {
    }

    private final Map<Point, Armed> armed = new ConcurrentHashMap<>();

    public void arm(Point point, Action action) {
        armed.put(point, new Armed(action, false));
    }

    public void armSticky(Point point, Action action) {
        armed.put(point, new Armed(action, true));
    }

    public void crashAt(Point point) {
        arm(point, id -> {
            throw new SimulatedCrash(point);
        });
    }

    public void ioFailureAt(Point point) {
        arm(point, id -> {
            throw new IOException("injected I/O failure at " + point);
        });
    }

    public void clear() {
        armed.clear();
        inaccessible.clear();
    }

    /**
     * Makes every examination of {@code path} (and of anything below it) fail the way a permission error, an
     * unreadable directory or a failing mount does - identically on Windows and Linux and whatever the test
     * account's privileges - until {@link #restoreAccess} or {@link #clear}.
     */
    public void denyAccess(Path path) {
        inaccessible.put(path.toAbsolutePath().normalize(), "denied");
    }

    /** Like {@link #denyAccess}, but the examination fails with a plain I/O error. */
    public void failIo(Path path) {
        inaccessible.put(path.toAbsolutePath().normalize(), "io");
    }

    /** Makes {@code path} (and anything below it) look absent ("no such file"), as an unmounted volume does. */
    public void vanish(Path path) {
        inaccessible.put(path.toAbsolutePath().normalize(), "gone");
    }

    public void restoreAccess(Path path) {
        inaccessible.remove(path.toAbsolutePath().normalize());
    }

    private final Map<Path, String> inaccessible = new ConcurrentHashMap<>();

    @Override
    public BasicFileAttributes stat(Path path) throws IOException {
        Path p = path.toAbsolutePath().normalize();
        for (Map.Entry<Path, String> e : inaccessible.entrySet()) {
            if (p.startsWith(e.getKey())) {
                if ("denied".equals(e.getValue())) {
                    throw new AccessDeniedException(path.toString(), null, "injected: access denied");
                }
                if ("gone".equals(e.getValue())) {
                    throw new java.nio.file.NoSuchFileException(path.toString(), null, "injected: vanished");
                }
                throw new IOException("injected I/O error examining " + path);
            }
        }
        return FaultPoints.super.stat(path);
    }

    @Override
    public void reach(Point point, UUID operationId) throws IOException {
        Armed a = armed.get(point);
        if (a == null) {
            return;
        }
        if (!a.sticky()) {
            armed.remove(point);
        }
        a.action().fire(operationId);
    }
}
