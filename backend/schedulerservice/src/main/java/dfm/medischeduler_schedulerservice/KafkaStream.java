package dfm.medischeduler_routeservice; 

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Sevice; 
import org.slf4j.Logger; 
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.StreamsBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.api.core.ApiFunction;
import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.CloudTasksSettings;
import com.google.cloud.tasks.v2.stub.CloudTasksStubSettings;
import com.google.api.gax.rpc.ServerStream;
import com.google.protobuf.Timestamp;
import com.google.ortools.Loader;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import com.google.ortools.sat.Literal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.ArrayList;
import javal.util.Set; 
import java.time.Duration; 
import java.http;
import com.google.api.gax.retrying.RetrySettings;

@Configuration
@EnableKafka
@EnableKafkaStreams
public class KafkaConfig {

    @Value(value = "${spring.kafka.bootstrap-servers}")
    private String bootstrapAddress;

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    KafkaStreamsConfiguration kStreamsConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(APPLICATION_ID_CONFIG, "schedulerservice");
        props.put(BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        props.put(DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());

        return new KafkaStreamsConfiguration(props);
    }
}

@Component
public class redisAPI { 
    
    @Autowired 
    public RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public ObjectMapper mapper;

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

    public Map<String, Long> getRouteMatrixData(String clientId){
        Set<String> routeKeys = redisTemplate.opsForSet().members(clientId+":computedRoutes");
        Map<String, Long> routeMatrixData = new Map<>(); 
        for (String key : routeKeys){
            HashMap<String, String> routeData = redisTemplate.opsForHash().entries(key); 
            
            String distance = routeData.get("distance"); 
            Long distance = Long.parseLong(distance); 
            routeMatrixData.put(key, distance); 
        }
        return routeMatrixData; 
    }

    public Integer getNumStudents(String clientId){
        return Integer.parseInt(redisTemplate.get(clientId+":processedCount", "studentCount")); 
    }

    public Integer getNumTeachers(String clientId){
        return Integer.parseInt(redisTemplate.get(clientId+":processedCount", "teacherCount"));
    }

    public Set<String> getStudentIds(String clientId){
        return redisTemplate.opsForSet().entries(clientId+":students"); 
    }

    public Set<string> getTeacherIds(String clientId){
        return redisTemplate.opsForSet().entries(clientId+":teachers");
    }

    public Student getStudent(String studentId, String clientId){
        Map<Object, Object> studentData = redisTemplate.opsForHash().entries(clientId+studentId); 
        Student student = mapper.convertValue(studentData, Student.class);
        return (studentData == null || studentData.isEmpty()) ? null : student; 
    }

    public Teacher getTeacher(String teacherId, String clientId){
        Map<Object, Object> teacherData = redisTemplate.opsForHash().entries(clientId+":"+teacherId);
        Teacher teacher = mapper.convertValue(teacherData, Teacher.class);
        return (teacherData == null || teacherData.isEmpty()) ? null : teacher;
    }

    public String getStudentAddress(String studentId, String clientId){
        Student student = getStudent(clientId+":"+studentId); 
        if (student == null) return null;
        Object address = student.getAddress(); 
        return address != null ? address.toString() : null; 
    }

    public String getTeacherAddress(String teacherId, String clientId){
        Teacher teacher = getTeacher(clientId+":"+teacherId);
        if (teacher == null) return null;  
        Object address = teacher.getAddress();
        return address != null ? address.toString() : null;
    }

    public HashMap<String, Integer> getTeacherAvailability(String teacherId, String clientId){
        Teacher teacher = getTeacher(clientId+":"+teacherId);
        if (teacher == null) return null;
        Object availability = teacher.getAvailability();
        availability.replaceAll((key, value) -> Integer.parseInt(value)); 
        return availability != null ? availability : null;
    }

    public HashMap<String, Integer> getTeacherSpecialtyInterests(String teacherId, String clientId){
        Teacher teacher = getTeacher(clientId+":"+teacherId);
        if (teacher == null) return null;
        Object specialtyInterests = teacher.getSpecialtyInterests();
        specialtyInterests.replaceAll((key, value) -> Integer.parseInt(value));
        return specialtyInterests != null ? specialtyInterests : null;
    }

