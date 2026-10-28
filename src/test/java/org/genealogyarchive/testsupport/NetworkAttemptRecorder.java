package org.genealogyarchive.testsupport;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Installs a JVM-wide {@link ProxySelector} that records every outbound connection attempt made
 * through the JDK's URL/HTTP stack (and socket-level proxy consultation) to a non-loopback host.
 * Used to show the application performs no Internet/cloud access during normal startup and use
 * (PHASE-0 "no normal runtime Internet/cloud dependency"; UP-001 foundation).
 * <p>
 * Limitation (documented in the completion report): code that opens raw sockets with an explicit
 * {@code Proxy.NO_PROXY} bypasses the selector. ArchitectureTest separately forbids outbound HTTP
 * clients in application code.
 */
public final class NetworkAttemptRecorder extends ProxySelector implements AutoCloseable {

    private final ProxySelector previous;
    private final List<URI> nonLocalAttempts = new CopyOnWriteArrayList<>();

    private NetworkAttemptRecorder() {
        this.previous = ProxySelector.getDefault();
    }

    public static NetworkAttemptRecorder install() {
        NetworkAttemptRecorder recorder = new NetworkAttemptRecorder();
        ProxySelector.setDefault(recorder);
        return recorder;
    }

    public List<URI> nonLocalAttempts() {
        return List.copyOf(nonLocalAttempts);
    }

    @Override
    public List<Proxy> select(URI uri) {
        String host = uri.getHost();
        if (host != null && !isLoopback(host)) {
            nonLocalAttempts.add(uri);
        }
        return List.of(Proxy.NO_PROXY);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        // nothing to do
    }

    @Override
    public void close() {
        ProxySelector.setDefault(previous);
    }

    private static boolean isLoopback(String host) {
        if (host.equalsIgnoreCase("localhost")) {
            return true;
        }
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
