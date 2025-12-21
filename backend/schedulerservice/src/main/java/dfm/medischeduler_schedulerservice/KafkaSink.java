package.dfm.medischeduler_schedulerservice; 

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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.ArrayList;
import javal.util.Set; 
import java.time.Duration; 
import java.http;

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


@Configuration 
@EnableWebSocketMessageBroker 
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer{ 
	@Override 
	public void configureMessageBroker(MessageBrokerRegistry config){
		config.enableSimpleBroker("/topic"); 
		config.setApplicationDestinationPrefixes("/upload"); 
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry){
		registry.addEndpoint("/assignments"); 
		registry.addEndpoint("/assignments").withSockJS(); 
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

    public HashMap<String, String> getOptimalAssignment(String clientId, String studentId){
        String key = clientId+":optimalAssignments:"+studentId;
        return redisTemplate.opsForHash.entries(key);
    }

}

@Component 
public class Assignment { 

    public Student student; 
    public Teacher teacher; 

    public void setStudent(Student student){
        this.student = student; 
    }

    public void setTeacher(Teacher teacher){
        this.teacher = teacher; 
    }

    public Student getStudent(){
        return this.student; 
    }

    public Teacher getTeacher(){
        return this.teacher; 
    }
}

@Service
public class KafkaSink {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate; 

    @Autowired
    private RedisAPI redisApi; 

    @Autowired 
    private final SimpMessagingTemplate messagingTemplate; 

    ObjectMapper mapper = new ObjectMapper();

    private static final Logger logger = LoggerFactory.getLogger(KafkaStreams.class); 
    private String batchId; 
    private String elemProcessedInLastMin = String.valueOf(0);
    private Instant processingStartTime = Instant.now(); 

    private static final Serde<String> STRING_SERDE = Serdes.String(); 
    @Autowired
    void buildPipeline(StreamsBuilder streamsBuilder) {
        KStream<String, String> messageStream = streamsBuilder.stream("OPTIMAL_ASSIGNMENTS_TOPIC", Consumed.with(STRING_SERDE, STRING_SERDE));

        messageStream.foreach((key, value) -> {
            try { 
                handleMessage(key, value); 
                logger.info("(Consumer) Message received: " + value);
            }catch (Exception e) {
                logger.error("Error processing message: " + value, e);
            }
        });
        
    }

    private List<Assignment> getAssignments(String clientId){

        List<Assignment> assignments = new List<Assignment>(); 

        List<Student> students = redisApi.getAllStudents(clientId); 
        List<Teacher> teachers = redisApi.getAllTeachers(clientId); 

        String key = clientId+":optimalAssignments";

        for (student : students){
            Assignment assignment = new Assignment<>(); 
            
            String teacherId = redisApi.getOptimalAssignment(clientId, student.getId()); 
            Teacher teacher = redisApi.getTeacher(teacherId, clientId); 

            assignment.setStudent(student); 
            assignment.setTeacher(teacher); 
            assignments.add(assignment); 
        }
        return assignments;
    }

    private void handleMessage(String key, String value) {
        String clientId = key; 
        List<Assignments> assignments = getAssignments(clientId);

        String jsonAssignments = mapper.writeValueAsString(assignments);  
        messagingTemplate.convertAndSend("/topic"+clientId+"/upload/assignments", jsonAssignments); 
    }
}