    public HashMap<String, Integer> getStudentSpecialtyInterests(String studentId, String clientId){
        Student student = getStudent(clientId+":"+studentId);
        if (student == null) return null;
        Object specialtyInterests = student.getSpecialtyInterests();
        specialtyInterests.replaceAll((key, value) -> Integer.parseInt(value)); 
        return specialtyInterests != null ? specialtyInterests : null;
    }

    public String getStudentTravelMethod(String studentId, String clientId){
        Student student = getStudent(clientId+":"+studentId);
        if (student == null) return null;
        Object travelMethods = student.getTravelMethods();
        return travelMethods != null ? travelMethods.toString() : null;
    }

    public HashMap<String, Integer> getStudentWeightedPreferences(String studentId, String clientId){
        Student student = getStudent(clientId+":"+studentId);
        if (student == null) return null;
        Object weightedPreferences = student.getWeightedPreferences();
        weightedPreferences.replaceAll((key, value) -> Integer.parseInt(value)); 
        return weightedPreferences != null ? weightedPreferences : null;
    }

    public void setIndexMapStudentToSolverResult(Integer i, String studentId, String clientId){
        String i = String.valueOf(i); 
        redisTemplate.opsForHash().put(clientId+":studentIndex", i, studentId); 
    }

    public void setIndexMapTeacherToSolverResult(Integer j, String teacherId, String clientId){
        String j = String.valueOf(j);
        redisTemplate.opsForHash().put(clientId+":teacherIndex", j, teacherId);
    }

    public String getStudentFromIndex(Integer i, String clientId){
        String i = String.valueOf(i);
        return redisTemplate.opsForHash().get(clientId+":studentIndex", i);
    }

    public String getTeacherFromIndex(Integer j, String clientId){
        String j = String.valueOf(j); 
        return redisTemplate.opsForHash().get(clientId+":teacherIndex", j);
    }

    public List<Teacher> getAllTeachers(String clientId){
        List<String> keys = getTeacherIds(clientId); 
        List<Teacher> teachers = teacherJsons.stream()
            .map(json -> mapper.readValue(json, Teacher.class))
            .collect(Collectors.toList()); 
        return teachers;
    }

    public List<Student> getAllStudents(String clientId){
        List<String> keys = Arrays.asList(getStudentIds(clientId));
        List<Student> students = studentJsons.stream()
            .map(json -> mapper.readValue(json, Student.class))
            .collect(Collectors.toList());
        return students;
    }

    public void setOptimalAssignments(String clientId, String studentId, String teacherId){
        String key = clientId+":optimalAssignments";
        redisTemplate.opsForHash.put(key, studentId, teacherId);
    }

}


@Component 
public class ORToolsScheduler { 
    static { 
        System.loadLibrary("jniortools");
    }

    @Autowired 
    redisAPI redisApi; 

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate; 

