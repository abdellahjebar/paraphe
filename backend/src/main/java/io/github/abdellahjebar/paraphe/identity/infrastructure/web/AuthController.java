package io.github.abdellahjebar.paraphe.identity.infrastructure.web;

import io.github.abdellahjebar.paraphe.identity.application.AuthenticationResult;
import io.github.abdellahjebar.paraphe.identity.application.AuthenticationService;
import io.github.abdellahjebar.paraphe.identity.application.UserAdminService;
import io.github.abdellahjebar.paraphe.identity.domain.AppUser;
import io.github.abdellahjebar.paraphe.identity.domain.UserId;
import io.github.abdellahjebar.paraphe.shared.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sign in, sign out, who am I, change my password. */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthenticationService authentication;
    private final UserAdminService users;
    private final SecurityContextRepository securityContextRepository;

    AuthController(AuthenticationService authentication, UserAdminService users,
                   SecurityContextRepository securityContextRepository) {
        this.authentication = authentication;
        this.users = users;
        this.securityContextRepository = securityContextRepository;
    }

    record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 200) String password) {
    }

    record PasswordChangeRequest(@NotBlank @Size(max = 200) String currentPassword,
                                 @NotBlank @Size(max = 200) String newPassword) {
    }

    record UserResponse(UUID id, String email, String displayName, boolean mustChangePassword) {
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                            HttpServletResponse response) {
        AuthenticationResult result = authentication.authenticate(body.email(), body.password(),
                request.getRemoteAddr());
        return switch (result.outcome()) {
            case LOCKED -> problem(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, try again later");
            case INVALID_CREDENTIALS -> problem(HttpStatus.UNAUTHORIZED, "Invalid email or password");
            case SUCCESS -> {
                signIn(result.user(), request, response);
                yield ResponseEntity.ok(toResponse(principalOf(result.user())));
            }
        };
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return toResponse(user);
    }

    @PostMapping("/password")
    ResponseEntity<UserResponse> changePassword(@Valid @RequestBody PasswordChangeRequest body,
                                                @AuthenticationPrincipal AuthenticatedUser user,
                                                HttpServletRequest request, HttpServletResponse response) {
        users.changePassword(new UserId(user.userId()), body.currentPassword(), body.newPassword());
        AppUser refreshed = users.find(new UserId(user.userId())).orElseThrow();
        // Same session, new principal: the "must change password" restriction is lifted at once.
        saveAuthentication(refreshed, request, response);
        return ResponseEntity.ok(toResponse(principalOf(refreshed)));
    }

    private void signIn(AppUser user, HttpServletRequest request, HttpServletResponse response) {
        // A session id issued before sign-in must not survive it (session fixation).
        request.getSession(true);
        request.changeSessionId();
        saveAuthentication(user, request, response);
    }

    private void saveAuthentication(AppUser user, HttpServletRequest request, HttpServletResponse response) {
        AuthenticatedUser principal = principalOf(user);
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(
                user.mustChangePassword() ? "ROLE_PASSWORD_CHANGE_REQUIRED" : "ROLE_USER"));
        Authentication token = UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(token);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private static AuthenticatedUser principalOf(AppUser user) {
        return new AuthenticatedUser(user.id().value(), user.email(), user.displayName(),
                user.mustChangePassword());
    }

    private static UserResponse toResponse(AuthenticatedUser user) {
        return new UserResponse(user.userId(), user.email(), user.displayName(), user.mustChangePassword());
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title) {
        ProblemDetail detail = ProblemDetail.forStatus(status);
        detail.setTitle(title);
        return ResponseEntity.status(status).body(detail);
    }
}
