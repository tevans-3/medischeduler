package dfm.medischeduler_routeservice; 

import Student; 
import Teacher; 
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
import com.google.maps.routing.v2.stub.RoutesStubSettings; 
import com.google.api.gax.rpc.ServerStream;
import com.google.maps.routing.v2.ComputeRouteMatrixRequest;
import com.google.maps.routing.v2.RouteMatrixDestination;
import com.google.maps.routing.v2.RouteMatrixElement;
import com.google.maps.routing.v2.RouteMatrixOrigin;
import com.google.maps.routing.v2.RouteTravelMode;
import com.google.maps.routing.v2.RoutesClient;
import com.google.maps.routing.v2.RoutingPreference;
import com.google.maps.routing.v2.TrafficModel;
import com.google.maps.routing.v2.TransitPreferences;
import com.google.maps.routing.v2.Units;
import com.google.protobuf.Timestamp;
import java.util.CopyOnWriteArrayList;
import java.util.ConcurrentHashMap; 
import java.time.Duration; 
import java.util.PriorityQueue; 
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
        props.put(APPLICATION_ID_CONFIG, "routeservice");
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
    ObjectMapper mapper; 

    private static final Logger log = LoggerFactory.getLogger(redisAPI.class);

    public Integer getTotalCount(String clientId){
        return Integer.parseInt(redisTemplate.opsForHash.get(clientId+":routesProgress", "totalCount")); 
    }
    
    public Integer getProcessedCount(String clientId){
        return Integer.parseInt(redisTemplate.opsForHash.get(clientId+":routesProgress", "processedSoFar"));
    }

    public Integer getFailedCount(String clientId){
        return Integer.parseInt(redisTemplate.opsForHash.get(clientId+":routesProgress", "failedSoFar"));
    }

    public Integer getNotFoundCount(String clientId){
        return Integer.parseInt(redisTemplate.opsForHash.get(clientId+":routesProgress", "notFoundSoFar"));
    }


    public Student getStudent(String studentId, String clientId){
        Map<Object, Object> studentData = redisTemplate.opsForHash().entries(clientId+":"+studentId); 
        Student student = mapper.convertValue(studentData, Student.class);
        return (studentData == null || studentData.isEmpty()) ? null : student; 
    }

    public Teacher getTeacher(String teacherId, String clientId){
        Map<Object, Object> teacherData = redisTemplate.opsForHash().entries(clientId+":"+teacherId);
        Teacher teacher = mapper.convertValue(teacherData, Teacher.class);
        return (teacherData == null || teacherData.isEmpty()) ? null : teacher;
    }

    public HashMap<String, Integer> getStudentTravelMethods(String studentId, String clientId){
        Student student = getStudent(clientId+":"+studentId);
        if (student == null) return null;
        Object travelMethods = student.getTravelMethods();
        travelMethods.replaceAll((key, value) -> Integer.parseInt(value)); 
        return travelMethods != null ? travelMethods : null;
    }

    public String getStudentAddress(String key){
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

	public void cacheRouteData(String clientId, String studentId, String teacherId, Long meters, Duration duration, String travelMode) { 
		String studentKey = ":student:"+studentId; 
        String teacherKey = ":teacher:"+teacherId;

        String key = clientId+":route:"+studentKey+teacherKey;

        HashMap<String, String> routeData =new HashMap<>();
        routeData.put("studentId", studentId);
        routeData.put("teacherId", teacherId);
        routeData.put("travelMode", travelMode); 
        routeData.put("distance", String.valueOf(meters)); 
        routeData.put("duration", String.valueOf(duration.getSeconds())); 

		//store route key in set so the scheduler service can look up all the routes later
		redisTemplate.opsForSet().add(clientId+":computedRoutes", key); 
		
		//store route data 
                redisTemplate.opsForHash().putAll(key, routeData);
    } 

	public void cacheIndexMapsStudentToResponse(String clientId, String studentId, ArrayList<String> origins, String batchId) { 
		String studentAddress = getStudentAddress(clientId+":"+studentId); 
		String addressIndex = origins.indexOf(clientId+":"+studentAddress); 
		redisTemplate.opsForHash().put(clientId+":originIndex:", String.valueOf(addressIndex), ":"+batchId); 
        redisTemplate.opsForHash().put(clientId+":originIndex:"+String.valueOf(addressIndex), ":"+batchId, studentId);
    } 

	public void cacheIndexMapsTeacherToResponse(String clientId, String teacherId, ArrayList<String> destinations, String batchId) { 
		String teacherAddress = getTeacherAddress(clientId+":"+teacherId); 
		String addressIndex = destinations.indexOf(teacherAddress); 
        HashMap<String, String> 
		redisTemplate.opsForHash().put(clientId+":destinationIndex:", String.valueOf(addressIndex), ":"+batchId); 
        redisTemplate.opsForHash().put(clientId+":destinationIndex:"+String.valueOf(addressIndex), ":"+batchId, teacherId);
    } 

	public String lookupTeacherByDestIndex(Integer destinationIndex, String batchId, String clientId) { 
		String destinationIndex = String.valueOf(destinationIndex); 
		String teacherId = redisTemplate.opsForHash.get(clientId+":destinationIndex", destinationIndex+":"+batchId); 
		return teacherId; 
    }

	public String lookupStudentByOriginIndex(Integer originIndex, String batchId, String clientId) { 
		String originIndex = String.valueOf(originIndex); 
		String studentId = redisTemplate.opsForHash.get(clientId+":originIndex", originIndex+":"+batchId); 
		return studentId; 
    }
	
    public void createNewBatch(String batchId, String clientId){ 
		String key = clientId+":batchMetadata:currentBatchId:"+batchId;
		Map<String, String> metadata = redisTemplate.opsForHash.entries(key); 
		if (!metadata.isEmpty()) { logger.error("Batch"+batchId+" already exists under that id.");}
		else { redisTemplate.opsForHash.put(clientId+":batchMetadata", "currentBatchId", batchId); }
	} 

    //set global state metadata: elements processed per minute 

    public void setGlobalStateMetadata(String attrToSet, String attrValue){
        String key = "globalStateMetadata";
        Map<String, String> metadata = redisTemplate.opsForHash.entries(key);
        if (metadata.isEmpty()) {redisTemplate.opsForHash.put(key, attrToSet, attrValue);}
         if (attrToSet != "elemProcessedInLastMin" && attrToSet != "processingStartTime") { 
            logger.warning("Invalid attribute. batchMetadata can only be one of: elemProcessedInLastMin, processingStartTime");
        }
        if (attrValue == null) { logger.warning(attrToSet + " set to NULL. Are you sure this is correct?");}
        else {
            redisTemplate.opsForHash.put("batchMetadata:currentBatchId:"+batchId, attrToSet, attrValue);  
            }
    }

    public void incrementGlobalStateMetadata(String attributeToInc, Integer amountBy) {
        String key = "globalStateMetadata";
        Map<String, String> metadata = redisTemplate.opsForHash.entries(key);
        if (metadata.isEmpty()) { logger.error("Global state metadata not found in redis. Can't increment attributes that don't exist."); }
        else { redisTemplate.opsForHash.increment(key+":"+attributeToInc, amountBy); }
    }

    public String getGlobalStateMetadata(String attrToGet) { 
		String key = "globalStateMetadata:";
		Map<String, String> metadata = redisTemplate.opsForHash.entries(key); 
		if (metadata.isEmpty()) { logger.error("Global state not found in redis.");}
		if (attrToGet != "elemProcessedInLastMin" && attrToGet != "processingStartTime") { 
			logger.warning("Invalid attribute. batchMetadata can only be one of: elemProcessedInLastMin, processingStartTime");
		}
		else {
			return metadata.get("globalStateMetadata:"+attrToGet);  
		}
	} 

	//batch metadata: status, processingStartTime, id

    public void setBatchMetadata(String batchId, String attrToSet, String attrValue, String clientId) { 
        String key = "batchMetadata:currentBatchId:"+batchId;
        Map<String, String> metadata = redisTemplate.opsForHash.entries(key); 
        if (metadata.isEmpty()) { redisTemplate.opsForHash.put(clientId+":batchMetadata", "currentBatchId", batchId);}
        if (attrToSet != "status" && attrToSet != "processingStartTime") { 
            logger.warning("Invalid attribute. batchMetadata can only be one of: status, processingStartTime");
        }
        if (attrValue == null) { logger.warning(attrToSet + " set to NULL. Are you sure this is correct?");}
        else {
            redisTemplate.opsForHash.put(clientId+":batchMetadata:currentBatchId:"+batchId, attrToSet, attrValue);  
            }
    }

	public void incrementBatchMetadata(String batchId, String attributeToInc, Integer amountBy, String clientId) {
		String key = clientId+":batchMetadata:currentBatchId:"+batchId; 
		Map<String, String> metadata = redisTemplate.opsForHash.entries(key); 
		if (metadata.isEmpty()) { logger.error(batchId + " not found in redis. Can't increment attributes that don't exist."); } 
		else { redisTemplate.opsForHash.increment(key+":"+attributeToInc, amountBy); }
	} 

	public String getBatchMetadata(String batchId, String attrToGet, String clientId) { 
		String key = clientId+":batchMetadata:currentBatchId:"+batchId;
		Map<String, String> metadata = redisTemplate.opsForHash.entries(key); 
		if (metadata.isEmpty()) { logger.error("Batch "+batchId+" not found in redis.");}
		if (attrToGet != "status" && attrToGet != "processingStartTime") { 
			logger.warning("Invalid attribute. batchMetadata can only be one of: status, processingStartTime");
		}
		else {
			return metadata.get(clientId+":batchMetadata:currentBatchId:"+batchId+":"+attrToGet);  
		}
	} 
	
	public void cacheValueForProcessing(String value, String clientId) { 
		if (value == null) {logger.warning("Cached value: "+value+" is null. Are you sure this is correct?"); } 
		redisTemplate.opsForSet.put(clientId+":cachedValue", value); 
	}

	public String getBatchId(String clientId){ 
		return redisTemplate.opsForHash.get(clientId+":batchMetadata", "currentBatchId"); 
	}
	
	public void removeFromCache(String value, String clientId) {
		redisTemplate.opsForSet.remove(clientId+":cachedValue", member); 
	}

    public void updateRoutesProgress(String category, Integer amount, String clientId){ 
		redisTemplate.opsForHash.increment(clientId+":routesProgress", category, amount); 
	} 

    public void removeBatchFromRedis(String batchId, String clientId){
        redisTemplate.opsForHash.delete(clientId+":batchMetadata:currentBatchId:"+batchId); 
    }

    public Set<Object> getCachedValues(String clientId){ 
		return redisTemplate.opsForSet.members(clientId+":cachedValue"); 
	}
} 
    

