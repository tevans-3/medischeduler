package dfm.medischeduler_routeservice;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * REST controller handling Google OAuth2 authentication.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /api/auth/google} — verifies a Google ID token and
 *       creates an HTTP session.</li>
 *   <li>{@code GET /api/auth/me} — returns the authenticated user's
 *       profile (email, name, picture) from the session.</li>
 *   <li>{@code POST /api/auth/logout} — invalidates the session.</li>
 * </ul>
 */
@RestController
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Value("${google.client-id}")
    private String googleClientId;

    /**
     * Verifies a Google ID token and creates an authenticated session.
     *
     * @param body    JSON body containing {@code credential} (the ID token string)
     * @param request the HTTP request (used to create/get the session)
     * @return the user's profile on success, or 401 on invalid token
     */
    @PostMapping("/google")
    public ResponseEntity<?> authenticateWithGoogle(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        String idTokenString = body.get("credential");
        if (idTokenString == null || idTokenString.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing credential"));
        }

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                log.warn("Invalid Google ID token received");
                return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");

            // Set Spring Security authentication in the SecurityContext
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            email, null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER")));
            authentication.setDetails(Map.of(
                    "name", name != null ? name : "",
                    "picture", picture != null ? picture : ""
            ));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Persist the SecurityContext in the HTTP session
            HttpSession session = request.getSession(true);
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext());
            session.setAttribute("email", email);
            session.setAttribute("name", name);
            session.setAttribute("picture", picture);
            session.setMaxInactiveInterval(86400); // 24 hours

            log.info("User authenticated: {} ({})", name, email);

            return ResponseEntity.ok(Map.of(
                    "email", email != null ? email : "",
                    "name", name != null ? name : "",
                    "picture", picture != null ? picture : ""
            ));

        } catch (Exception e) {
            log.error("Google token verification failed: {}", e.getMessage());
            return ResponseEntity.status(401).body(Map.of("error", "Token verification failed"));
        }
    }

    /**
     * Returns the currently authenticated user's profile.
     *
     * @param request the HTTP request
     * @return user profile or 401 if not authenticated
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("authenticated", false));
        }

        HttpSession session = request.getSession(false);
        String email = auth.getName();
        String name = "";
        String picture = "";
        if (session != null) {
            Object n = session.getAttribute("name");
            Object p = session.getAttribute("picture");
            if (n != null) name = n.toString();
            if (p != null) picture = p.toString();
        }

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "email", email != null ? email : "",
                "name", name,
                "picture", picture
        ));
    }

    /**
     * Logs out the current user by invalidating their session.
     *
     * @param request the HTTP request
     * @return 200 OK
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }
}
