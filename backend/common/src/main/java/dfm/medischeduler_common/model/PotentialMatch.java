package dfm.medischeduler_common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;

/**
 * Represents a potential student-teacher pairing to be evaluated by the
 * route matrix computation.
 *
 * Each {@code PotentialMatch} stores the IDs of the student and teacher
 * so that the pairing can be serialized to JSON, sent through Kafka, and
 * later resolved against data cached in Redis.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PotentialMatch {

    @NotNull private String studentId;
    @NotNull private String teacherId;

    public PotentialMatch() {}

    public PotentialMatch(Student student, Teacher teacher) {
        this.studentId = student.getId();
        this.teacherId = teacher.getId();
    }

    public String getStudentId() { return studentId; }
    public String getTeacherId() { return teacherId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public void setTeacherId(String teacherId) { this.teacherId = teacherId; }
}
