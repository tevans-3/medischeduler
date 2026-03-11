package dfm.medischeduler_schedulerservice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dfm.medischeduler_common.model.Student;
import dfm.medischeduler_common.model.Teacher;

import com.google.ortools.Loader;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import com.google.ortools.sat.Literal;

/**
 * Uses Google OR-Tools CP-SAT solver to generate optimal student-to-teacher
 * assignments based on commute distances and specialty preferences.
 *
 * <p>This class is a pure computational component with no I/O dependencies.
 * All required data (students, teachers, route matrix) must be provided by
 * the caller, and results are returned as an in-memory map. The caller is
 * responsible for loading input data from Redis and persisting the results.
 *
 * <h3>Objective Function</h3>
 * The solver maximizes a weighted sum of:
 * <ul>
 *   <li><b>Travel scores</b> &mdash; shorter commutes (by the student's
 *       preferred travel mode) contribute a higher score.</li>
 *   <li><b>Specialty matches</b> &mdash; when a student's specialty interest
 *       matches a teacher's specialty, the preference weight is added to the
 *       objective.</li>
 * </ul>
 *
 * <h3>Constraints</h3>
 * <ul>
 *   <li>Each student is assigned to exactly one teacher.</li>
 *   <li>Each teacher is assigned to at most one student per track.</li>
 *   <li>A student may only be assigned to a teacher who is available for
 *       that student's session/track number.</li>
 * </ul>
 */
@Component
public class ORToolsScheduler {

    private static final Logger log = LoggerFactory.getLogger(ORToolsScheduler.class);

    /** The specialty/preference names used in the objective function. */
    public static final List<String> PREFERENCE_NAMES = List.of(
            "shortTransitCommute", "shortCarCommute", "shortBikeCommute", "shortWalkCommute",
            "palliativeCare", "generalLgbtHealth", "dermatology",
            "reproductiveAndSexualHealth", "generalWomensHealth", "transHealth",
            "addictions", "mentalHealth", "inOfficeProcedures", "anesthesia",
            "vulnerablePopulations", "mensHealth", "geriatrics", "pediatrics",
            "mskSportsMed", "hospitalist", "occupational", "newcomer");

    static {
        Loader.loadNativeLibraries();
    }

