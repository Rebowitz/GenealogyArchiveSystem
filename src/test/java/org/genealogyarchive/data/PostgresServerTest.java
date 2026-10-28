package org.genealogyarchive.data;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** ADR-0003: PostgreSQL 16 is the minimum supported baseline. */
class PostgresServerTest {

    @Test
    void minimumVersionRule() {
        assertThat(PostgresServer.isSupported(150012)).isFalse();
        assertThat(PostgresServer.isSupported(160000)).isTrue();
        assertThat(PostgresServer.isSupported(180001)).isTrue();
        assertThat(PostgresServer.majorVersion(160004)).isEqualTo(16);
    }
}
