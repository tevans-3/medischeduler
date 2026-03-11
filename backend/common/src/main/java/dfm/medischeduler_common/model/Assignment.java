package dfm.medischeduler_common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a finalized student-to-teacher assignment produced by the
 * OR-Tools CP-SAT solver.
 *
 * Instances are serialized to JSON and sent to the frontend via WebSocket
 * so the administrator can review and edit assignments on the map.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Assignment {

    private Student student;
    private Teacher teacher;

    public Assignment() {}

    public Assignment(Student student, Teacher teacher) {
        this.student = student;
        this.teacher = teacher;
    }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
    public Teacher getTeacher() { return teacher; }
    public void setTeacher(Teacher teacher) { this.teacher = teacher; }
}
