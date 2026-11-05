package org.genealogyarchive.testsupport;

import org.genealogyarchive.preservation.FaultPoints;

import java.io.IOException;
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
