package org.genealogyarchive.jobs;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

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
}
