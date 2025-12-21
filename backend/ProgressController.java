package dfm.medischeduler; 

import org.slf4j.Logger; 
import org.slf4j.LoggerFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.RedisZSET; 
import org.springframework.data.redis.core.RedisHSET; 

@Component
public class redisAPI {
    @Autowired
    public RedisTemplate<String, Object> redisTemplate;

    @Autowired
    ObjectMapper mapper; 

    private static final Logger log = LoggerFactory.getLogger(redisAPI.class);

    public Integer getTotalCount(String clientId){
        return Integer.parseInt(redisTemplate.opsForHash(clientId+":routesProgress", "totalCount")); 
    }
    
    public Integer getProcessedCount(String clientId){
        return Integer.parseInt(redisTemplate.opsForHash(clientId+":routesProgress", "processedSoFar"));
    }

    public Integer getFailedCount(String clientId){
        return Integer.parseInt(redisTemplate.opsForHash(clientId+":routesProgress", "failedSoFar"));
    }

    public Integer getNotFoundCount(String clientId){
        return Integer.parseInt(redisTemplate.opsForHash(clientId+":routesProgress", "notFoundSoFar"));
    }
}

@RestController
@RequestMapping("/running")
public class ProgressTracker { 

    @Autowired
    private redisAPI redisApi; 

    private String clientId; 

    public setClientId(String clientId){
        this.clientId = clientId; 
    };

    @GetMapping
    private Integer getTotalProgress(){
        Integer totalUploadCount = redisApi.getTotalCount();
        Integer processedCount = redisApi.getProcessedCount(); 
        Integer failedCount = redisApi.getFailedCount(); 
        Integer notFoundCount = redisApi.getNotFoundCount();

        Integer totalSoFar = processedCount + failedCount + notFoundCount;

        this.elemAwaitingProcessing = totalUploadCount - totalSoFar;

        ArrayList<Integer> counts = { totalUploadCount, 
                                  processedCount, 
                                  failedCount, 
                                  notFoundCount
                                }; 
        return counts; 
    }
}