@Component
public class routeMatrixBuilder { 

    @Value("${routes-api.api-key}")
    private String apiKey; 

    @Value("${routes-api.field-mask-bikeMode}")
    private String fieldMaskBikeMode;

    @Value("${routes-api.field-mask-driveMode}")
    private String fieldMaskDriveMode; 

    @Value("${routes-api.field-mask-walkMode}")
    private String fieldMaskWalkMode;

    @Value("${routes-api.field-mask-transitMode}")
    private String fieldMaskTransitMode;

    private String fieldMask; 

    @Autowired
    private redisAPI redisApi;

    private static final Logger log = LoggerFactory.getLogger(RouteMatrixBuilder.class);

    public RoutesClient createRoutesClientWithApiKey(String fieldMask) throws Exception {

        RoutesSettings settings = RoutesSettings.newBuilder()
                                .setRetrySettings(
                                    RetrySettings.newBuilder()
                                    .setInitialRetryDelay(Duration.ofMillis(500))
                                    .setMaxRetryDelay(Duration.ofSeconds(5))
                                    .setRetryDelayMultiplier(2)
                                    .setMaxAttempts(5)
                                    .setTotalTimeout(Duration.ofSeconds(30))
                                    .build()
                                )
                                .setHeaderProvider(()-> {
                                    Map<String, String> headers = new HashMap<>();
                                    headers.put("x-goog-api-key", apiKey);
                                    headers.put("x-goog-fieldmask", fieldMask);
                                    return headers;
                                }).build();
        try {
            RouteServiceClient client = RoutesClient.create(settings);
            return client;
        } catch (Exception e){
            //TODO handle exception 
        }
    }

