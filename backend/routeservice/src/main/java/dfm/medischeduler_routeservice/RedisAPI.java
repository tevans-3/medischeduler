package dfm.medischeduler_routeservice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import dfm.medischeduler_common.model.Student;
import dfm.medischeduler_common.model.Teacher;

/**
 * Data-access layer for all Redis operations in the Route Service.
 *
 * Provides helpers for caching student/teacher data, route computation
 * results, batch metadata, global rate-limit state, and index maps that
 * link Google Routes API response indices back to student/teacher IDs.
 *
 * All keys are namespaced by {@code clientId} so that concurrent
 * scheduling runs from different clients are isolated.
 */
@Component
public class RedisAPI {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper mapper;

    private static final Logger log = LoggerFactory.getLogger(RedisAPI.class);

    // ---------------------------------------------------------------
    // Progress tracking
    // ---------------------------------------------------------------

    /**
     * Returns the total number of potential matches uploaded for this client.
     *
     * @param clientId the client identifier
     * @return the total match count
     */
    public Integer getTotalCount(String clientId) {
        Object val = redisTemplate.opsForHash().get(clientId + ":routesProgress", "totalCount");
        return val != null ? Integer.parseInt(val.toString()) : 0;
    }

    /** Returns the number of routes successfully processed so far. */
    public Integer getProcessedCount(String clientId) {
        Object val = redisTemplate.opsForHash().get(clientId + ":routesProgress", "processedSoFar");
        return val != null ? Integer.parseInt(val.toString()) : 0;
    }

    /** Returns the number of route computations that failed. */
    public Integer getFailedCount(String clientId) {
        Object val = redisTemplate.opsForHash().get(clientId + ":routesProgress", "failedSoFar");
        return val != null ? Integer.parseInt(val.toString()) : 0;
    }

    /** Returns the number of routes where no valid path was found. */
    public Integer getNotFoundCount(String clientId) {
        Object val = redisTemplate.opsForHash().get(clientId + ":routesProgress", "notFoundSoFar");
        return val != null ? Integer.parseInt(val.toString()) : 0;
    }

    /**
     * Increments a progress counter (e.g. "processedSoFar") by the given amount.
     *
     * @param category the counter name within the routesProgress hash
     * @param amount   the amount to increment by
     * @param clientId the client identifier
     */
    public void updateRoutesProgress(String category, Integer amount, String clientId) {
        redisTemplate.opsForHash().increment(clientId + ":routesProgress", category, amount);
    }

    // ---------------------------------------------------------------
    // Student and Teacher lookup
    // ---------------------------------------------------------------

