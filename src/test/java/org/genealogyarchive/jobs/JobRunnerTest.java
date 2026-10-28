package org.genealogyarchive.jobs;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobRunnerTest {

    @Test
    void backoffGrowsAndIsCappedAtOneHour() {
        assertThat(JobRunner.backoff(1)).isEqualTo(Duration.ofSeconds(20));
        assertThat(JobRunner.backoff(2)).isEqualTo(Duration.ofSeconds(40));
        assertThat(JobRunner.backoff(30)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void storedErrorIsBounded() {
        String described = JobRunner.describe(new IllegalStateException("x".repeat(5000)));
        assertThat(described).startsWith("IllegalStateException: ").hasSize(JobRunner.MAX_ERROR_LENGTH);
    }

    /** A clock the test moves by hand. */
    private static final class ManualClock extends Clock {
        Instant now = Instant.parse("2026-10-28T12:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static Job runningJob(Instant leaseUntil) {
        return new Job(UUID.randomUUID(), "test.kind", JobState.RUNNING, "{}", 1, 3, leaseUntil, UUID.randomUUID(),
                UUID.randomUUID(), leaseUntil, null, leaseUntil, leaseUntil, null);
    }

    @Test
    void executionLeaseLapsesLocallyWhenNotRenewedInTime() {
        ManualClock clock = new ManualClock();
        JobExecution execution = new JobExecution(runningJob(clock.now), clock, clock.now.plusSeconds(10));
        assertThat(execution.leaseHeld()).isTrue();
        clock.now = clock.now.plusSeconds(8);
        execution.renewed(clock.now.plusSeconds(10));
        clock.now = clock.now.plusSeconds(9);
        assertThat(execution.leaseHeld()).as("renewal extended the local lease end").isTrue();
        clock.now = clock.now.plusSeconds(2);
        assertThat(execution.leaseHeld()).as("no renewal before the local lease end").isFalse();
        assertThatThrownBy(execution::requireLease).isInstanceOf(JobExecution.LeaseLostException.class);
    }

    @Test
    void lostOwnershipIsNeverRegainedByTheSameAttempt() {
        ManualClock clock = new ManualClock();
        JobExecution execution = new JobExecution(runningJob(clock.now), clock, clock.now.plusSeconds(10));
        execution.markLost();
        execution.renewed(clock.now.plusSeconds(60));
        assertThat(execution.leaseHeld()).isFalse();
    }

    @Test
    void leaseAndRenewalIntervalMustBePositive() {
        assertThatThrownBy(() -> new JobRunner(null, Clock.systemUTC(), UUID.randomUUID(), Duration.ZERO, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JobRunner(null, Clock.systemUTC(), UUID.randomUUID(), Duration.ofSeconds(1),
                Duration.ZERO, List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
