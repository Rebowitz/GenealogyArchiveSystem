package org.genealogyarchive.testsupport;

import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A browser-like HTTP session for web tests: keeps cookies, does not follow redirects, and submits
 * forms with the CSRF token taken from the page that contains the form (as a browser would).
 */
public final class WebSession {

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"|value=\"([^\"]+)\"\\s+name=\"_csrf\"");

    private final ConfigurableApplicationContext app;
    private final HttpClient client;

    public WebSession(ConfigurableApplicationContext app) {
        this.app = app;
        this.client = HttpClient.newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + Http.port(app) + path);
    }

    public HttpResponse<String> get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(90)).GET().build());
    }

    /** Submits a form found on {@code formPage} to {@code action}, adding the page's CSRF token. */
    public HttpResponse<String> submit(String formPage, String action, Map<String, String> fields) {
        String token = csrfToken(get(formPage).body());
        Map<String, String> all = new LinkedHashMap<>(fields);
        all.put("_csrf", token);
        return postRaw(action, all);
    }

    /** POST without adding a CSRF token (to prove CSRF protection). */
    public HttpResponse<String> postRaw(String action, Map<String, String> fields) {
        StringJoiner body = new StringJoiner("&");
        fields.forEach((k, v) -> body.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(v, StandardCharsets.UTF_8)));
        return send(HttpRequest.newBuilder(uri(action)).timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build());
    }

    /** Signs in through the real sign-in form. @return the response to the sign-in POST */
    public HttpResponse<String> signIn(String username, String password) {
        return submit("/login", "/login", Map.of("username", username, "password", password));
    }

    public static String csrfToken(String html) {
        Matcher m = CSRF.matcher(html);
        if (!m.find()) {
            throw new IllegalStateException("No CSRF token found in page");
        }
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    public static String location(HttpResponse<?> response) {
        return response.headers().firstValue("Location").orElse("");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
