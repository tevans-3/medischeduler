package dfm.medischeduler_routeservice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dfm.medischeduler_common.model.Student;
import dfm.medischeduler_common.model.Teacher;

import jakarta.validation.Valid;

/**
 * REST controller that receives student and teacher data from the frontend,
 * stores it in Redis, and kicks off the route-matrix computation pipeline.
 *
 * <h3>Endpoint</h3>
 * <pre>POST /matches</pre>
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Acquire a distributed lock so that only one upload per client
 *       is processed at a time.</li>
 *   <li>Store each student and teacher in Redis as both a hash map
 *       (for field-level access) and a JSON string (for efficient
 *       bulk retrieval via MGET).</li>
 *   <li>Generate all possible student-teacher pairings and publish
 *       each one to the {@code MATCH_TOPIC} Kafka topic.</li>
 *   <li>Store the upload total in Redis for progress tracking.</li>
 * </ol>
 */
@RestController
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RequestMapping("/matches")
public class MatchController {

    private static final Logger log = LoggerFactory.getLogger(MatchController.class);

    @Autowired
    private MatchGeneratorService matchGeneratorService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisLock redisLock;

    @Autowired
    private ObjectMapper mapper;

    /**
     * Receives a JSON payload containing student and teacher lists, stores
     * the data in Redis, and begins the matching pipeline.
     *
     * @param clientId the client identifier read from the {@code clientId} cookie
     * @param request  the validated request body containing students and teachers
     * @return 200 OK on success, 429 if another upload is in progress, 400 on error
     */
    @PostMapping
    public ResponseEntity<String> uploadMatches(
            @CookieValue("clientId") String clientId,
            @RequestBody @Valid MatchRequest request) {

        List<Student> students = request.getStudents();
        List<Teacher> teachers = request.getTeachers();

        try {
            boolean lock = redisLock.acquireLock(clientId, 5000L, 60000L);
            if (!lock) {
                log.info("Failed to acquire lock for client {}", clientId);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body("Upload in progress.");
            }
            log.info("Lock acquired for client {}, processing upload", clientId);
            handleStudentsUpload(students, clientId);
            handleTeachersUpload(teachers, clientId);
            setUploadTotalInRedis(students, teachers, clientId);

            // Match generation runs asynchronously; release lock when complete
            matchGeneratorService.generateMatches(students, teachers, clientId)
                    .whenComplete((result, ex) -> {
                        redisLock.releaseLock(clientId);
                        if (ex != null) {
                            log.error("Match generation failed for client {}: {}", clientId, ex.getMessage());
                        } else {
                            log.info("Match generation and lock release complete for client {}", clientId);
                        }
                    });
        } catch (Exception e) {
            redisLock.releaseLock(clientId);
            return ResponseEntity.badRequest()
                    .body("Error uploading matches: " + e.getMessage());
        }

        return ResponseEntity.ok(
                "Uploaded " + students.size() + " students and " + teachers.size() + " teachers.");
    }

    /** Stores all students in Redis. */
    private void handleStudentsUpload(List<Student> students, String clientId) {
        for (Student student : students) {
            setStudentInRedis(student, clientId);
        }
    }

    /** Stores all teachers in Redis. */
    private void handleTeachersUpload(List<Teacher> teachers, String clientId) {
        for (Teacher teacher : teachers) {
            setTeacherInRedis(teacher, clientId);
        }
    }

    /**
     * Stores a student in Redis as both a hash map (for field-level access)
     * and a JSON string (for efficient bulk retrieval).
     *
     * @param student  the student to store
     * @param clientId the client identifier for key namespacing
     */
    private void setStudentInRedis(Student student, String clientId) {
        String key = clientId + ":student:" + student.getId();

        try {
            String jsonSpecialtyInterests = mapper.writeValueAsString(student.getSpecialtyInterests());
            String jsonWeightedPreferences = mapper.writeValueAsString(student.getWeightedPreferences());

            Map<String, String> values = new HashMap<>();
            values.put("id", student.getId());
            values.put("address", student.getAddress());
            values.put("firstName", student.getFirstName());
            values.put("lastName", student.getLastName());
            values.put("email", student.getEmail());
            values.put("travelMethod", student.getTravelMethod());
            values.put("specialtyInterests", jsonSpecialtyInterests);
            values.put("weightedPreferences", jsonWeightedPreferences);
            values.put("sessionNum", student.getSessionNum());

            redisTemplate.opsForHash().putAll(key, values);

            // store as JSON string for efficient batch lookups by the scheduler service
            String jsonStudent = mapper.writeValueAsString(student);
            redisTemplate.opsForValue().set(clientId + ":serStudent:" + student.getId(), jsonStudent);

            // add to the student ID set for enumeration
            redisTemplate.opsForSet().add(clientId + ":students", student.getId());
        } catch (JsonProcessingException ex) {
            log.error("Failed to store student data in redis: {}", ex.getMessage());
            throw new RuntimeException("Failed to store student data in redis", ex);
        }
    }

    /**
     * Stores a teacher in Redis as both a hash map and a JSON string.
     *
     * @param teacher  the teacher to store
     * @param clientId the client identifier for key namespacing
     */
    private void setTeacherInRedis(Teacher teacher, String clientId) {
        String key = clientId + ":teacher:" + teacher.getId();

        try {
            String jsonAvailability = mapper.writeValueAsString(teacher.getAvailability());
            String jsonSpecialtyInterests = mapper.writeValueAsString(teacher.getSpecialtyInterests());

            Map<String, String> values = new HashMap<>();
            values.put("id", teacher.getId());
            values.put("address", teacher.getAddress());
            values.put("firstName", teacher.getFirstName());
            values.put("lastName", teacher.getLastName());
            values.put("email", teacher.getEmail());
            values.put("availability", jsonAvailability);
            values.put("specialtyInterests", jsonSpecialtyInterests);

            redisTemplate.opsForHash().putAll(key, values);

            // store as JSON string for efficient batch lookups
            String jsonTeacher = mapper.writeValueAsString(teacher);
            redisTemplate.opsForValue().set(clientId + ":serTeacher:" + teacher.getId(), jsonTeacher);

            // add to the teacher ID set for enumeration
            redisTemplate.opsForSet().add(clientId + ":teachers", teacher.getId());
        } catch (JsonProcessingException ex) {
            log.error("Failed to store teacher data in redis: {}", ex.getMessage());
            throw new RuntimeException("Failed to store teacher data in redis", ex);
        }
    }

    /**
     * Stores the upload totals in Redis so that the progress tracker and
     * the scheduler service can determine when all routes have been computed.
     *
     * @param students the list of uploaded students
     * @param teachers the list of uploaded teachers
     * @param clientId the client identifier
     */
    private void setUploadTotalInRedis(List<Student> students, List<Teacher> teachers, String clientId) {
        int teacherCount = teachers.size();
        int studentCount = students.size();
        int totalCount = teacherCount * studentCount;

        Map<String, String> progressData = new HashMap<>();
        progressData.put("teacherCount", String.valueOf(teacherCount));
        progressData.put("studentCount", String.valueOf(studentCount));
        progressData.put("totalCount", String.valueOf(totalCount));
        progressData.put("processedSoFar", "0");
        progressData.put("failedSoFar", "0");
        progressData.put("notFoundSoFar", "0");

        String key = clientId + ":routesProgress";
        redisTemplate.opsForHash().putAll(key, progressData);
    }
}
