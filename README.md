

<a name="top"></a><img width="290" height="304" alt="logo" src="https://github.com/user-attachments/assets/a8b71c51-ff3d-4f78-9f65-691262ae189a" />

Automated scheduling for medical student clerkship rotations. <br><br>
[![Java Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Apache Kafka](https://img.shields.io/badge/Kafka-231F20?logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![Redis](https://img.shields.io/badge/Redis-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![Google Maps API](https://img.shields.io/badge/Google_Maps_API-4285F4?logo=googlemaps&logoColor=white)](https://developers.google.com/maps)
[![OR-Tools](https://img.shields.io/badge/OR--Tools-1A73E8?logo=google&logoColor=white)](https://developers.google.com/optimization)
[![React](https://img.shields.io/badge/React-61DAFB?logo=react&logoColor=black)](https://react.dev/)
[![Docker](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
<br>
## Table of Contents
- Try It Out
- What Is This?
- How It Works
- CSV Formatting

## [![What Is This?](https://img.shields.io/badge/%F0%9F%93%9D-What_Is_This%3F-blue)](#-what-is-this?)
At Canadian medical schools, all 3rd-year medical students are required to complete a clerkship rotation in Family Medicine. 

On these rotations, students are scheduled to work with real patients under the supervison of practicing family physicians in community training sites. These sites can be
geographically dispersed. 

In any given year, a class of medical students numbers in the hundreds; this group is divided into "tracks",  with at most few dozen students per track. Each track then completes its rotation on a rolling basis throughout the academic year. Each track's students are assigned to preceptors to satisfy the following constraints: <br>
<br>
(1) All preceptors must be assigned at most one student per track. <br>
(2) All non-driving students should be assigned the shortest possible commute. <br>
(3) All students should be assigned to preceptors whose work location and specialty meet the student's preferences. <br>
(4) All preceptors must be scheduled to teach only those tracks that they are actually available to teach. <br>

Scheduling is usually done manually by administrative staff at the medical school. This application automates the scheduling process. 

## [![How It Works](https://img.shields.io/badge/%E2%9A%99%EF%B8%8F-How_It_Works-green)](#-how-it-works)
Users upload two CSV files, one with student addresses and preferences and the other with teacher addresses and specialties. 
Data is stored in-memory in redis with namespaced keys per client with persistence guaranteed for only that session's lifetime (so, on disconnect, 
all that client's data gets flushed). 
<br>
<br>
A routing microservice uses Google's Routes API to compute a route matrix, which computes for 
each student the distance to that preceptor. A custom batching handler ensures that the API's rate limits are respected by dividing the user's 
full upload into batches of 25 x 25 submatrixes and limiting the total number of matrix requests per minute to < 2500. Each time a batch is 
processed the handler pushes a progress update to redis. 
<br>
<br>
On each new notification from the upstream source Kafka topic, the Scheduler polls redis and checks if the 
Route Service is done processing the entire upload. When it is, the Scheduler uses OR Tool's CP SAT solver to generate the best possible assignment 
of students to teachers. Users then view the matches in browser on an embedded Google Map, where they can dynamically interact with and edit the assignments before downloading them. 

## Example Use 
TODO 

## ❗ CSV Formatting 
The CSV files must be formatted like this, or the app will break:<br>
Student Data: ID | FirstName | LastName | EmailAddress | Address |TravelMethods | Session# <br>
Teacher Data: ID | FirstName | LastName | EmailAddress | Address | Availability
