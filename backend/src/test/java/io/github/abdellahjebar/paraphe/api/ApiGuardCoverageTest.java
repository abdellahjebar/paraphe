package io.github.abdellahjebar.paraphe.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.abdellahjebar.paraphe.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Walks every route the application registers and fails when a project route is missing its
 * membership guard. Forgetting the guard on a new endpoint breaks the build instead of shipping.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ApiGuardCoverageTest {

    /** Routes under /api that are deliberately not project-scoped. */
    private static final Set<String> NOT_PROJECT_SCOPED = Set.of("/api/projects", "/api/auth", "/api/probe");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapping;

    private List<String> unguardedProjectRoutes() {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
            Set<String> patterns = entry.getKey().getPathPatternsCondition().getPatternValues();
            for (String pattern : patterns) {
                if (!pattern.startsWith("/api/projects/{projectId}")) {
                    continue;
                }
                PreAuthorize guard = AnnotatedElementUtils.findMergedAnnotation(
                        entry.getValue().getMethod(), PreAuthorize.class);
                boolean guarded = guard != null && guard.value().contains("@projectAccessGuard.isMember(#projectId");
                if (!guarded) {
                    missing.add(entry.getKey().getMethodsCondition().getMethods() + " " + pattern);
                }
            }
        }
        return missing;
    }

    @Test
    void everyProjectRouteIsGuarded() {
        assertThat(unguardedProjectRoutes()).isEmpty();
    }

    @Test
    void projectRoutesExistSoTheCheckIsNotVacuous() {
        long projectRoutes = mapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPathPatternsCondition().getPatternValues().stream())
                .filter(p -> p.startsWith("/api/projects/{projectId}"))
                .count();

        assertThat(projectRoutes).isGreaterThanOrEqualTo(7);
    }

    @Test
    void everyApiRouteIsEitherProjectScopedOrAnExplicitlyKnownException() {
        List<String> unexpected = new ArrayList<>();
        for (RequestMappingInfo info : mapping.getHandlerMethods().keySet()) {
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (!pattern.startsWith("/api/")) {
                    continue;
                }
                boolean projectScoped = pattern.startsWith("/api/projects/{projectId}");
                boolean known = NOT_PROJECT_SCOPED.stream().anyMatch(pattern::startsWith);
                if (!projectScoped && !known) {
                    unexpected.add(pattern);
                }
            }
        }

        // A new top-level API route must be reviewed on purpose, not slip in unguarded.
        assertThat(unexpected).isEmpty();
    }
}
