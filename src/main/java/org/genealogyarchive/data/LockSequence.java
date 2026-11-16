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
 *   <li>A row is locked at its final strength the first time: no SHARE to UPDATE upgrade.</li>
 * </ol>
 * L1 is the accepted Phase 3 chain (capture session, worksheet rows, Files, Archival Record), kept exactly as
 * Phase 3 accepted it; its internal order is Phase 3's own and is not re-checked here. What is checked is that no
 * L1 lock follows a lock of a higher level.
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
            for (Acquisition held : acquisitions) {
                if (held.table().equals(a.table()) && held.id().equals(a.id())) {
                    if (held.mode() == Mode.SHARE && a.mode() == Mode.UPDATE && a.level() != Level.L1_PHASE3) {
                        throw new LockOrderViolation("Lock upgrade from SHARE to UPDATE on " + a.table() + " "
                                + a.id() + ": lock the row at its final strength first");
                    }
                    return;
                }
            }
            if (acquisitions.isEmpty()) {
                return;
            }
            Acquisition last = acquisitions.get(acquisitions.size() - 1);
            Level highest = acquisitions.stream().map(Acquisition::level).max(Enum::compareTo).orElseThrow();
            if (a.level().compareTo(highest) < 0) {
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
    }

    /** PostgreSQL's uuid ordering: unsigned, byte by byte (java.util.UUID#compareTo is signed). */
    public static int compareUnsigned(UUID x, UUID y) {
        int c = Long.compareUnsigned(x.getMostSignificantBits(), y.getMostSignificantBits());
        return c != 0 ? c : Long.compareUnsigned(x.getLeastSignificantBits(), y.getLeastSignificantBits());
    }
}
