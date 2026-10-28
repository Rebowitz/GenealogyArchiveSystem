package org.genealogyarchive.testsupport;

import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Minimal HTTP client for exercising a running web process in tests. */
public final class Http {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .proxy(HttpClient.Builder.NO_PROXY)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private Http() {
    }

    public static int port(ConfigurableApplicationContext context) {
        return Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
    }

    public static HttpResponse<String> get(ConfigurableApplicationContext context, String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port(context) + path))
                    .timeout(Duration.ofSeconds(90))
                    .GET()
                    .build();
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