    public void asyncComputeRouteMatrix(ArrayList<RouteMatrixOrigin> Origins, ArrayList<RouteMatrixDestination> Destinations, String travelMode, String batchId) throws Exception {

        Integer modeNumber; 
        if (travelMode == "BIKE") {fieldMask = fieldMaskBikeMode; modeNumber = 2;}
        else if (travelMode == "DRIVE") {fieldMask = fieldMaskDriveMode; modeNumber = 1;}
        else if (travelMode == "WALK") {fieldMask = fieldMaskWalkMode; modeNumber = 3;}
        else if (travelMode == "TRANSIT") {fieldMask = fieldMaskTransitMode; modeNumber = 4;}
        else { log.error("Bad parameter: invalid travel mode: "+travelMode); 
               throw new IllegalArgumentException("Bad parameter: invalid travel mode: "+travelMode);}

        try (RoutesClient routesClient = createRoutesClientWithApiKey(fieldMask)) {
        ComputeRouteMatrixRequest request =
            ComputeRouteMatrixRequest.newBuilder()
                .addAllOrigins(Origins)
                .addAllDestinations(Destinations)
                .setTravelMode(RouteTravelMode.forNumber(modeNumber))
                .setRoutingPreference(RoutingPreference.forNumber(0))
                .setDepartureTime(Timestamp.newBuilder().build())
                .setArrivalTime(Timestamp.newBuilder().build())
                .setLanguageCode("en")
                .setRegionCode("northamerica-northeast2-a")
                .setUnits(Units.forNumber(0))
                .addAllExtraComputations(new ArrayList<ComputeRouteMatrixRequest.ExtraComputation>())
                .setTrafficModel(TrafficModel.forNumber(0))
                .setTransitPreferences(TransitPreferences.newBuilder().build())
                .build();
        ServerStream<RouteMatrixElement> stream =
            routesClient.computeRouteMatrixCallable().call(request);
        for (RouteMatrixElement element : stream) {
            if (!element.getStatus().getCode().equals(Code.OK.name())){
                log.warn("Failed to compute route: " + element.getStatus().getMessage()); 
                redisApi.updateRoutesProgress("failedSoFar", 1);
                continue; 
            }

            Integer originIndex = element.getOriginIndex();
            Integer destinationIndex = element.getDestinationIndex();
            
            if (element.getCondition() != RouteMatrixElementCondition.ROUTE_EXISTS){ 
                log.info("No valid route between Origin:"+originIndex+ " and Destination:"+destinationIndex); 
                redisApi.updateRoutesProgress("notFoundSoFar", 1); 
                continue; 
            }
            else { 
                long meters = element.getDistanceMeters(); 
                Duration duration = element.getDuration(); 


                log.info("Route between Origin:"+originIndex+ " and Destination:"+destinationIndex+" has "+meters+" meters and "+duration+" seconds");

                String studentId = redisApi.lookupStudentByOriginIndex(originIndex, batchId);
                String teacherId = redisApi.lookupTeacherByDestIndex(destinationIndex, batchId);

                redisApi.cacheRouteData(studentId, teacherId, meters, duration, travelMode);

                //update count 
                redisApi.updateRoutesProgress("processedSoFar", 1);
            }
        }
        } catch (Exception e) { 
            //TODO handle exception 
        }
    }

}

