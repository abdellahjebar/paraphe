package io.github.abdellahjebar.paraphe.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.abdellahjebar.paraphe.TestcontainersConfiguration;
import io.github.abdellahjebar.paraphe.identity.application.UserAdminService;
import io.github.abdellahjebar.paraphe.identity.domain.AppUser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * Cookie attributes are set by the embedded server, so they can only be seen over real HTTP.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=true")
@Import(TestcontainersConfiguration.class)
class SessionCookieHttpTest {

    private static final String PASSWORD = "correct horse battery staple";

    @LocalServerPort
    private int port;

    @Autowired
    private UserAdminService admin;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String cookieNamed(List<String> setCookies, String name) {
        return setCookies.stream().filter(c -> c.startsWith(name + "=")).findFirst().orElseThrow();
    }

    @Test
    void sessionCookieIsHttpOnlySecureAndSameSite() throws Exception {
        AppUser user = admin.createUser("cookie-" + UUID.randomUUID() + "@example.org", "Khawla", PASSWORD,
                false, "test");
        URI base = URI.create("http://localhost:" + port);

        // 1. any API call hands out the CSRF cookie
        HttpResponse<String> first = send(HttpRequest.newBuilder(base.resolve("/api/auth/me")).GET().build());
        assertThat(first.statusCode()).isEqualTo(401);
        String xsrf = cookieNamed(first.headers().allValues("Set-Cookie"), "XSRF-TOKEN");
        String xsrfValue = xsrf.substring("XSRF-TOKEN=".length(), xsrf.indexOf(';'));
        // The front end must be able to read it, so this one is deliberately not HttpOnly.
        assertThat(xsrf).doesNotContainIgnoringCase("HttpOnly");

        // 2. sign in with that token
        HttpResponse<String> login = send(HttpRequest.newBuilder(base.resolve("/api/auth/login"))
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + xsrfValue)
                .header("X-XSRF-TOKEN", xsrfValue)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(user.email(), PASSWORD)))
                .build());

        assertThat(login.statusCode()).isEqualTo(200);
        // The idle time-out is set on the embedded server, so its value is only visible over real HTTP.
        assertThat(login.body()).contains("\"sessionIdleTimeoutSeconds\":3600");
        String session = cookieNamed(login.headers().allValues("Set-Cookie"), "JSESSIONID");
        assertThat(session).containsIgnoringCase("HttpOnly")
                .containsIgnoringCase("Secure")
                .containsIgnoringCase("SameSite=Lax");
    }
}
