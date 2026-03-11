package dfm.medischeduler_routeservice;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes route-computation progress to the frontend.
 *
 * The frontend polls this endpoint while the scheduler is running to
 * display a progress bar showing how many routes have been computed,
 * how many failed, and how many had no valid path.
 *
 * <h3>Endpoint</h3>
 * <pre>GET /running</pre>
 *
 * <h3>Response</h3>
 * A JSON object with keys: {@code totalCount}, {@code processedSoFar},
 * {@code failedSoFar}, {@code notFoundSoFar}.
 */
@RestController
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RequestMapping("/running")
public class ProgressController {

    @Autowired
    private RedisAPI redisApi;

    /**
     * Returns the current route-computation progress for the given client.
     *
     * @param clientId the client identifier read from the {@code clientId} cookie
     * @return a map of progress counters
     */
    @GetMapping
    public ResponseEntity<Map<String, Integer>> getTotalProgress(
            @CookieValue("clientId") String clientId) {
        Integer totalCount = redisApi.getTotalCount(clientId);
        Integer processedCount = redisApi.getProcessedCount(clientId);
        Integer failedCount = redisApi.getFailedCount(clientId);
        Integer notFoundCount = redisApi.getNotFoundCount(clientId);

        Map<String, Integer> progress = new HashMap<>();
        progress.put("totalCount", totalCount);
        progress.put("processedSoFar", processedCount);
        progress.put("failedSoFar", failedCount);
        progress.put("notFoundSoFar", notFoundCount);

        return ResponseEntity.ok(progress);
    }
}
