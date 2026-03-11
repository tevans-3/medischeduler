package dfm.medischeduler_routeservice;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.api.gax.rpc.ServerStream;
import com.google.maps.routing.v2.ComputeRouteMatrixRequest;
import com.google.maps.routing.v2.RouteMatrixDestination;
import com.google.maps.routing.v2.RouteMatrixElement;
import com.google.maps.routing.v2.RouteMatrixElementCondition;
import com.google.maps.routing.v2.RouteMatrixOrigin;
import com.google.maps.routing.v2.RouteTravelMode;
import com.google.maps.routing.v2.RoutesClient;
import com.google.maps.routing.v2.RoutesSettings;
import com.google.maps.routing.v2.RoutingPreference;
import com.google.maps.routing.v2.TrafficModel;
import com.google.maps.routing.v2.TransitPreferences;
import com.google.maps.routing.v2.Units;
import com.google.protobuf.Timestamp;
import com.google.rpc.Code;

/**
 * Builds and executes Google Routes API ComputeRouteMatrix requests.
 *
 * For each batch of student origins and teacher destinations, this
 * component creates a {@link RoutesClient} with the appropriate API key
 * and field mask, sends a streaming ComputeRouteMatrix request, and
 * processes each {@link RouteMatrixElement} in the response.
 *
 * Successfully computed routes are cached in Redis via {@link RedisAPI}.
 * Failed or not-found routes are counted for progress tracking.
 */
@Component
public class RouteMatrixBuilder {

    @Value("${routes-api.api-key:}")
    private String apiKey;

    @Value("${routes-api.field-mask-bikeMode:originIndex,destinationIndex,distanceMeters,duration,condition,status}")
    private String fieldMaskBikeMode;

    @Value("${routes-api.field-mask-driveMode:originIndex,destinationIndex,distanceMeters,duration,condition,status}")
    private String fieldMaskDriveMode;

    @Value("${routes-api.field-mask-walkMode:originIndex,destinationIndex,distanceMeters,duration,condition,status}")
    private String fieldMaskWalkMode;

    @Value("${routes-api.field-mask-transitMode:originIndex,destinationIndex,distanceMeters,duration,condition,status}")
    private String fieldMaskTransitMode;

    @Autowired
    private RedisAPI redisApi;

    private static final Logger log = LoggerFactory.getLogger(RouteMatrixBuilder.class);

    /**
     * Creates a {@link RoutesClient} configured with the API key and field mask
     * sent as custom headers.
     *
     * @param fieldMask the field mask controlling which response fields are returned
     * @return a configured RoutesClient
     * @throws Exception if the client cannot be created
     */
    public RoutesClient createRoutesClientWithApiKey(String fieldMask) throws Exception {
        RoutesSettings routesSettings = RoutesSettings.newBuilder()
                .setHeaderProvider(() -> {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("x-goog-api-key", apiKey);
                    headers.put("x-goog-fieldmask", fieldMask);
                    return headers;
                })
                .build();
        return RoutesClient.create(routesSettings);
    }

    /**
     * Sends a ComputeRouteMatrix request to the Google Routes API and processes
     * each element in the streamed response.
     *
     * For each response element:
     * <ul>
     *   <li>If the status is not OK, the failure counter is incremented.</li>
     *   <li>If no valid route exists, the not-found counter is incremented.</li>
     *   <li>Otherwise the route distance and duration are cached in Redis and
     *       the processed counter is incremented.</li>
     * </ul>
     *
     * @param origins      the list of origin waypoints (student addresses)
     * @param destinations the list of destination waypoints (teacher addresses)
     * @param travelMode   one of BIKE, DRIVE, WALK, TRANSIT
     * @param batchId      the batch identifier for index-map lookups
     * @param clientId     the client identifier for Redis key namespacing
     * @throws Exception if the API call fails
     */
    public void asyncComputeRouteMatrix(ArrayList<RouteMatrixOrigin> origins,
                                        ArrayList<RouteMatrixDestination> destinations,
                                        String travelMode, String batchId,
                                        String clientId) throws Exception {
        String fieldMask;
        int modeNumber;

        if ("BIKE".equals(travelMode)) {
            fieldMask = fieldMaskBikeMode;
            modeNumber = 2;
        } else if ("DRIVE".equals(travelMode)) {
            fieldMask = fieldMaskDriveMode;
            modeNumber = 1;
        } else if ("WALK".equals(travelMode)) {
            fieldMask = fieldMaskWalkMode;
            modeNumber = 3;
        } else if ("TRANSIT".equals(travelMode)) {
            fieldMask = fieldMaskTransitMode;
            modeNumber = 4;
        } else {
            log.error("Bad parameter: invalid travel mode: {}", travelMode);
            throw new IllegalArgumentException("Bad parameter: invalid travel mode: " + travelMode);
        }

        try (RoutesClient routesClient = createRoutesClientWithApiKey(fieldMask)) {
            ComputeRouteMatrixRequest request = ComputeRouteMatrixRequest.newBuilder()
                    .addAllOrigins(origins)
                    .addAllDestinations(destinations)
                    .setTravelMode(RouteTravelMode.forNumber(modeNumber))
                    .setRoutingPreference(RoutingPreference.TRAFFIC_UNAWARE)
                    .setDepartureTime(Timestamp.getDefaultInstance())
                    .setLanguageCode("en")
                    .setRegionCode("CA")
                    .setUnits(Units.METRIC)
                    .setTrafficModel(TrafficModel.BEST_GUESS)
                    .setTransitPreferences(TransitPreferences.getDefaultInstance())
                    .build();

            ServerStream<RouteMatrixElement> stream =
                    routesClient.computeRouteMatrixCallable().call(request);

            for (RouteMatrixElement element : stream) {
                if (element.getStatus().getCode() != Code.OK_VALUE) {
                    log.warn("Failed to compute route: {}", element.getStatus().getMessage());
                    redisApi.updateRoutesProgress("failedSoFar", 1, clientId);
                    continue;
                }

                int originIndex = element.getOriginIndex();
                int destinationIndex = element.getDestinationIndex();

                if (element.getCondition() != RouteMatrixElementCondition.ROUTE_EXISTS) {
                    log.info("No valid route between Origin:{} and Destination:{}", originIndex, destinationIndex);
                    redisApi.updateRoutesProgress("notFoundSoFar", 1, clientId);
                    continue;
                }

                long meters = element.getDistanceMeters();
                long durationSeconds = element.getDuration().getSeconds();

                log.info("Route Origin:{} -> Destination:{}: {} meters, {} seconds",
                        originIndex, destinationIndex, meters, durationSeconds);

                String studentId = redisApi.lookupStudentByOriginIndex(originIndex, batchId, clientId);
                String teacherId = redisApi.lookupTeacherByDestIndex(destinationIndex, batchId, clientId);

                redisApi.cacheRouteData(clientId, studentId, teacherId, meters, durationSeconds, travelMode);
                redisApi.updateRoutesProgress("processedSoFar", 1, clientId);
            }
        } catch (Exception e) {
            log.error("Error computing route matrix for batch {}: {}", batchId, e.getMessage(), e);
            throw e;
        }
    }
}
