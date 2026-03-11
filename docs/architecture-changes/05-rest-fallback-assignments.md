# Issue 5: REST Fallback for Assignments

## The Problem

Assignments were delivered exclusively via WebSocket (STOMP over SockJS). This meant:

- **Page refresh = data loss**: If the user refreshed the browser after assignments arrived, the WebSocket subscription was torn down and there was no way to retrieve the data again.
- **Connection failures**: If the WebSocket connection failed (network issues, proxy interference, corporate firewalls blocking WebSocket upgrades), the user had no fallback.
- **No bookmarkability**: The results page couldn't be shared or revisited — it only worked if you were connected at the exact moment assignments were published.

The assignments were already stored in Redis (by the solver), but there was no HTTP endpoint to read them.

## The Fix

A single REST endpoint was added to the schedulerservice:

```java
@RestController
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RequestMapping("/api/assignments")
public class AssignmentController {

    @Autowired
    private RedisAPI redisApi;

    @GetMapping
    public ResponseEntity<List<Assignment>> getAssignments(
            @CookieValue("clientId") String clientId) {

        List<Assignment> assignments = new ArrayList<>();
        List<Student> students = redisApi.getAllStudents(clientId);

        for (Student student : students) {
            String teacherId = redisApi.getOptimalAssignment(clientId, student.getId());
            if (teacherId != null) {
                Teacher teacher = redisApi.getTeacher("teacher:" + teacherId, clientId);
                if (teacher != null) {
                    assignments.add(new Assignment(student, teacher));
                }
            }
        }

        return ResponseEntity.ok(assignments);
    }
}
```

The frontend's `Results.jsx` now fetches from this endpoint on mount:

```javascript
useEffect(() => {
  fetch('/api/assignments', { credentials: 'include' })
    .then((res) => res.ok ? res.json() : [])
    .then((data) => {
      if (Array.isArray(data) && data.length > 0) {
        setAssignments(data);
      }
    });
}, [clientId]);
```

If assignments already exist in Redis (from a previous solver run), they appear immediately. If not, the page waits for the WebSocket to deliver them in real time.

## Key Concept: WebSocket + REST Dual Strategy

WebSocket and REST serve complementary purposes:

| Capability | REST | WebSocket |
|-----------|------|-----------|
| Real-time push | No (requires polling) | Yes |
| Works through proxies/firewalls | Always | Sometimes blocked |
| Survives page refresh | Yes (stateless) | No (connection lost) |
| Cacheable | Yes (HTTP caching) | No |
| Idempotent retrieval | Yes (GET is safe) | N/A |

The ideal pattern for this kind of application is:

1. **REST for initial load**: When the page mounts, `GET /api/assignments` returns whatever data exists. This handles page refreshes, bookmarks, and WebSocket failures.

2. **WebSocket for real-time updates**: While the page is open, a STOMP subscription receives new assignments as soon as they're generated. This provides the "live" experience without polling.

3. **No duplication concern**: If both channels deliver the same data, the frontend simply calls `setAssignments(data)` — React's state setter is idempotent, so rendering the same data twice is harmless.

### Why Not Just Poll?

You could replace WebSocket with a polling loop (`setInterval` + `fetch`), but:

- Polling introduces latency (you wait up to the polling interval for updates).
- Polling wastes bandwidth (most requests return "no change").
- Polling scales poorly (N clients x M polls/minute = N*M requests/minute).

WebSocket delivers updates with zero latency and zero wasted requests. The REST endpoint is purely a fallback for resilience, not a replacement for the real-time channel.
