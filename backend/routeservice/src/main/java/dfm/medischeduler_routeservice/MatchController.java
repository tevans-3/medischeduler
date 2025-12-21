package dfm.medischeduler_routeservice; 

import java.util.List;

import Student;
import Teacher;
import PotentialMatch; 
import KafkaProducerService; 
import KafkaStreamService;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger; 
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.RedisZSET; 
import org.springframework.data.redis.core.RedisHSET; 
import org.redisson.Redisson; 
import org.redisson.api.RLock; 
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class MatchRequest { 
    private List<Student> students;
    private List<Teacher> teachers;

    public List<Student> getStudents(){
        return students;
    }

    public List<Teacher> getTeachers(){
        return teachers;
    }

    public void setStudents(List<Student> students){
        this.students = students;
    }

    public void setTeachers(List<Teacher> teachers){
        this.teachers = teachers;
    }
}

@Component 
public class RedisLock { 

    private final RedissonClient redisson; 

    public RedisLock(RedissonClient redisson) {
        this.redisson = redisson;
    }

    public boolean acquireLock(String clientId, Long waitMs, Long leaseMs){
        RLock lock = redisson.getLock(clientId);
        try { 
            return lock.tryLock(waitMs, leaseMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e){
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public boolean releaseLock(String clientId){
        RLock lock = redisson.getLock("uploadLock:" + clientId); 
        if (lock.isHeldByCurrentThread()){
            lock.unlock(); 
        }
    }
}

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e){
        return ResponseEntity.badRequest().body("Error: "+e.getMessage());
    }

    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<String> handleJsonProcessingException(JsonProcessingException e){
        return ResponseEntity.badRequest().body("Error: "+e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidation(MethodArgumentNotValidException e){
        return ResponseEntity.badRequest().body("Validation error: "+ e.getMessage()); 
    }
}

public class SimpleAsyncExceptionHandler implements AsyncUncaughtExceptionHandler { 
    @Override
    public void handleUncaughtException(Throwable e, Method method, Object... params){
        
        System.out.println("Exception: "+e.getMessage());
        System.out.println("Method: "+method.getName());
        for (Object param : params){
            System.out.println("Param: "+param);
        }

    }
}

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("Async-");
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler(){
        return new SimpleAsyncExceptionHandler(); 
    }
}

@EnableAsync
@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/matches")
//one endpoint: get all matches 
public class MatchController {

    @Autowired
    KafkaProducerService kafkaProducerService;

    @Autowired
    public RedisTemplate<String, Object> redisTemplate; 

    @Autowired
    public RedisLock redisLock; 

    @Autowired 
    public ObjectMapper mapper;

    private static final Logger log = LoggerFactory.getLogger(MatchController.class);
    
    @PostMapping
    public ResponseEntity<String> uploadMatches(@RequestHeader("ClientId") String clientId, @RequestBody @Valid MatchUploadRequest request){

        List<Student> students = request.getStudents(); 
        List<Teacher> teachers = request.getTeachers();

        Boolean lock = redisLock.acquireLock(clientId, 5000, 60000);

        try { 
            if (lock){
                try { 
                        log.info("Lock acquired, processing upload"); 
                        handleStudentsUpload(students); 
                        handleTeachersUpload(teachers); 
                        generateMatches(students, teachers);
                        setUploadTotalInRedis(students, teachers); 
                    } finally { 
                        redisLock.releaseLock(clientId);
                        log.info("Lock released, upload complete");
                    }
                }
            else { 
                log.info("Failed to acquire lock"); 
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Upload in progress.");
            }
        } catch (Exception e){
            return ResponseEntity.badRequest().body("Error uploading matches: "+e);
        }

        return ResponseEntity.ok("Uploaded " + students.size() + " students and " + teachers.size()); 
    }

    private void handleStudentsUpload(List<Student> students){
        for (Student student : students){
            setStudentInRedis(student);
        }
    }

    private void handleTeachersUpload(List<Teacher> teachers){
        for (Teacher teacher : teachers){
            setTeacherInRedis(teacher);
        }
    }

    @Async
    public void generateMatches(List<Teacher> teachers, List<Student> students, String clientId){

        for (Teacher teacher : teachers){
            for (Student student: students){
                PotentialMatch match = new PotentialMatch(student, teacher);
                try {
                    String json = mapper.writeValueAsString(match);
                    kafkaProducerService.sendMessage("MATCH_TOPIC", json, clientId);
                } catch (JsonProcessingException e) {
                    logger.error("Message not sent, serialization error: "+e);
                    throw new RuntimeException("Message not sent, serialization error: ",e); 
                }
            }
        }
    }

    @Async
    protected void setStudentInRedis(Student student, String clientId){
        String key = clientId+":"+"student:" + student.getId();

        try { 
            String jsonSpecialtyInterests = mapper.writeValueAsString(student.getSpecialtyInterests());
            String jsonWeightedPreferences = mapper.writeValueAsString(student.getWeightedPreferences());

            Map<String, String> values = new HashMap<>();
            values.put("id", student.getId()); 
            values.put("address", student.getAddress());
            values.put("firstName", student.getFirstName());
            values.put("lastName", student.getLastName());
            values.put("email", student.getEmail()); 
            values.put("travelMethod", student.getTravelMethods().toString());
            values.put("specialtyInterests", jsonSpecialtyInterests);
            values.put("weightedPreferences", jsonWeightedPreferences);
            values.put("sessionNum", student.getSessionNum());

            redisTemplate.opsForHash().putAll(key, values);

            //store each student a second time as a json string to optimize batch lookups in scheduler service 
            String jsonStudent = mapper.writeValueAsString(student);
            redisTemplate.opsForValue().set("serStudent:"+student.getId(), jsonStudent); 
            //store all ids in a set for lookup later in the scheduler service
            redisTemplate.opsForSet().add("students", student.getId());
        } catch (JsonException ex){
            logger.error("Failed to store student data in redis: ", ex);
            throw new RuntimeException("Failed to store student data in redis: ", ex);
        }
    }

    @Async
    protected void setTeacherInRedis(Teacher teacher, String clientId){
        String key = clientId+":"+"teacher:" + teacher.getId();

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

            //store each teacher a second time as a json string to optimize batch lookups in scheduler service 
            String jsonTeacher = mapper.writeValueAsString(teacher);
            redisTemplate.opsForValue().set(clientId+":"+"serTeacher:"+teacher.getId(), jsonTeacher); 

            //store all ids in a set for lookup later in the scheduler service
            redisTemplate.opsForSet().add(clientId+":"+"teachers", teacher.getId());
        } catch (JsonException ex){
            throw new RuntimeException("Failed to store teacher data in redis: ", ex);
        }
    }

    @Async
    private void setUploadTotalInRedis(List<Teacher> Teachers, List<Student> Students){
        try{
            Integer teacherCount = Teachers.size();
            Integer studentCount = Students.size();
            Integer totalCount = teacherCount * studentCount;

            HashMap<String, String> processedCount = new HashMap<>(); 
            processedCount.put(clientId+":teacherCount", String.valueOf(teacherCount)); 
            processedCount.put(clientId+":studentCount", String.valueOf(studentCount)); 
            processedCount.put(clientId+":totalCount", String.valueOf(totalCount)); 
            processedCount.put(clientId+":processedSoFar", String.valueOf(0));
            processedCount.put(clientId+":failedSoFar", String.valueOf(0));
            processedCount.put(clientId+":notFoundSoFar", String.valueOf(0));

            String key = clientId+":routesProgress";
            redisTemplate.opsForHash.putAll(key, processedCount); 
        } catch (Exception ex){
            throw new RuntimeException("Failed to store upload metadata in redis: ", ex);
        }
    }
}