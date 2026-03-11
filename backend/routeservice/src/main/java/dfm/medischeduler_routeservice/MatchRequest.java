package dfm.medischeduler_routeservice;

import java.util.List;

import dfm.medischeduler_common.model.Student;
import dfm.medischeduler_common.model.Teacher;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for the match upload endpoint.
 *
 * Contains two lists: one of {@link Student} objects and one of
 * {@link Teacher} objects, both parsed from the CSV files uploaded
 * by the administrator in the frontend.
 */
public class MatchRequest {

    @NotNull
    @Valid
    private List<Student> students;

    @NotNull
    @Valid
    private List<Teacher> teachers;

    public List<Student> getStudents() {
        return students;
    }

    public List<Teacher> getTeachers() {
        return teachers;
    }

    public void setStudents(List<Student> students) {
        this.students = students;
    }

    public void setTeachers(List<Teacher> teachers) {
        this.teachers = teachers;
    }
}