@Component 
public class requestBatch { 
    @Autowired
    private RedisAPI redisApi;

    @Autowired 
    ObjectMapper mapper; 

    public String elemProcessedInLastMin; 
    public String batchId; 
    public String clientId; 
    public String batchType; 
    public String batchStatus; 
    public Integer batchFillStatus; 
    private ArrayList<String> origins = new ArrayList<>();
    private ArrayList<String> destinations = new ArrayList<>();

    public setBatchId(String batchId){
        this.batchId = batchId;
    }

    public setClientId(String clientId){
        this.clientId = clientId; 
    }

    public setBatchType(String batchType){
        this.batchType = batchType; 
    }

    public setBatchStatus(String batchStatus){
        this.batchStatus = batchStatus;
    }

    public getBatchId(){
        return this.batchId; 
    }

    public getClientId(){
        return this.clientId;
    }

    public setBatchFillStatus(Integer batchFillStatus){
        this.batchFillStatus = batchFillStatus; 
    }

    private Integer getRouteMatrixSize(){
        return origins.size()*destinations.size();
    }

    private Integer testRouteMatrixSize(Integer addNumOrigins, Integer addNumDestinations){
        return (origins.size()+addNumOrigins)*(destinations.size()+addNumDestinations);
    }

    //For each origin-destination pair, the Routes API response includes an originIndex and a destinationIndex.
    //This corresponds to the index of that origin (destination) address in the request's origins (destinations) array. 
    //We need to use this originIndex (and destinationIndex) to map the route response to the student-teacher pair it corresponds to. 
    //So we store them in redis as a hash, with the key being the destinationIndex (originIndex) and the value the student (teacher)
    // id. This enables fast matching as soon as each response is returned. 

