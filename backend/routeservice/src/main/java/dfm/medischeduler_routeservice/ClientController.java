package dfm.medischeduler_routeservice;

import java.util.UUID;

import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

/**
 * REST controller that manages client identity.
 *
 * On the first request to {@code GET /api/client-id}, a new UUID is generated
 * and returned in both the response body and an {@code HttpOnly} cookie named
 * {@code clientId}. Subsequent requests return the same ID from the cookie.
 */
@RestController
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RequestMapping("/api/client-id")
public class ClientController {

    /**
     * Returns the current client ID, generating a new one if none exists.
     *
     * @param existingId the client ID from the cookie, or {@code null}
     * @param response   the HTTP response to set the cookie on
     * @return the client ID as a plain-text response
     */
    @GetMapping
    public ResponseEntity<String> getClientId(
            @CookieValue(name = "clientId", required = false) String existingId,
            HttpServletResponse response) {

        String clientId = (existingId != null && !existingId.isBlank())
                ? existingId
                : UUID.randomUUID().toString();

        ResponseCookie cookie = ResponseCookie.from("clientId", clientId)
                .httpOnly(true)
                .path("/")
                .maxAge(86400) // 24 hours
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());

        return ResponseEntity.ok(clientId);
    }
}
