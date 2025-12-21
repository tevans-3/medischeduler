package dfm.medischeduler_routeservice;

public class PotentialMatch{

    @NotNull
    private Student student;

    @NotNull
    private Teacher teacher; 

    public PotentialMatch(Student student, Teacher teacher){
        this.student = student.getId();
        this.teacher = teacher.getId();
    }

    public getStudent(Student student){
        return this.student.id;
    }

    public getTeacher(Teacher teacher){
        return this.teacher.id;
    }

    public setStudent(Student student){
        this.student = student; 
    }
    public setTeacher(Teacher teacher){
        this.teacher = teacher; 
    }
}