    public String updateBatch(String studentId, String teacherId, String studentAddress, String teacherAddress) {

        if (testRouteMatrixSize(1,1) <= 625){
            origins.add(studentAddress);
            destinations.add(teacherAddress); 

            redisApi.cacheIndexMapsStudentToResponse(studentId, origins, this.batchId); 
            redisApi.cacheIndexMapsTeacherToResponse(teacherId, destinations, this.batchId);

            setBatchFillStatus(this.BatchFillStatus+1);
            return "batchUpdated";
        }
        else { 
            return "batchFull";
        }
    }

}

@Component
public class BatchManager { 
    @Autowired
    private RedisAPI redisApi; 

    @Autowired
    private RouteMatrixBuilder routeMatrixBuilder;

    @Autowired 
    private KafkaStream kafkaStream;

    @Autowired
    private RequestBatch requestBatch; 

    @Autowired
    ObjectMapper mapper; 

    private String uploadBatchId; 
    private String elemProcessedInLastMin;
    private Integer elemAwaitingProcessing;
    private ArrayList<HashMap<String, RequestBatch>> requestBatchQueue; 

    private Integer pollTotalProgress(){
        Integer totalUploadCount = redisApi.getTotalCount();
        Integer processedCount = redisApi.getProcessedCount(); 
        Integer failedCount = redisApi.getFailedCount(); 
        Integer notFoundCount = redisApi.getNotFoundCount();

        Integer totalSoFar = processedCount + failedCount + notFoundCount;

        this.elemAwaitingProcessing = totalUploadCount - totalSoFar;
    }

    private String createNewBatch(String clientId){
        String newBatchId = clientId + ":"+UUID.randomUUID().toString(); 
        redisApi.createNewBatch(String.valueOf(newBatchId)); 
        redisApi.setBatchMetadata(newBatchId, "processingStartTime", String.valueOf(Instant.now())); 
        redisApi.setBatchMetadata(newBatchId, "status", "processing");
        return newBatchId;
    }
    
    private void createAllBatches(String clientId){ 
        private ArrayList<String> travelMethods = new ArrayList<>(); 
        this.requestBatchQueue = new HashMap<>();
        travelMethods.put("BIKE"); 
        travelMethods.put("WALK"); 
        travelMethods.put("DRIVE"); 
        travelMethods.put("TRANSIT");  

        for (method : travelMethods){
            RequestBatch batch = new RequestBatch();
            String newBatchId = createNewBatch();
            batch.setBatchId(newBatchId); 
            batch.setBatchId(clientId)
            batch.setBatchType(method); 
            batch.setBatchStatus("processing"); 
            batch.setBatchFillStatus(0);
            requestBatchQueue.put(method, batch);
        }
    }

    Logger logger = LoggerFactory.getLogger(BatchManager.class);

    //receive value. get student's travel mode. 
    //batchManager.handleIncoming(String travelMode, String value)
    //within batchManager, whenever new job added to queue: 
    //check that travelMode's batch. if it's under 625, add to it. 
    private void processBatch(RequestBatch batch){ 
        
        if (Integer.parseInt(this.elemProcessedInLastMin <= 2500)){ 
            RouteMatrixBuilder.asyncComputeRouteMatrix(batch.origins, batch.destinations, batch.batchType);
            kafkaTemplate.send("ROUTE_MATRIX_TOPIC", batch.clientId, "new batch incoming: check in redis to see if this is the last batch for clientId: "+clientId);
            redisApi.incrementGlobalStateMetadata("elemProcessedInLastMin", getRouteMatrixSize());
            redisApi.setBatchMetadata(batch.getBatchId(), "status", "processed");
            batch.setBatchStatus("processed");
        }
        else { 
            pollAndWait();
        }
    }