    /**
     * Runs the CP-SAT solver for the given client's pre-loaded data.
     *
     * <p>All data is provided by the caller so that the solver performs no I/O
     * during the CPU-bound optimization phase. Index-to-ID mappings are kept
     * in local in-memory maps rather than external storage.
     *
     * <ol>
     *   <li>Builds the assignment matrix and availability constraints.</li>
     *   <li>Builds travel-distance and specialty-preference objective terms.</li>
     *   <li>Solves and extracts the optimal (or feasible) assignment.</li>
     * </ol>
     *
     * @param clientId        the client identifier (used for route-key lookups)
     * @param students        all students for this client
     * @param teachers        all teachers for this client
     * @param routeMatrixData pre-loaded map of route keys to distances
     * @return a map of studentId to teacherId for each assignment, or an
     *         empty map if the solver could not find a solution
     */
    public Map<String, String> runScheduler(String clientId, List<Student> students,
                                            List<Teacher> teachers,
                                            Map<String, Long> routeMatrixData) {
        CpModel model = new CpModel();
        CpSolver solver = new CpSolver();

        int numStudents = students.size();
        int numTeachers = teachers.size();

        if (numStudents == 0 || numTeachers == 0) {
            log.error("No students or teachers found for client {}", clientId);
            return Map.of();
        }

        // --- Local index-to-ID maps (replaces Redis index maps) ---
        Map<Integer, String> studentIndexMap = new HashMap<>();
        Map<Integer, String> teacherIndexMap = new HashMap<>();

        // --- Create the assignment decision variable matrix ---
        BoolVar[][] assigned = createBoolVarMatrix(model, "assigned", numStudents, numTeachers);

        // --- Populate assignment matrix respecting teacher availability ---
        populateAssignedMatrix(model, assigned, students, teachers,
                studentIndexMap, teacherIndexMap);

        // --- Constraint: each student assigned to exactly one teacher ---
        for (int i = 0; i < numStudents; i++) {
            List<Literal> row = new ArrayList<>();
            for (int j = 0; j < numTeachers; j++) {
                row.add(assigned[i][j]);
            }
            model.addExactlyOne(row);
        }

        // --- Constraint: each teacher assigned to at most one student ---
        for (int j = 0; j < numTeachers; j++) {
            List<Literal> col = new ArrayList<>();
            for (int i = 0; i < numStudents; i++) {
                col.add(assigned[i][j]);
            }
            model.addAtMostOne(col);
        }

        // --- Build objective function from travel and preference matrices ---
        LinearExprBuilder objectiveBuilder = LinearExpr.newBuilder();

        for (String preference : PREFERENCE_NAMES) {
            if (preference.startsWith("short") && preference.endsWith("Commute")) {
                addTravelObjective(objectiveBuilder, model, assigned, students, teachers,
                        routeMatrixData, clientId);
            } else {
                addPreferenceObjective(objectiveBuilder, model, assigned, students, teachers,
                        preference);
            }
        }

        model.maximize(objectiveBuilder.build());

        // --- Solve ---
        CpSolverStatus status = solver.solve(model);

        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            log.info("Solver found {} solution for client {}", status, clientId);
            return extractAssignments(solver, assigned, numStudents, numTeachers,
                    studentIndexMap, teacherIndexMap);
        } else {
            log.warn("Solver returned status {} for client {}", status, clientId);
            return Map.of();
        }
    }

    /**
     * Creates a 2D matrix of {@link BoolVar} decision variables.
     *
     * @param model       the CP model
     * @param baseName    prefix for variable names
     * @param numStudents number of rows (students)
     * @param numTeachers number of columns (teachers)
     * @return the initialized BoolVar matrix
     */
    private BoolVar[][] createBoolVarMatrix(CpModel model, String baseName,
                                            int numStudents, int numTeachers) {
        BoolVar[][] matrix = new BoolVar[numStudents][numTeachers];
        for (int i = 0; i < numStudents; i++) {
            for (int j = 0; j < numTeachers; j++) {
                matrix[i][j] = model.newBoolVar(baseName + "_" + i + "_" + j);
            }
        }
        return matrix;
    }

    /**
     * Constrains the assignment matrix so that students can only be assigned
     * to teachers who are available for the student's session number.
     * Also populates the local index-to-ID maps for result extraction.
     *
     * @param model           the CP model
     * @param assigned        the assignment decision variable matrix
     * @param students        the list of students
     * @param teachers        the list of teachers
     * @param studentIndexMap local map populated with solver index to student ID
     * @param teacherIndexMap local map populated with solver index to teacher ID
     */
    private void populateAssignedMatrix(CpModel model, BoolVar[][] assigned,
                                        List<Student> students, List<Teacher> teachers,
                                        Map<Integer, String> studentIndexMap,
                                        Map<Integer, String> teacherIndexMap) {
        for (int i = 0; i < students.size(); i++) {
            Student student = students.get(i);
            String sessionNum = student.getSessionNum();
            studentIndexMap.put(i, student.getId());

            for (int j = 0; j < teachers.size(); j++) {
                Teacher teacher = teachers.get(j);
                teacherIndexMap.put(j, teacher.getId());

                HashMap<String, String> availability = teacher.getAvailability();
                // if teacher is not available for this session, force the variable to 0
                if (availability == null || !"1".equals(availability.get(sessionNum))) {
                    model.addEquality(assigned[i][j], 0);
                }
            }
        }
    }

    /**
     * Adds travel-distance terms to the objective function.
     *
     * For each assigned pair, the objective gets a bonus inversely
     * proportional to the commute distance, weighted by the student's
     * preference weight for their travel mode.
     */
    private void addTravelObjective(LinearExprBuilder builder, CpModel model,
                                    BoolVar[][] assigned, List<Student> students,
                                    List<Teacher> teachers, Map<String, Long> routeMatrixData,
                                    String clientId) {
        for (int i = 0; i < students.size(); i++) {
            Student student = students.get(i);
            String preferredMode = student.getTravelMethod();
            HashMap<String, String> prefs = student.getWeightedPreferences();
            int weight = 1;
            if (prefs != null && prefs.containsKey(preferredMode)) {
                try { weight = Integer.parseInt(prefs.get(preferredMode)); }
                catch (NumberFormatException ignored) {}
            }

            for (int j = 0; j < teachers.size(); j++) {
                Teacher teacher = teachers.get(j);
                String key = clientId + ":route:student:" + student.getId()
                        + ":teacher:" + teacher.getId();
                Long distance = routeMatrixData.get(key);
                if (distance != null && distance > 0) {
                    // invert distance so shorter commutes get higher scores
                    // use a scale factor to keep values reasonable
                    long score = (long) weight * (10000L / distance);
                    builder.addTerm(assigned[i][j], score);
                }
            }
        }
    }

    /**
     * Adds specialty-preference terms to the objective function.
     *
     * If both the student and teacher share a specialty interest and the
     * student has a nonzero preference weight for it, the assignment
     * decision variable gets that weight added to the objective.
     */
    private void addPreferenceObjective(LinearExprBuilder builder, CpModel model,
                                        BoolVar[][] assigned, List<Student> students,
                                        List<Teacher> teachers, String prefName) {
        for (int i = 0; i < students.size(); i++) {
            Student student = students.get(i);
            HashMap<String, String> studentPrefs = student.getWeightedPreferences();
            int prefWeight = 0;
            if (studentPrefs != null && studentPrefs.containsKey(prefName)) {
                try { prefWeight = Integer.parseInt(studentPrefs.get(prefName)); }
                catch (NumberFormatException ignored) {}
            }
            if (prefWeight == 0) continue;

            for (int j = 0; j < teachers.size(); j++) {
                Teacher teacher = teachers.get(j);
                HashMap<String, String> teacherSpecs = teacher.getSpecialtyInterests();
                if (teacherSpecs != null && "1".equals(teacherSpecs.get(prefName))) {
                    builder.addTerm(assigned[i][j], prefWeight);
                }
            }
        }
    }

    /**
     * Extracts the optimal assignments from the solved model using local
     * in-memory index maps.
     *
     * @param solver          the solved CP solver
     * @param assigned        the assignment decision variable matrix
     * @param numStudents     number of students (rows)
     * @param numTeachers     number of teachers (columns)
     * @param studentIndexMap local map of solver index to student ID
     * @param teacherIndexMap local map of solver index to teacher ID
     * @return a map of studentId to teacherId for each assignment
     */
    private Map<String, String> extractAssignments(CpSolver solver, BoolVar[][] assigned,
                                                   int numStudents, int numTeachers,
                                                   Map<Integer, String> studentIndexMap,
                                                   Map<Integer, String> teacherIndexMap) {
        Map<String, String> assignments = new LinkedHashMap<>();
        for (int i = 0; i < numStudents; i++) {
            for (int j = 0; j < numTeachers; j++) {
                if (solver.booleanValue(assigned[i][j])) {
                    String studentId = studentIndexMap.get(i);
                    String teacherId = teacherIndexMap.get(j);
                    assignments.put(studentId, teacherId);
                    log.info("Assignment: student {} -> teacher {}", studentId, teacherId);
                }
            }
        }
        return assignments;
    }
}
