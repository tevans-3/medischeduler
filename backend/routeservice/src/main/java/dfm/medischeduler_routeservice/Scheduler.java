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

    public Integer getTotalCount(){
        return Integer.parseInt(redisTemplate.opsForHash("routesProgress", "totalCount")); 
    }
    
    public Integer getProcessedCount(){
        return Integer.parseInt(redisTemplate.opsForHash("routesProgress", "processedSoFar"));
    }

    public Integer getFailedCount(){
        return Integer.parseInt(redisTemplate.opsForHash("routesProgress", "failedSoFar"));
    }

    public Integer getNotFoundCount(){
        return Integer.parseInt(redisTemplate.opsForHash("routesProgress", "notFoundSoFar"));
    }

    public Map<String, Long> getRouteMatrixData(){
        Set<String> routeKeys = redisTemplate.opsForSet().members("computedRoutes");
        Map<String, Long> routeMatrixData = new Map<>(); 
        for (String key : routeKeys){
            HashMap<String, String> routeData = redisTemplate.opsForHash().entries(key); 
            
            String distance = routeData.get("distance"); 
            Long distance = Long.parseLong(distance); 
            routeMatrixData.put(key, distance); 
        }
        return routeMatrixData; 
    }

    public Integer getNumStudents(){
        return Integer.parseInt(redisTemplate.get("processedCount", "studentCount")); 
    }

    public Integer getNumTeachers(){
        return Integer.parseInt(redisTemplate.get("processedCount", "teacherCount"));
    }

    public Set<String> getStudentIds(){
        return redisTemplate.opsForSet().entries("students"); 
    }

    public Set<string> getTeacherIds(){
        return redisTemplate.opsForSet().entries("teachers");
    }

    public Student getStudent(String studentId){
        Map<Object, Object> studentData = redisTemplate.opsForHash().entries(studentId); 
        Student student = mapper.convertValue(studentData, Student.class);
        return (studentData == null || studentData.isEmpty()) ? null : student; 
    }

    public Teacher getTeacher(String teacherId){
        Map<Object, Object> teacherData = redisTemplate.opsForHash().entries(teacherId);
        Teacher teacher = mapper.convertValue(teacherData, Teacher.class);
        return (teacherData == null || teacherData.isEmpty()) ? null : teacher;
    }

    public String getStudentAddress(String studentId){
        Student student = getStudent(studentId); 
        if (student == null) return null;
        Object address = student.getAddress(); 
        return address != null ? address.toString() : null; 
    }

    public String getTeacherAddress(String teacherId){
        Teacher teacher = getTeacher(teacherId);
        if (teacher == null) return null;  
        Object address = teacher.getAddress();
        return address != null ? address.toString() : null;
    }

    public HashMap<String, Integer> getTeacherAvailability(String teacherId){
        Teacher teacher = getTeacher(teacherId);
        if (teacher == null) return null;
        Object availability = teacher.getAvailability();
        availability.replaceAll((key, value) -> Integer.parseInt(value)); 
        return availability != null ? availability : null;
    }

    public HashMap<String, Integer> getTeacherSpecialtyInterests(String teacherId){
        Teacher teacher = getTeacher(teacherId);
        if (teacher == null) return null;
        Object specialtyInterests = teacher.getSpecialtyInterests();
        specialtyInterests.replaceAll((key, value) -> Integer.parseInt(value));
        return specialtyInterests != null ? specialtyInterests : null;
    }

    public HashMap<String, Integer> getStudentSpecialtyInterests(String studentId){
        Student student = getStudent(studentId);
        if (student == null) return null;
        Object specialtyInterests = student.getSpecialtyInterests();
        specialtyInterests.replaceAll((key, value) -> Integer.parseInt(value)); 
        return specialtyInterests != null ? specialtyInterests : null;
    }

    public String getStudentTravelMethod(String studentId){
        Student student = getStudent(studentId);
        if (student == null) return null;
        Object travelMethods = student.getTravelMethods();
        return travelMethods != null ? travelMethods.toString() : null;
    }

    public HashMap<String, Integer> getStudentWeightedPreferences(String studentId){
        Student student = getStudent(studentId);
        if (student == null) return null;
        Object weightedPreferences = student.getWeightedPreferences();
        weightedPreferences.replaceAll((key, value) -> Integer.parseInt(value)); 
        return weightedPreferences != null ? weightedPreferences : null;
    }

    public void setIndexMapStudentToSolverResult(Integer i, String studentId){
        String i = String.valueOf(i); 
        redisTemplate.opsForHash().put("studentIndex", i, studentId); 
    }

    public void setIndexMapTeacherToSolverResult(Integer j, String teacherId){
        String j = String.valueOf(j);
        redisTemplate.opsForHash().put("teacherIndex", j, teacherId);
    }

    public String getStudentFromIndex(Integer i){
        String i = String.valueOf(i);
        return redisTemplate.opsForHash().get("studentIndex", i);
    }

    public String getTeacherFromIndex(Integer j){
        String j = String.valueOf(j); 
        return redisTemplate.opsForHash().get("teacherIndex", j);
    }

    public List<Teacher> getAllTeachers(){
        List<String> keys = Arrays.asList(getTeacherIds()); 
        List<Teacher> teachers = teacherJsons.stream()
            .map(json -> mapper.readValue(json, Teacher.class))
            .collect(Collectors.toList()); 
        return teachers;
    }

    public List<Student> getAllStudents(){
        List<String> keys = Arrays.asList(getStudentIds());
        List<Student> students = studentJsons.stream()
            .map(json -> mapper.readValue(json, Student.class))
            .collect(Collectors.toList());
        return students;
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
    CpModel model; 

    public int scheduler(){

        final int numStudents = redisApi.getNumStudents(); 
        final int numTeachers = redisApi.getNumTeachers();

        final int[] allStudents = IntStream.range(0, numStudents).toArray(); 
        final int[] allTeachers = IntStream.range(0, numTeachers).toArray();

        // create arrays of weighted constraints for the problem
        BoolVar[][] assigned = createBoolVarMatrix(model, "assigned", numStudents, numTeachers);
        BoolVar[][] shortTransitCommute = createBoolVarMatrix(model, "shortTransitCommute", numStudents, numTeachers);
        BoolVar[][] shortCarCommute = createBoolVarMatrix(model, "shortCarCommute", numStudents, numTeachers);
        BoolVar[][] shortBikeCommute = createBoolVarMatrix(model, "shortBikeCommute", numStudents, numTeachers);
        BoolVar[][] shortWalkCommute = createBoolVarMatrix(model, "shortWalkCommute", numStudents, numTeachers);
        BoolVar[][] palliativeCare = createBoolVarMatrix(model, "palliativeCare", numStudents, numTeachers);
        BoolVar[][] generalLgbtHealth = createBoolVarMatrix(model, "generalLgbtHealth", numStudents, numTeachers);
        BoolVar[][] dermatology = createBoolVarMatrix(model, "dermatology", numStudents, numTeachers);
        BoolVar[][] reproductiveAndSexualHealth = createBoolVarMatrix(model, "reproductiveAndSexualHealth", numStudents, numTeachers);
        BoolVar[][] generalWomensHealth = createBoolVarMatrix(model, "generalWomensHealth", numStudents, numTeachers);
        BoolVar[][] transHealth = createBoolVarMatrix(model, "transHealth", numStudents, numTeachers);
        BoolVar[][] addictions = createBoolVarMatrix(model, "addictions", numStudents, numTeachers);
        BoolVar[][] mentalHealth = createBoolVarMatrix(model, "mentalHealth", numStudents, numTeachers);
        BoolVar[][] inOfficeProcedures = createBoolVarMatrix(model, "inOfficeProcedures", numStudents, numTeachers);
        BoolVar[][] anesthesia = createBoolVarMatrix(model, "anesthesia", numStudents, numTeachers);
        BoolVar[][] vulnerablePopulations = createBoolVarMatrix(model, "vulnerablePopulations", numStudents, numTeachers);
        BoolVar[][] mensHealth = createBoolVarMatrix(model, "mensHealth", numStudents, numTeachers);
        BoolVar[][] geriatrics = createBoolVarMatrix(model, "geriatrics", numStudents, numTeachers);
        BoolVar[][] pediatrics = createBoolVarMatrix(model, "pediatrics", numStudents, numTeachers);
        BoolVar[][] mskSportsMed = createBoolVarMatrix(model, "mskSportsMed", numStudents, numTeachers);
        BoolVar[][] hospitalist = createBoolVarMatrix(model, "hospitalist", numStudents, numTeachers);
        BoolVar[][] occupational = createBoolVarMatrix(model, "occupational", numStudents, numTeachers);
        BoolVar[][] newcomer = createBoolVarMatrix(model, "newcomer", numStudents, numTeachers);
        BoolVar[][] allowedAssignments = createBoolVarMatrix(model, "teacherAvailable", numStudents, numTeachers);
        Int[][] studentWeightsPreference = createIntVarMatrix(model, "studentWeightsPreference", numStudents, numPreferences);
        
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

    private Map<String, BoolVar[][]> preferenceBoolVars = new HashMap<>();
    private List<Students> students = redisApi.getAllStudents(); 
    private List<Teachers> teachers = redisApi.getAllTeachers(); 

    public static void populateAssignedMatrix(BoolVar[][] assigned, List<Students> students, List<Teachers> teachers){
        for (student : students){
            redisApi.setIndexMapStudentToSolverResult(i, student.getId());
            for (teacher : teachers){
                redisApi.setIndexMapTeacherToSolverResult(j, teacher.getId());
                assigned[i][j] = model.newBoolVar("assigned_" + i + "_" + j);
            }
        }
    }

    public static void populateTravelMatrix(CpModel model, BoolVar[][] assigned, BoolVar[][] targetMatrix, String methodName, List<Student> students, List<Teacher> teachers){
        Map<String, Long> routeMatrixData = redisApi.getRouteMatrixData(); 
        for (student : students){
            HashMap <String, Integer> studentConstraints = student.getTravelMethods();
            for (teacher : teachers){
                String key = "route:student:"+student.getStudentId()+":teacher:"+teacher.getTeacherId();
                Long distance = routeMatrixData.get(key); // distance in meters
                if (distance == null) continue;
                
            }
        }
    }
    public static void populatePrefMatrix(CpModel model, BoolVar[][] assigned, BoolVar[][] prefMatrix, String prefName, List<Student> students, List<Teacher> teachers){
        for (student : students){
            for (teacher : teachers){
                    HashMap <String, Integer> teacherConstraints = new HashMap<>();
                    if (prefName.equalsIgnoreCase("specialtyInterests")){ teacherConstraints = teacher.getSpecialtyInterests();}
                    if (assigned[i][j] && teacher.get(prefName)){
                        prefMatrix[i][j] = model.newBoolVar(prefName + "_" + i + "_" + j);
                    }
                }
            }
            preferenceBoolVars.put(basename, studentPrefs);
        
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
                
                handleMessage(value); 

                logger.info("(Consumer) Message received: " + value);
            }catch (Exception e) {
                logger.error("Error processing message: " + message, e);
            }
        });

        
    }
    private static final Logger logger = LoggerFactory.getLogger(KafkaStreams.class); 

    private void handleMessage(String value) {
        boolean check = isThisTheLastBatch(); 
        if (check){
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


    private boolean isThisTheLastBatch(){
        Integer totalUploadCount = redisApi.getTotalCount();
        Integer processedCount = redisApi.getProcessedCount(); 
        Integer failedCount = redisApi.getFailedCount(); 
        Integer notFoundCount = redisApi.getNotFoundCount();

        Integer totalSoFar = processedCount + failedCount + notFoundCount;
        if (totalSoFar == totalUploadCount){
            return true; 
        }
        logger.info("Saw "+totalSoFar+"/"totalUploadCount +"matches. "+ String.valueOf((1 - totalSoFar/totalUploadcount)*100)+"% left"); 
    }

}