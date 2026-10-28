package org.genealogyarchive.platform;

import java.util.Locale;

/**
 * The operating system family this process is running on.
 * <p>
 * This is the ONLY place that inspects the OS name. Code outside {@code platform} must not branch on
 * operating system; archival and domain behaviour is identical on every host (BUILD-SPEC s.6).
 */
public enum HostPlatform {
    WINDOWS("Windows service (WinSW)"),
    LINUX("systemd"),
    /** Not a supported v1 server host. Development may still work but is untested. */
    OTHER("unsupported host - no service manager integration");

    private final String serviceManager;

    HostPlatform(String serviceManager) {
        this.serviceManager = serviceManager;
    }

    /** Name of the native service-management mechanism used on this host family. */
    public String serviceManager() {
        return serviceManager;
    }

    public boolean isSupportedServerHost() {
        return this != OTHER;
    }

    public static HostPlatform current() {
        return fromOsName(System.getProperty("os.name", ""));
    }

    static HostPlatform fromOsName(String osName) {
        String name = osName.toLowerCase(Locale.ROOT);
        if (name.startsWith("windows")) {
            return WINDOWS;
        }
        if (name.startsWith("linux")) {
            return LINUX;
        }
        return OTHER;
    }
}
