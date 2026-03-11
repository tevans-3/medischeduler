package dfm.medischeduler_routeservice;

import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Represents a single request batch for the Google Routes API.
 *
 * The Routes API limits each ComputeRouteMatrix request to at most 625
 * matrix elements (25 origins x 25 destinations). A {@code RequestBatch}
 * accumulates student origins and teacher destinations until the batch
 * is full, at which point it is handed off to {@link RouteMatrixBuilder}
 * for execution.
 *
 * Each batch has a type corresponding to a travel mode (BIKE, WALK,
 * DRIVE, TRANSIT) because the Routes API only allows one travel mode
 * per request.
 */
@Component
@Scope("prototype")
public class RequestBatch {

    /** Maximum number of matrix elements per Routes API request. */
    public static final int MAX_MATRIX_ELEMENTS = 625;

    @Autowired
    private RedisAPI redisApi;

    private String batchId;
    private String clientId;
    private String batchType;
    private String batchStatus;
    private int batchFillStatus;

    private final ArrayList<String> origins = new ArrayList<>();
    private final ArrayList<String> destinations = new ArrayList<>();

    // --- Getters and Setters ---

    public String getBatchId() {
        return this.batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getClientId() {
        return this.clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getBatchType() {
        return this.batchType;
    }

    public void setBatchType(String batchType) {
        this.batchType = batchType;
    }

    public String getBatchStatus() {
        return this.batchStatus;
    }

    public void setBatchStatus(String batchStatus) {
        this.batchStatus = batchStatus;
    }

    public int getBatchFillStatus() {
        return this.batchFillStatus;
    }

    public void setBatchFillStatus(int batchFillStatus) {
        this.batchFillStatus = batchFillStatus;
    }

    public ArrayList<String> getOrigins() {
        return this.origins;
    }

    public ArrayList<String> getDestinations() {
        return this.destinations;
    }

    /**
     * Returns the current number of matrix elements in this batch
     * (origins count * destinations count).
     */
    public int getRouteMatrixSize() {
        return origins.size() * destinations.size();
    }

    /**
     * Tests whether adding the given number of origins and destinations
     * would keep the batch within the 625-element limit.
     *
     * @param addNumOrigins      number of new origins to add
     * @param addNumDestinations number of new destinations to add
     * @return the projected matrix size after the addition
     */
    public int testRouteMatrixSize(int addNumOrigins, int addNumDestinations) {
        return (origins.size() + addNumOrigins) * (destinations.size() + addNumDestinations);
    }

    /**
     * Attempts to add a student-teacher pair to this batch.
     *
     * For each origin-destination pair, the Routes API response includes an
     * originIndex and a destinationIndex corresponding to the position of
     * that address in the request's arrays. We cache these index mappings
     * in Redis so that we can resolve the response back to student/teacher
     * IDs after the API call completes.
     *
     * @param studentId      the student ID
     * @param teacherId      the teacher ID
     * @param studentAddress the student's address string
     * @param teacherAddress the teacher's address string
     * @return "batchUpdated" if the pair was added, "batchFull" if the batch is at capacity
     */
    public String updateBatch(String studentId, String teacherId,
                              String studentAddress, String teacherAddress) {
        if (testRouteMatrixSize(1, 1) <= MAX_MATRIX_ELEMENTS) {
            origins.add(studentAddress);
            destinations.add(teacherAddress);

            redisApi.cacheIndexMapsStudentToResponse(clientId, studentId, origins, batchId);
            redisApi.cacheIndexMapsTeacherToResponse(clientId, teacherId, destinations, batchId);

            batchFillStatus++;
            return "batchUpdated";
        } else {
            return "batchFull";
        }
    }
}
