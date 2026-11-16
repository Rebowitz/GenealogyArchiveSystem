package org.genealogyarchive.data;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The global row-lock order of Phase 4 (ADR-0012 s.8; clarifications s.5.5.3, review finding PR4-R3). Every
 * explicit row lock on an archival table is announced here, in the repository, immediately before its SQL.
 *
 * <p>The order is a total order over (level, UUID). Rules:
 * <ol>
 *   <li>Locks are acquired in non-decreasing level order; levels may be skipped, never reversed.</li>
 *   <li>Within a level above L1, rows are locked in ascending UUID order (PostgreSQL's unsigned byte order).</li>
 *   <li>A row is locked at its final strength the first time: no SHARE to UPDATE upgrade, at any level.</li>
 *   <li>A re-lock of a row already held at the same or a weaker strength never waits; it is recorded, skips the
 *       UUID check of its level, and is still rejected after a higher level (it would reveal a mis-ordered path).
 *       A row is always announced at the same level.</li>
 * </ol>
 * L7 (reference rows locked only implicitly by foreign-key checks) has no level here: no writer locks an L7 row
 * explicitly through this class. A level and its checks must be added before any writer does (WP1-R3).
 * L1 is the accepted Phase 3 chain (capture session, worksheet rows, Files, Archival Record), kept exactly as
 * Phase 3 accepted it; its internal order is Phase 3's own and is not re-checked here (GAS-P4-002; the locking
 * protocol proposal for WP1-R1 is pending owner approval). What is checked at L1 is that no L1 lock follows a lock
 * of a higher level and that no L1 lock is upgraded.
 *
 * <p>The guard ({@code -Dgas.lockOrderGuard=true}, set for every test run) throws on a violation, so a mis-ordered
 * path fails visibly in the test suites (LO-2). Observers receive each transaction's sequence when it completes
 * (LO-1). With neither enabled this class does nothing.
 */
public final class LockSequence {

    /** Lock levels in acquisition order. Persons precede Places within L2. */
    public enum Level {
        L0_RESTRICTION_OPERATION,
        L1_PHASE3,
        L2_PERSON,
        L2_PLACE,
        L3_MENTION,
        L4_COLLECTIVE,
        L5_IDENTITY_PROPOSAL
    }

    public enum Mode {
        SHARE,
        UPDATE
    }

    public record Acquisition(Level level, String table, UUID id, Mode mode) {
    }

    /** Thrown by the guard when a lock would break the global order. */
    public static final class LockOrderViolation extends IllegalStateException {
        LockOrderViolation(String message) {
            super(message);
        }
    }

    private static final boolean GUARD = Boolean.getBoolean("gas.lockOrderGuard");
    private static final List<Consumer<List<Acquisition>>> OBSERVERS = new CopyOnWriteArrayList<>();

    private LockSequence() {
    }

    /**
     * Announces an explicit row lock about to be taken in the current transaction. Outside a transaction, or with
     * neither the guard nor an observer active, nothing happens.
     */
    public static void acquire(Level level, String table, UUID id, Mode mode) {
        if (!GUARD && OBSERVERS.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        Tracker tracker = (Tracker) TransactionSynchronizationManager.getResource(LockSequence.class);
        if (tracker == null) {
            Tracker created = new Tracker(GUARD);
            TransactionSynchronizationManager.bindResource(LockSequence.class, created);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(LockSequence.class);
                    for (Consumer<List<Acquisition>> o : OBSERVERS) {
                        o.accept(created.acquisitions());
                    }
                }
            });
            tracker = created;
        }
        tracker.acquire(new Acquisition(level, table, id, mode));
    }

    /** True if the guard is on in this process. */
    public static boolean guardEnabled() {
        return GUARD;
    }

    /** Registers an observer of completed transactions' lock sequences (tests); returns a handle that removes it. */
    public static AutoCloseable observe(Consumer<List<Acquisition>> observer) {
        OBSERVERS.add(observer);
        return () -> OBSERVERS.remove(observer);
    }

    /** One transaction's sequence; checks the rules when {@code guard} is set. */
    public static final class Tracker {

        private final boolean guard;
        private final List<Acquisition> acquisitions = new ArrayList<>();

        public Tracker(boolean guard) {
            this.guard = guard;
        }

        public List<Acquisition> acquisitions() {
            return List.copyOf(acquisitions);
        }

        public void acquire(Acquisition a) {
            if (guard) {
                check(a);
            }
            acquisitions.add(a);
        }

        private void check(Acquisition a) {
            if (acquisitions.isEmpty()) {
                return;
            }
            Level highest = acquisitions.stream().map(Acquisition::level).max(Enum::compareTo).orElseThrow();
            Acquisition held = null;
            for (Acquisition h : acquisitions) {
                if (h.table().equals(a.table()) && h.id().equals(a.id())) {
                    held = h;
                    break;
                }
            }
            if (held != null) {
                // Re-lock of a row this transaction already holds. Its strength must not rise at any level,
                // including L1: two holders of SHARE upgrading to UPDATE deadlock each other (WP1-R3).
                if (held.level() != a.level()) {
                    throw new LockOrderViolation(a.table() + " " + a.id() + " announced at " + a.level()
                            + " but first locked at " + held.level());
                }
                if (strongest(a.table(), a.id()) == Mode.SHARE && a.mode() == Mode.UPDATE) {
                    throw new LockOrderViolation("Lock upgrade from SHARE to UPDATE on " + a.table() + " " + a.id()
                            + ": lock the row at its final strength the first time");
                }
                // A same-strength or weaker re-lock never waits (the row is already held), so it is exempt from
                // the UUID-order check within its level. It is not exempt from the level order: a re-lock below a
                // higher level already held marks a mis-ordered code path and is rejected like a new lock.
                if (a.level().compareTo(highest) < 0) {
                    throw new LockOrderViolation("Re-lock of " + a.table() + " " + a.id() + " at " + a.level()
                            + " after " + highest + " was locked");
                }
                return;
            }
            if (a.level().compareTo(highest) < 0) {
                Acquisition last = acquisitions.get(acquisitions.size() - 1);
                throw new LockOrderViolation("Lock order reversed: " + a.level() + " " + a.table() + " after "
                        + highest + " (" + last.table() + ")");
            }
            if (a.level() == highest && a.level() != Level.L1_PHASE3) {
                UUID max = acquisitions.stream().filter(h -> h.level() == highest).map(Acquisition::id)
                        .max(LockSequence::compareUnsigned).orElseThrow();
                if (compareUnsigned(a.id(), max) < 0) {
                    throw new LockOrderViolation("Rows of " + a.level() + " locked out of UUID order: " + a.id()
                            + " after " + max);
                }
            }
        }

        private Mode strongest(String table, UUID id) {
            Mode m = Mode.SHARE;
            for (Acquisition h : acquisitions) {
                if (h.table().equals(table) && h.id().equals(id) && h.mode() == Mode.UPDATE) {
                    m = Mode.UPDATE;
                }
            }
            return m;
        }
    }

    /** PostgreSQL's uuid ordering: unsigned, byte by byte (java.util.UUID#compareTo is signed). */
    public static int compareUnsigned(UUID x, UUID y) {
        int c = Long.compareUnsigned(x.getMostSignificantBits(), y.getMostSignificantBits());
        return c != 0 ? c : Long.compareUnsigned(x.getLeastSignificantBits(), y.getLeastSignificantBits());
    }
}
