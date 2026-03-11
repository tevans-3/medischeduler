package dfm.medischeduler_schedulerservice;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dfm.medischeduler_common.model.Assignment;
import dfm.medischeduler_common.model.Student;
import dfm.medischeduler_common.model.Teacher;

/**
 * REST controller that serves computed assignments as a fallback when
 * the WebSocket connection is unavailable or the user refreshes the page.
 *
 * Reads the optimal assignments from Redis and returns them as JSON.
 */
@RestController
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RequestMapping("/api/assignments")
public class AssignmentController {

    private static final Logger log = LoggerFactory.getLogger(AssignmentController.class);

    @Autowired
    private RedisAPI redisApi;

    /**
     * Returns all computed assignments for the given client.
     *
     * @param clientId the client identifier from the cookie
     * @return list of assignments, or an empty list if none exist
     */
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

        log.info("Returning {} assignments for client {}", assignments.size(), clientId);
        return ResponseEntity.ok(assignments);
    }
}
