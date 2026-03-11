package dfm.medischeduler_schedulerservice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dfm.medischeduler_common.model.Student;
import dfm.medischeduler_common.model.Teacher;

/**
 * Data-access layer for all Redis operations in the Scheduler Service.
 *
 * Provides methods to retrieve route matrix data, student/teacher
 * information, progress counters, and solver index maps. Also handles
 * storing the final optimal assignments.
 */
@Component
public class RedisAPI {

    private static final Logger log = LoggerFactory.getLogger(RedisAPI.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper mapper;

    // ---------------------------------------------------------------
    // Progress tracking
    // ---------------------------------------------------------------

    /** Returns the total number of potential matches uploaded. */
    public Integer getTotalCount(String clientId) {
        Object val = redisTemplate.opsForHash().get(clientId + ":routesProgress", "totalCount");
        return val != null ? Integer.parseInt(val.toString()) : 0;
    }

    /** Returns the number of routes successfully processed. */
    public Integer getProcessedCount(String clientId) {
        Object val = redisTemplate.opsForHash().get(clientId + ":routesProgress", "processedSoFar");
        return val != null ? Integer.parseInt(val.toString()) : 0;
    }

    /** Returns the number of failed route computations. */
    public Integer getFailedCount(String clientId) {
        Object val = redisTemplate.opsForHash().get(clientId + ":routesProgress", "failedSoFar");
        return val != null ? Integer.parseInt(val.toString()) : 0;
    }

    /** Returns the number of routes where no valid path was found. */
    public Integer getNotFoundCount(String clientId) {
        Object val = redisTemplate.opsForHash().get(clientId + ":routesProgress", "notFoundSoFar");
        return val != null ? Integer.parseInt(val.toString()) : 0;
    }

    // ---------------------------------------------------------------
    // Counts and IDs
    // ---------------------------------------------------------------

    /** Returns the number of students uploaded for this client. */
    public Integer getNumStudents(String clientId) {
        Object val = redisTemplate.opsForHash().get(clientId + ":routesProgress", "studentCount");
        return val != null ? Integer.parseInt(val.toString()) : 0;
    }

    /** Returns the number of teachers uploaded for this client. */
    public Integer getNumTeachers(String clientId) {
        Object val = redisTemplate.opsForHash().get(clientId + ":routesProgress", "teacherCount");
        return val != null ? Integer.parseInt(val.toString()) : 0;
    }

    /** Returns the set of all student IDs for this client. */
    @SuppressWarnings("unchecked")
    public Set<Object> getStudentIds(String clientId) {
        return redisTemplate.opsForSet().members(clientId + ":students");
    }

    /** Returns the set of all teacher IDs for this client. */
    @SuppressWarnings("unchecked")
    public Set<Object> getTeacherIds(String clientId) {
        return redisTemplate.opsForSet().members(clientId + ":teachers");
    }

    // ---------------------------------------------------------------
    // Student and Teacher retrieval
    // ---------------------------------------------------------------

    /** Retrieves a single student by their ID. */
    public Student getStudent(String studentId, String clientId) {
        Map<Object, Object> data = redisTemplate.opsForHash().entries(clientId + ":" + studentId);
        if (data == null || data.isEmpty()) return null;
        try {
            Object si = data.get("specialtyInterests");
            if (si instanceof String) {
                data.put("specialtyInterests", mapper.readValue((String) si, java.util.HashMap.class));
            }
            Object wp = data.get("weightedPreferences");
            if (wp instanceof String) {
                data.put("weightedPreferences", mapper.readValue((String) wp, java.util.HashMap.class));
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse student map fields for {}: {}", studentId, e.getMessage());
            return null;
        }
        return mapper.convertValue(data, Student.class);
    }

    /** Retrieves a single teacher by their ID. */
    public Teacher getTeacher(String teacherId, String clientId) {
        Map<Object, Object> data = redisTemplate.opsForHash().entries(clientId + ":" + teacherId);
        if (data == null || data.isEmpty()) return null;
        try {
            Object av = data.get("availability");
            if (av instanceof String) {
                data.put("availability", mapper.readValue((String) av, java.util.HashMap.class));
            }
            Object si = data.get("specialtyInterests");
            if (si instanceof String) {
                data.put("specialtyInterests", mapper.readValue((String) si, java.util.HashMap.class));
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse teacher map fields for {}: {}", teacherId, e.getMessage());
            return null;
        }
        return mapper.convertValue(data, Teacher.class);
    }

    /**
     * Retrieves all students by reading their serialized JSON strings
     * from Redis and deserializing each one.
     *
     * @param clientId the client identifier
     * @return list of all students for this client
     */
    public List<Student> getAllStudents(String clientId) {
        Set<Object> ids = getStudentIds(clientId);
        if (ids == null || ids.isEmpty()) return new ArrayList<>();

        List<Student> students = new ArrayList<>();
        for (Object id : ids) {
            Object json = redisTemplate.opsForValue().get(clientId + ":serStudent:" + id.toString());
            if (json != null) {
                try {
                    students.add(mapper.readValue(json.toString(), Student.class));
                } catch (JsonProcessingException e) {
                    log.error("Failed to deserialize student {}: {}", id, e.getMessage());
                }
            }
        }
        return students;
    }

    /**
     * Retrieves all teachers by reading their serialized JSON strings.
     *
     * @param clientId the client identifier
     * @return list of all teachers for this client
     */
    public List<Teacher> getAllTeachers(String clientId) {
        Set<Object> ids = getTeacherIds(clientId);
        if (ids == null || ids.isEmpty()) return new ArrayList<>();

        List<Teacher> teachers = new ArrayList<>();
        for (Object id : ids) {
            Object json = redisTemplate.opsForValue().get(clientId + ":serTeacher:" + id.toString());
            if (json != null) {
                try {
                    teachers.add(mapper.readValue(json.toString(), Teacher.class));
                } catch (JsonProcessingException e) {
                    log.error("Failed to deserialize teacher {}: {}", id, e.getMessage());
                }
            }
        }
        return teachers;
    }

    // ---------------------------------------------------------------
    // Route matrix data
    // ---------------------------------------------------------------

    /**
     * Retrieves all computed route distances as a map from route key to
     * distance in meters.
     *
     * @param clientId the client identifier
     * @return map of route keys to distances
     */
    public Map<String, Long> getRouteMatrixData(String clientId) {
        Set<Object> routeKeys = redisTemplate.opsForSet().members(clientId + ":computedRoutes");
        Map<String, Long> routeMatrixData = new HashMap<>();

        if (routeKeys == null) return routeMatrixData;

        for (Object keyObj : routeKeys) {
            String key = keyObj.toString();
            Map<Object, Object> routeData = redisTemplate.opsForHash().entries(key);
            Object distStr = routeData.get("distance");
            if (distStr != null) {
                routeMatrixData.put(key, Long.parseLong(distStr.toString()));
            }
        }
        return routeMatrixData;
    }

    // ---------------------------------------------------------------
    // Solver index maps
    // ---------------------------------------------------------------

    /** Maps a student's solver index to their ID for later result extraction. */
    public void setIndexMapStudentToSolverResult(int i, String studentId, String clientId) {
        redisTemplate.opsForHash().put(clientId + ":studentIndex", String.valueOf(i), studentId);
    }

    /** Maps a teacher's solver index to their ID for later result extraction. */
    public void setIndexMapTeacherToSolverResult(int j, String teacherId, String clientId) {
        redisTemplate.opsForHash().put(clientId + ":teacherIndex", String.valueOf(j), teacherId);
    }

    /** Retrieves a student ID by their solver index. */
    public String getStudentFromIndex(int i, String clientId) {
        Object val = redisTemplate.opsForHash().get(clientId + ":studentIndex", String.valueOf(i));
        return val != null ? val.toString() : null;
    }

    /** Retrieves a teacher ID by their solver index. */
    public String getTeacherFromIndex(int j, String clientId) {
        Object val = redisTemplate.opsForHash().get(clientId + ":teacherIndex", String.valueOf(j));
        return val != null ? val.toString() : null;
    }

    // ---------------------------------------------------------------
    // Optimal assignments
    // ---------------------------------------------------------------

    /**
     * Stores an optimal assignment (student -> teacher) in Redis.
     *
     * @param clientId  the client identifier
     * @param studentId the assigned student ID
     * @param teacherId the assigned teacher ID
     */
    public void setOptimalAssignment(String clientId, String studentId, String teacherId) {
        redisTemplate.opsForHash().put(clientId + ":optimalAssignments", studentId, teacherId);
    }

    /**
     * Retrieves the teacher ID assigned to a given student.
     *
     * @param clientId  the client identifier
     * @param studentId the student ID
     * @return the assigned teacher ID, or {@code null} if not found
     */
    public String getOptimalAssignment(String clientId, String studentId) {
        Object val = redisTemplate.opsForHash().get(clientId + ":optimalAssignments", studentId);
        return val != null ? val.toString() : null;
    }
}
