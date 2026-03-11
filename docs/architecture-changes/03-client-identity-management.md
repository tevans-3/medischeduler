# Issue 3: Client Identity Management

## The Problem

The frontend hardcoded a client identifier:

```javascript
const clientId = 'default-client';
```

This value was sent as a custom HTTP header (`ClientId: default-client`) with every request. The backend used it to namespace all Redis keys (e.g., `default-client:student:123`).

Problems with this approach:

- **No multi-user support**: Every user shared the same namespace, so one user's upload would overwrite another's data.
- **Client-managed identity**: The frontend was responsible for generating and persisting the ID, but had no mechanism to do so.
- **Custom headers are fragile**: Custom headers like `ClientId` are non-standard, easy to forget, and require explicit CORS configuration.

## The Fix

### Backend: Cookie-Based Identity

A new `ClientController` handles identity:

```java
@GetMapping("/api/client-id")
public ResponseEntity<String> getClientId(
        @CookieValue(name = "clientId", required = false) String existingId,
        HttpServletResponse response) {

    String clientId = (existingId != null && !existingId.isBlank())
            ? existingId
            : UUID.randomUUID().toString();

    ResponseCookie cookie = ResponseCookie.from("clientId", clientId)
            .httpOnly(true)
            .path("/")
            .maxAge(86400)
            .sameSite("Lax")
            .build();
    response.addHeader("Set-Cookie", cookie.toString());

    return ResponseEntity.ok(clientId);
}
```

On the first request, a UUID is generated and set as an `HttpOnly` cookie. On subsequent requests, the existing cookie value is returned. The cookie is:

- **HttpOnly**: JavaScript cannot read it, reducing XSS risk.
- **SameSite=Lax**: Sent on same-site navigations and top-level cross-site GET requests, but not on cross-site POST/AJAX — a reasonable default.
- **maxAge=86400**: Expires after 24 hours, matching the ephemeral nature of the scheduling data.

### Backend: Cookie Instead of Header

All controllers switched from `@RequestHeader` to `@CookieValue`:

```java
// Before
@PostMapping
public ResponseEntity<String> uploadMatches(
        @RequestHeader("ClientId") String clientId, ...)

// After
@PostMapping
public ResponseEntity<String> uploadMatches(
        @CookieValue("clientId") String clientId, ...)
```

The `@CrossOrigin` annotations were updated with `allowCredentials = "true"` so browsers send cookies on cross-origin requests during development.

### Frontend: useClientId Hook

A React hook fetches the client ID on app initialization:

```javascript
export default function useClientId() {
  const [clientId, setClientId] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch('/api/client-id', { credentials: 'include' })
      .then((res) => res.text())
      .then((id) => { setClientId(id); setLoading(false); })
      .catch(() => setLoading(false));
  }, []);

  return { clientId, loading };
}
```

Components that need the client ID (like the WebSocket subscription in `Results.jsx`) call this hook and wait for `loading` to be `false` before connecting.

All `fetch()` calls now include `credentials: 'include'` so the browser sends the cookie automatically. No more manual `ClientId` headers.

## Key Concept: Why Cookies Over Headers for Identity

| Approach | Pros | Cons |
|----------|------|------|
| **Custom header** | Explicit, visible in code | Must be manually added to every request; not sent by WebSocket or browser navigation; requires client-side storage |
| **Cookie** | Automatically sent by browser on every request including WebSocket handshake; HttpOnly prevents XSS theft | Requires CORS `credentials` config; slightly less visible in code |
| **JWT in localStorage** | Portable across domains; can carry claims | Vulnerable to XSS; must be manually attached; overkill for session-scoped identity |

For this application, cookies are the best fit because:

1. The identity is ephemeral (24-hour scheduling session), not a long-lived auth token.
2. The WebSocket handshake needs the identity, and cookies are the only credential type sent automatically during WebSocket connection establishment.
3. There are no cross-domain requirements — the frontend and backend run on the same origin in production.

## CORS and Credentials

When `credentials: 'include'` is used in fetch, the browser requires the server's CORS response to include:

```
Access-Control-Allow-Credentials: true
Access-Control-Allow-Origin: http://localhost:5173   (not *)
```

Spring's `@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")` sets both headers. Note that `Access-Control-Allow-Origin: *` is **not allowed** when credentials are included — the origin must be explicit.
