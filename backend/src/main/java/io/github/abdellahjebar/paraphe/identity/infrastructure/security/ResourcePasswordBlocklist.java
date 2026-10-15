package io.github.abdellahjebar.paraphe.identity.infrastructure.security;

import io.github.abdellahjebar.paraphe.identity.domain.PasswordBlocklist;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** The list shipped with the application, loaded once at startup. */
@Component
class ResourcePasswordBlocklist implements PasswordBlocklist {

    private static final String RESOURCE = "security/common-passwords.txt";

    private final Set<String> entries = new HashSet<>();

    ResourcePasswordBlocklist() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    entries.add(PasswordBlocklist.key(line));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException(RESOURCE + " is empty: refusing to start without a blocklist");
        }
    }

    @Override
    public boolean contains(String candidate) {
        return candidate != null && entries.contains(PasswordBlocklist.key(candidate));
    }

    int size() {
        return entries.size();
    }
}
