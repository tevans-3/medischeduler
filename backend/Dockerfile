FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/my-app.jar /app/my-app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "medischeduler.jar"]

#batching
#rate limiting logic- queue requests, track rate limit and backoff or delay as needed 
#caching- cache every address computation and check cache before every API call 

#max is 625 elements (25 students X 25 teachers) per route matrix for non-transit, 
#and 100 elements for transit (10 x 10). 
#routes API rate limit is 3000 elements per minute. 
#4 25 x 25 matrices per minute for non-transit routes. 
#
#Drop-down List with teachers under each student name.

#Application structure: 
#Frontend: webpage form & data input 
#UI is a form and a file upload box
#form can have data (student and teacher addresses) pasted or entered into it 
#on form submission, addresses get sent to a messaging queue for processing 
#   form data sent via post REST API 
#   JavaScript FileReader API to handle the file upload and parse CSV data on frontend 

#Messaging queue for asynchronous processing 
# Kafka (okay it's a distributed stream but I'm using it as MQ)
# distinct topics for input data (addresses-input), route calculations (routes-requests), 
# assignments-output 
#
#Redis to store student, teacher and route data 
#set expiration policies to avoid buildup
#students:{studentId} = {name, address, ...}
#teachers:{teacherId} = {name, address, ...}
#routes:{studentId}:{teacherId} = {driving_distance, biking_distance, walking_distance, transit_distance}
#Redis ZSET: "student:{student_id}:teacher_distances"
#student id is the entry i of that student's row in the distance matrix 
#teacher id is the entry j of that teacher's column in the distance matrix
#cache key: distance:{originHash}:{destinationHash}:{mode} to look up and 
#reuse already computed distances

#Google Routes API calculates: driving distance, biking distance, walking distance, transit distance 
#Need to implement separate block-dispatching services for (1) driving,biking,walking and (2) transit as
#the rate limits are different. 
#Need to implement retries
#Need to cache all address distances in memory to prevent redundant computation 
#Spring WebClient 
#look into circuit breaker pattern

#add logging and metrics on Kafka consumers/producers and Redis usage 
#to track bottlenecks/failures

#Linear Sum Assignment Solver: 
#OR-Tools Linear Sum Assignment Solver solves the assignment problem
#RouteService class computes the distance matrix and passes this data to the 
#SchedulerService, which uses OR-Tools to find the optimal assignment. 

#Workflow: 
#Calculate all student-teacher distances. 
#Add distance matrix to a message queue for further processing. 
#       ^need to store (i,j) as attributes in redis student and teacher data stores 
#        in order to identify which student is which
#  scheduler listens for messages, grabs the distance matrix from Redis, and then uses OR-Tools
#Results are then displayed on a map as described below.

#Use Spring Security and environment variables to secure endpoints 

#"Best Match" at the top in green, with alternatives under a heading like "Other Possible Matches" (or a similar name).
#Map View: Dynamic updates of routes as the user selects teachers.
#Stat Window: Show route differences in time/distance between optimal and selected routes.
#Below Map: List view of optimal assignments, with the ability to manually edit assignments.

#Development Plan: 
#(1) Messaging Queue 
#(2) Redis 
#(3) Routes Logic
#(4) Scheduler Logic 
#(5) Data input frontend 
#(6) Display results frontend