    private void deleteBatch(RequestBatch batch){
        redisApi.removeBatchFromRedis(batch.getBatchId());

        //get any values that were cached while waiting for element per minute rate limit to refresh
        Set<String> members = redisApi.getCachedValues(batch.getClientId());
        //add those values to the new batch and remove them from the cache
        for (String member : members) { 
            updateBatch(member); 
            redisApi.removeFromCache(member);
       }
    }

    private void handleIncomingJob(String clientId, String value){
        JsonNode node = mapper.readTree(value);
        String studentId = node.get("student").asText();
        String teacherId = node.get("teacher").asText(); 
        String travelMethod = node.get("travelMethod").asText();
        String studentAddress = redisApi.getStudentAddress(clientId+":"+studentId); 
        String teacherAddress = redisApi.getTeacherAddress(clientId+":"+teacherId); 

        RequestBatch batch = this.batchRequestQueue.get(travelMethod);
        String batchId = batch.getBatchId();
        String batchStatus = redisApi.getBatchMetadata(batchId, "status");

        String tryAddingToBatch; 
        if (batchStatus.equalsIgnoreCase("processing")){

            batchStatus = redisApi.getBatchMetadata(batchId, "status"); 
            tryAddingToBatch = batch.updateBatch(studentId, teacherId, studentAddress, teacherAddress);
            if (tryAddingToBatch == "batchFull"){
                processBatch(batch);
                redisApi.cacheValueForProcessing(value);
            }
        }
        else if (batchStatus.equalsIgnoreCase("processed")){  
            createNewBatch();
            deleteBatch(batch);
        }
    }

    @Scheduled(fixedRate = 60000)
    public void refreshRateLimitEachMinute(){
        redisApi.setGlobalStateMetadata("elemProcessedInLastmin", String.valueOf(0)); 
        redisApi.setGlobalStateMetadata("processingStartTime", String.valueOf(Instant.now())); 
    }

    private void pollAndWait(){ 
        Instant currentTime = Instant.now(); 
        String startTimeFromRedis = redisApi.getGlobalStateMetadata("processingStartTime"); 
        Instant startTime = Instant.parse(startTimeFromRedis); 
        Duration duration = Duration.between(startTime, currentTime); 
        while (duration.getSeconds() < 60) { 
            currentTime = Instant.now();
            duration = Duration.between(startTime, currentTime);
        }
    }
}

@Service
public class KafkaStream {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate; 

    @Autowired
    private RedisAPI redisApi; 

    @Autowired 
    private RouteMatrixBuilder routeMatrixBuilder;

    @Autowired
    private BatchManager batchManager;

    ObjectMapper mapper = new ObjectMapper();

    private static final Logger logger = LoggerFactory.getLogger(KafkaStreams.class); 
    private String batchId; 
    private String elemProcessedInLastMin = String.valueOf(0);
    private Instant processingStartTime = Instant.now(); 
    
    public KafkaStream(){
        batchId = redisApi.createNewBatch(batchId); 
        redisApi.setBatchMetadata(batchId, "elemProcessedInLastMin", elemProcessedInLastMin); 

        redisApi.setBatchMetadata(batchId, "status", "processsing"); 
        redisApi.setBatchMetadata(batchId, "processingStartTime", processingStartTime);
    }
    

    private static final Serde<String> STRING_SERDE = Serdes.String(); 
    @Autowired
    void buildPipeline(StreamsBuilder streamsBuilder) {
        KStream<String, String> messageStream = streamsBuilder.stream("MATCH_TOPIC", Consumed.with(STRING_SERDE, STRING_SERDE));

        messageStream.foreach((key, value) -> {
            try { 
                handleMessage(key, value); 
                logger.info("(Consumer) Message received: " + value);
            }catch (Exception e) {
                logger.error("Error processing message: " + value, e);
            }
        });
        
    }

    private void handleMessage(String key, String value) {
        String clientId = key; 
        batchManager.handleIncomingJob(clientId, value);
    }
}