    public int runScheduler(String clientId){

        @Autowired 
        CpModel model; 

        @Autowired
        CpSolver solver; 

        final int numStudents = redisApi.getNumStudents(clientId); 
        final int numTeachers = redisApi.getNumTeachers(clientId);

        Integer STATUS_CODE_FAILURE = 0; 
        Integer STATUS_CODE_SUCCESS = 1; 

        final int[] allStudents = IntStream.range(0, numStudents).toArray(); 
        final int[] allTeachers = IntStream.range(0, numTeachers).toArray();

        // creating the constraints
        //
        // each student is assigned to exactly one teacher
        for (int student : allStudents){
            List<Literal> teachers = new ArrayList<>(); 
            for (int teacher : allTeachers){ 
                teachers.add(binVars[student][teacher]);
            }
            model.addExactlyOne(teachers);
        }

        // each teacher is assigned to exactly one student
        for (int teacher : allTeachers){
            List<Literal> students = new ArrayList<>();
            for (int student : allStudents){
                students.add(binVars[student][teacher]);
            }
            model.addExactlyOne(students);
        }
        

        public static BoolVar[][] createBoolVarMatrix(CpModel model, String baseName, int numStudents, int numTeachers){
            BoolVar[][] matrix = new BoolVar[numStudents][numTeachers];
            for (int i = 0; i < numStudents; i++){
                for (int j = 0; j < numTeachers; j++){
                    matrix[i][j] = model.newBoolVar(baseName + "_" + i + "_" + j);
                }
            }
            return matrix;
        }

        public static LinearExpr[][] createLinearExprMatrix(CpModel model, String baseName, int numStudents, int numTeachers){
            return new LinearExpr[numStudents][numTeachers]; 
        }

        public void populateAssignedMatrix(BoolVar[][] assigned, List<Students> students, List<Teachers> teachers){
            for (int i = 0; i < students.size(); i++){
                Student student = students.get(i); 
                String sessionNum = student.getSessionNum();
                redisApi.setIndexMapStudentToSolverResult(i, student.getId(), clientId); 
                for (int j = 0; j < teachers.size(); j++){
                    Teacher teacher = teachers.get(j); 
                    HashMap<String, String> teacherAvailability = teacher.getAvailability();
                    if (Integer.parseInt(teacherAvailability.get(sessionNum))){
                        redisApi.setIndexMapTeacherToSolverResult(j, teacher.getId(), clientId);
                        assigned[i][j] = model.newBoolVar("assigned_" + i + "_" + j);
                    }
                }
            }
        }

        public void populateTravelMatrix(CpModel model, BoolVar[][] assigned, LinearExpr[][] targetMatrix, String methodName, List<Student> students, List<Teacher> teachers){
            Map<String, Long> routeMatrixData = redisApi.getRouteMatrixData(clientId); 
            for (int i = 0; i < students.size(); i++){
                Student student = students.get(i); 
                String preferredTravelMethod = student.getTravelMethod();
                Integer travelMethodWeight = Integer.parseInt(student.getWeightedPreferences().get(preferredTravelMethod)); 
                for (int j = 0; j < teachers.size(); j++){            
                    Teacher teacher = teachers.get(j);     
                    String key = "route:student:"+student.getStudentId()+":teacher:"+teacher.getTeacherId();
                    Long distance = routeMatrixData.get(key); // distance in meters
                    if (distance == null) continue;
                    else if (assigned[i][j]) {
                        targetMatrix[i][j] = LinearExpr.term(distance, travelMethodWeight); 
                    }
                }
            }
        }

        public void populatePrefMatrix(CpModel model, BoolVar[][] assigned, LinearExpr[][] prefMatrix, String prefName, List<Student> students, List<Teacher> teachers){
            for (int i = 0; i < students.size(); i++){
                Student student = students.get(i);
                Integer prefWeight = Integer.parseInt(students.getWeightedPreferences().get(prefName));
                for (int j = 0; j < teachers.size(); j++){
                    Teacher teacher = teachers.get(i);
                    HashMap <String, Integer> teacherConstraints = teacher.getSpecialtyInterests().map((k,v)->(Integer.parseInt(v)));
                    if (teacher != null) { 
                        teacherConstraints = teacher.getSpecialtyInterests(); 
                        if (assigned[i][j] && teacherConstraints.get(prefName)){
                            prefMatrix[i][j] = LinearExpr.term(assigned[i][j], prefWeight); 
                        }
                    }
                }
            }
        }

        public static <T> List<T> flattenMatrix(T[][] matrix) {
            List<T> flat = new ArrayList<>();
            for (T[] row : matrix) {
                for (T elem : row) {
                    flat.add(elem);
                }
            }
            return flat;
        }

        public extractAssignments(CpSolver solver, BoolVar[][] assigned){
            HashMap<String, String> optimalAssignments = new HashMap<>(); 
            
            for (int i = 0; i < numStudents; i++){
                for (int j = 0; j < numTeachers; j++){
                    if (solver.value(assigned[i][j]) == 1){ 
                        String studentId = getStudentFromIndex(i, clientId);
                        String teacherId = getTeacherFromIndex(j, clientId);
                        redisApi.setOptimalAssignments(clientId, studentId, teacherId);
                        kafkaTemplate.send("OPTIMAL_ASSIGNMENTS_TOPIC", "assignments generated", clientId); 
                    }
                }
            }
        }

        // create arrays of weighted constraints for the problem
        BoolVar[][] assigned = createBoolVarMatrix(model, "assigned", numStudents, numTeachers);
        
        public static final List<String> PREFERENCE_NAMES = List.of(
            "shortTransitCommute",
            "shortCarCommute",
            "shortBikeCommute",
            "shortWalkCommute",
            "palliativeCare",
            "generalLgbtHealth",
            "dermatology",
            "reproductiveAndSexualHealth",
            "generalWomensHealth",
            "transHealth",
            "addictions",
            "mentalHealth",
            "inOfficeProcedures",
            "anesthesia",
            "vulnerablePopulations",
            "mensHealth",
            "geriatrics",
            "pediatrics",
            "mskSportsMed",
            "hospitalist",
            "occupational",
            "newcomer");
        
        private List<Students> students = redisApi.getAllStudents(clientId); 
        private List<Teachers> teachers = redisApi.getAllTeachers(clientId); 

        populateAssignedMatrix(assigned, students, teachers); 

        HashMap<String, LinearExpr> sumFlattenedMatrixes = new HashMap<>(); 

        for (preference : PREFERENCE_NAMES){
            if (preference.equalsIgnoreCase("shortTransitCommute") || 
                preference.equalsIgnoreCase("shortCarCommute") ||
                preference.equalsIgnoreCase("shortBikeCommute") || 
                preference.equalsIgnoreCase("shortWalkCommute")){
                    LinearExpr[][] travelMatrix = createLinearExprMatrix(model, preference, students, teachers); 
                    populateTravelMatrix(model, assigned, travelMatrix, preference, students, teachers); 
                    LinearExpr sum = LinearExpr.sum(flattenMatrix(travelMatrix));
                    sumFlattenedMatrixes.put(preference, sum);
            }
            else { 
                    LinearExpr[][] prefMatrix = createLinearExprMatrix(model, preference, students, teachers); 
                    populatePrefMatrix(model, assigned, prefMatrix, preference, students, teachers); 
                    LinearExpr sum = LinearExpr.sum(flattenMatrix(prefMatrix));
                    sumFlattenedMatrixes.put(preference, sum); 
            }
        }

        sumFlattenedMatrixes.put("assigned", LinearExpr.sum(flattenMatrix(assigned))); 

        LinearExpr totalObjective = LinearExpr.sum(List.of(sumFlattenedMatrixes.Values()));
        
        model.Maximize(totalObjective); 

        CpSolverStatus status = solver.solve(model); 

        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE){
            extractAssignments(solver); 
            return STATUS_CODE_SUCCESS; 
        }
        else { 
            return STATUS_CODE_FAILURE; 
        }
    }
}

