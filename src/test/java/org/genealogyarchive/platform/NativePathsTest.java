package org.genealogyarchive.platform;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NativePathsTest {

    @Test
    void osNameMapping() {
        assertThat(HostPlatform.fromOsName("Windows Server 2022")).isEqualTo(HostPlatform.WINDOWS);
        assertThat(HostPlatform.fromOsName("Windows 11")).isEqualTo(HostPlatform.WINDOWS);
        assertThat(HostPlatform.fromOsName("Linux")).isEqualTo(HostPlatform.LINUX);
        assertThat(HostPlatform.fromOsName("Mac OS X")).isEqualTo(HostPlatform.OTHER);
        assertThat(HostPlatform.WINDOWS.serviceManager()).contains("Windows service");
        assertThat(HostPlatform.LINUX.serviceManager()).isEqualTo("systemd");
    }

    @Test
    void emptyPathRejected() {
        assertThat(NativePaths.validateAbsolute("  ").valid()).isFalse();
    }
}