    /**
     * Retrieves a {@link Student} from Redis by deserializing the hash
     * stored at {@code clientId:studentId}.
     *
     * @param studentId the student key suffix
     * @param clientId  the client identifier prefix
     * @return the deserialized Student, or {@code null} if not found
     */
    public Student getStudent(String studentId, String clientId) {
        Map<Object, Object> data = redisTemplate.opsForHash().entries(clientId + ":" + studentId);
        if (data == null || data.isEmpty()) {
            return null;
        }
        // specialtyInterests and weightedPreferences are stored as JSON strings
        // in the Redis hash — parse them back into Maps before converting.
        try {
            Object si = data.get("specialtyInterests");
            if (si instanceof String) {
                data.put("specialtyInterests", mapper.readValue((String) si, java.util.HashMap.class));
            }
            Object wp = data.get("weightedPreferences");
            if (wp instanceof String) {
                data.put("weightedPreferences", mapper.readValue((String) wp, java.util.HashMap.class));
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to parse student map fields for {}: {}", studentId, e.getMessage());
            return null;
        }
        return mapper.convertValue(data, Student.class);
    }

    /**
     * Retrieves a {@link Teacher} from Redis by deserializing the hash
     * stored at {@code clientId:teacherId}.
     *
     * @param teacherId the teacher key suffix
     * @param clientId  the client identifier prefix
     * @return the deserialized Teacher, or {@code null} if not found
     */
    public Teacher getTeacher(String teacherId, String clientId) {
        Map<Object, Object> data = redisTemplate.opsForHash().entries(clientId + ":" + teacherId);
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            Object av = data.get("availability");
            if (av instanceof String) {
                data.put("availability", mapper.readValue((String) av, java.util.HashMap.class));
            }
            Object si = data.get("specialtyInterests");
            if (si instanceof String) {
                data.put("specialtyInterests", mapper.readValue((String) si, java.util.HashMap.class));
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to parse teacher map fields for {}: {}", teacherId, e.getMessage());
            return null;
        }
        return mapper.convertValue(data, Teacher.class);
    }

    /**
     * Looks up a student's address from Redis.
     *
     * @param studentId the student key (e.g. "student:S001")
     * @param clientId  the client identifier
     * @return the address string, or {@code null} if the student is not found
     */
    public String getStudentAddress(String studentId, String clientId) {
        Student student = getStudent(studentId, clientId);
        if (student == null) return null;
        return student.getAddress();
    }

    /**
     * Looks up a teacher's address from Redis.
     *
     * @param teacherId the teacher key (e.g. "teacher:T001")
     * @param clientId  the client identifier
     * @return the address string, or {@code null} if the teacher is not found
     */
    public String getTeacherAddress(String teacherId, String clientId) {
        Teacher teacher = getTeacher(teacherId, clientId);
        if (teacher == null) return null;
        return teacher.getAddress();
    }

    // ---------------------------------------------------------------
    // Route data caching
    // ---------------------------------------------------------------

    /**
     * Caches a computed route (distance and duration) between a student and teacher.
     *
     * The data is stored as a Redis hash keyed by
     * {@code clientId:route:student:studentId:teacher:teacherId}, and the
     * key is also added to the {@code clientId:computedRoutes} set for
     * later enumeration by the Scheduler Service.
     *
     * @param clientId   the client identifier
     * @param studentId  the student ID
     * @param teacherId  the teacher ID
     * @param meters     the route distance in meters
     * @param durationSeconds the route duration in seconds
     * @param travelMode the travel mode (DRIVE, TRANSIT, BIKE, WALK)
     */
    public void cacheRouteData(String clientId, String studentId, String teacherId,
                               Long meters, long durationSeconds, String travelMode) {
        String studentKey = ":student:" + studentId;
        String teacherKey = ":teacher:" + teacherId;
        String key = clientId + ":route" + studentKey + teacherKey;

        HashMap<String, String> routeData = new HashMap<>();
        routeData.put("studentId", studentId);
        routeData.put("teacherId", teacherId);
        routeData.put("travelMode", travelMode);
        routeData.put("distance", String.valueOf(meters));
        routeData.put("duration", String.valueOf(durationSeconds));

        // store route key in set so the scheduler service can enumerate all routes later
        redisTemplate.opsForSet().add(clientId + ":computedRoutes", key);

        // store the route data hash
        redisTemplate.opsForHash().putAll(key, routeData);
    }

    // ---------------------------------------------------------------
    // Index maps: link Routes API response indices to student/teacher IDs
    // ---------------------------------------------------------------

    /**
     * Maps a student's position in the origins array to their ID for a given batch,
     * so that when the Routes API response arrives we can look up which student
     * corresponds to each origin index.
     *
     * @param clientId  the client identifier
     * @param studentId the student ID
     * @param origins   the ordered list of origin addresses in the batch
     * @param batchId   the batch identifier
     */
    public void cacheIndexMapsStudentToResponse(String clientId, String studentId,
                                                ArrayList<String> origins, String batchId) {
        String studentAddress = getStudentAddress("student:" + studentId, clientId);
        int addressIndex = origins.indexOf(studentAddress);
        redisTemplate.opsForHash().put(
                clientId + ":originIndex:" + addressIndex + ":" + batchId,
                "studentId", studentId);
    }

    /**
     * Maps a teacher's position in the destinations array to their ID for a given batch.
     *
     * @param clientId  the client identifier
     * @param teacherId the teacher ID
     * @param destinations the ordered list of destination addresses in the batch
     * @param batchId   the batch identifier
     */
    public void cacheIndexMapsTeacherToResponse(String clientId, String teacherId,
                                                ArrayList<String> destinations, String batchId) {
        String teacherAddress = getTeacherAddress("teacher:" + teacherId, clientId);
        int addressIndex = destinations.indexOf(teacherAddress);
        redisTemplate.opsForHash().put(
                clientId + ":destinationIndex:" + addressIndex + ":" + batchId,
                "teacherId", teacherId);
    }

    /**
     * Resolves a destination index from a Routes API response back to a teacher ID.
     *
     * @param destinationIndex the zero-based destination index from the API response
     * @param batchId          the batch that produced this response
     * @param clientId         the client identifier
     * @return the teacher ID, or {@code null} if not found
     */
    public String lookupTeacherByDestIndex(Integer destinationIndex, String batchId, String clientId) {
        Object val = redisTemplate.opsForHash().get(
                clientId + ":destinationIndex:" + destinationIndex + ":" + batchId,
                "teacherId");
        return val != null ? val.toString() : null;
    }

    /**
     * Resolves an origin index from a Routes API response back to a student ID.
     *
     * @param originIndex the zero-based origin index from the API response
     * @param batchId     the batch that produced this response
     * @param clientId    the client identifier
     * @return the student ID, or {@code null} if not found
     */
    public String lookupStudentByOriginIndex(Integer originIndex, String batchId, String clientId) {
        Object val = redisTemplate.opsForHash().get(
                clientId + ":originIndex:" + originIndex + ":" + batchId,
                "studentId");
        return val != null ? val.toString() : null;
    }

    // ---------------------------------------------------------------
    // Batch metadata
    // ---------------------------------------------------------------

    /**
     * Creates a new batch entry in Redis, recording its ID under the client's
     * batch metadata.
     *
     * @param batchId  the unique batch identifier
     * @param clientId the client identifier
     */
    public void createNewBatch(String batchId, String clientId) {
        String key = clientId + ":batchMetadata:currentBatchId:" + batchId;
        Map<Object, Object> metadata = redisTemplate.opsForHash().entries(key);
        if (!metadata.isEmpty()) {
            log.error("Batch {} already exists under that id.", batchId);
        } else {
            redisTemplate.opsForHash().put(clientId + ":batchMetadata", "currentBatchId", batchId);
        }
    }

    /**
     * Sets an attribute on batch-level metadata (e.g. "status", "processingStartTime").
     *
     * @param batchId   the batch identifier
     * @param attrToSet the attribute name
     * @param attrValue the attribute value
     * @param clientId  the client identifier
     */
    public void setBatchMetadata(String batchId, String attrToSet, String attrValue, String clientId) {
        if (!"status".equals(attrToSet) && !"processingStartTime".equals(attrToSet)) {
            log.warn("Invalid batch metadata attribute: {}. Expected 'status' or 'processingStartTime'.", attrToSet);
            return;
        }
        if (attrValue == null) {
            log.warn("{} set to NULL. Are you sure this is correct?", attrToSet);
        }
        redisTemplate.opsForHash().put(
                clientId + ":batchMetadata:currentBatchId:" + batchId, attrToSet, attrValue);
    }

    /**
     * Retrieves a batch metadata attribute.
     *
     * @param batchId  the batch identifier
     * @param attrToGet the attribute name (e.g. "status")
     * @param clientId the client identifier
     * @return the attribute value, or {@code null} if not found
     */
    public String getBatchMetadata(String batchId, String attrToGet, String clientId) {
        Object val = redisTemplate.opsForHash().get(
                clientId + ":batchMetadata:currentBatchId:" + batchId, attrToGet);
        return val != null ? val.toString() : null;
    }

    /** Increments a numeric batch metadata attribute by the specified amount. */
    public void incrementBatchMetadata(String batchId, String attributeToInc,
                                       Integer amountBy, String clientId) {
        String key = clientId + ":batchMetadata:currentBatchId:" + batchId;
        redisTemplate.opsForHash().increment(key, attributeToInc, amountBy);
    }

    /** Removes a batch's metadata from Redis after it has been fully processed. */
    public void removeBatchFromRedis(String batchId, String clientId) {
        redisTemplate.delete(clientId + ":batchMetadata:currentBatchId:" + batchId);
    }

    /** Returns the current batch ID for the given client. */
    public String getBatchId(String clientId) {
        Object val = redisTemplate.opsForHash().get(clientId + ":batchMetadata", "currentBatchId");
        return val != null ? val.toString() : null;
    }

    // ---------------------------------------------------------------
    // Global state metadata (rate-limit tracking)
    // ---------------------------------------------------------------

    /**
     * Sets a global state attribute used for rate-limit tracking.
     * Valid attributes: "elemProcessedInLastMin", "processingStartTime".
     *
     * @param attrToSet the attribute name
     * @param attrValue the attribute value
     */
    public void setGlobalStateMetadata(String attrToSet, String attrValue) {
        if (!"elemProcessedInLastMin".equals(attrToSet) && !"processingStartTime".equals(attrToSet)) {
            log.warn("Invalid global state attribute: {}.", attrToSet);
            return;
        }
        redisTemplate.opsForHash().put("globalStateMetadata", attrToSet, attrValue);
    }

    /** Increments a numeric global state attribute. */
    public void incrementGlobalStateMetadata(String attributeToInc, Integer amountBy) {
        redisTemplate.opsForHash().increment("globalStateMetadata", attributeToInc, amountBy);
    }

    /**
     * Retrieves a global state attribute.
     *
     * @param attrToGet the attribute name
     * @return the attribute value, or {@code null} if not found
     */
    public String getGlobalStateMetadata(String attrToGet) {
        Object val = redisTemplate.opsForHash().get("globalStateMetadata", attrToGet);
        return val != null ? val.toString() : null;
    }

    // ---------------------------------------------------------------
    // Value caching (for messages received while rate-limited)
    // ---------------------------------------------------------------

    /** Caches a value for later processing when the current batch is full. */
    public void cacheValueForProcessing(String value, String clientId) {
        if (value == null) {
            log.warn("Cached value is null. Are you sure this is correct?");
        }
        redisTemplate.opsForSet().add(clientId + ":cachedValues", value);
    }

    /** Removes a previously cached value after it has been processed. */
    public void removeFromCache(String value, String clientId) {
        redisTemplate.opsForSet().remove(clientId + ":cachedValues", value);
    }

    /** Returns all cached values awaiting processing for the given client. */
    @SuppressWarnings("unchecked")
    public Set<Object> getCachedValues(String clientId) {
        return redisTemplate.opsForSet().members(clientId + ":cachedValues");
    }
}