@Service
public class KafkaStream {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate; 

    private static final Serde<String> STRING_SERDE = Serdes.String(); 
    @Autowired
    void buildPipeline(StreamsBuilder streamsBuilder) {
        KStream<String, String> messageStream = streamsBuilder.stream("ROUTE_MATRIX_TOPIC", Consumed.with(STRING_SERDE, STRING_SERDE));

        messageStream.foreach((key, value) -> {
            ObjectMapper mapper = new ObjectMapper(); 
            try { 
                
                handleMessage(key, value); 

                logger.info("(Consumer) Message received: " + value);
            }catch (Exception e) {
                logger.error("Error processing message: " + message, e);
            }
        });

        
    }
    private static final Logger logger = LoggerFactory.getLogger(KafkaStreams.class); 

    private void handleMessage(String clientId, String value) {
        boolean check = isThisTheLastBatch(clientId); 
        if (check){
             runOutcome = ORToolsScheduler.runScheduler(clientId); 
            //Do things 
            //pass it to OR Tools assignment solver 
            //get result 
            //stream it to client   
        }
    }



    //studentId:123:palliativecare,lgbtqia+
    //studentId:456:dermatology
    //studentId:789:reproductiveandsexualhealth
    //teacherId:123:palliativecare
    //teacherId:456:dermatology
    //teacherId:789:reproductiveandsexualhealth


    private boolean isThisTheLastBatch(String clientId){
        Integer totalUploadCount = redisApi.getTotalCount(clientId);
        Integer processedCount = redisApi.getProcessedCount(clientId); 
        Integer failedCount = redisApi.getFailedCount(clientId); 
        Integer notFoundCount = redisApi.getNotFoundCount(clientId);

        Integer totalSoFar = processedCount + failedCount + notFoundCount;
        if (totalSoFar == totalUploadCount){
            return true; 
        }
        logger.info("Saw "+totalSoFar+"/"+totalUploadCount +"matches. "+ String.valueOf((1 - totalSoFar/totalUploadcount)*100)+"% left for client: "+clientId); 
    }

}