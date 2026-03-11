package dfm.medischeduler_common.model;

import java.util.HashMap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Represents a medical student participating in a clerkship rotation.
 *
 * Each student has an address (used for commute calculations), a preferred
 * travel method (DRIVE, TRANSIT, BIKE, or WALK), specialty interests that
 * influence matching, weighted preferences that control how strongly each
 * factor affects the objective function, and a session number indicating
 * which track the student belongs to.
 *
 * Instances are created via the {@link Builder} pattern or deserialized
 * from JSON by Jackson (which requires the no-arg constructor).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Student {

    @NotNull
    private String id;

    @Size(min = 1, max = 50)
    private String firstName;

    @Size(min = 1, max = 50)
    private String lastName;

    @Email
    private String email;

    @NotNull
    private String address;

    @NotNull
    private String travelMethod;

    @NotNull
    private HashMap<String, String> specialtyInterests;

    @NotNull
    private HashMap<String, String> weightedPreferences;

    @NotNull
    private String sessionNum;

    /** No-arg constructor required by Jackson for deserialization. */
    public Student() {}

    private Student(Builder builder) {
        this.id = builder.id;
        this.firstName = builder.firstName;
        this.lastName = builder.lastName;
        this.email = builder.email;
        this.address = builder.address;
        this.travelMethod = builder.travelMethod;
        this.specialtyInterests = builder.specialtyInterests;
        this.weightedPreferences = builder.weightedPreferences;
        this.sessionNum = builder.sessionNum;
    }

    /**
     * Builder for constructing {@link Student} instances with a fluent API.
     *
     * Example:
     * <pre>
     * Student student = new Student.Builder()
     *     .id("S001")
     *     .firstName("Alice")
     *     .lastName("Smith")
     *     .email("alice@example.com")
     *     .address("123 Main St, Edmonton, AB")
     *     .travelMethod("DRIVE")
     *     .specialtyInterests(interests)
     *     .weightedPreferences(prefs)
     *     .sessionNum("1")
     *     .build();
     * </pre>
     */
    public static class Builder {
        private String id;
        private String firstName;
        private String lastName;
        private String email;
        private String address;
        private String travelMethod;
        private HashMap<String, String> specialtyInterests;
        private HashMap<String, String> weightedPreferences;
        private String sessionNum;

        public Builder id(String id) { this.id = id; return this; }
        public Builder firstName(String firstName) { this.firstName = firstName; return this; }
        public Builder lastName(String lastName) { this.lastName = lastName; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder address(String address) { this.address = address; return this; }
        public Builder travelMethod(String travelMethod) { this.travelMethod = travelMethod; return this; }
        public Builder specialtyInterests(HashMap<String, String> s) { this.specialtyInterests = s; return this; }
        public Builder weightedPreferences(HashMap<String, String> w) { this.weightedPreferences = w; return this; }
        public Builder sessionNum(String sessionNum) { this.sessionNum = sessionNum; return this; }

        public Student build() { return new Student(this); }
    }

    // --- Getters ---
    public String getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public String getTravelMethod() { return travelMethod; }
    public HashMap<String, String> getSpecialtyInterests() { return specialtyInterests; }
    public HashMap<String, String> getWeightedPreferences() { return weightedPreferences; }
    public String getSessionNum() { return sessionNum; }

    // --- Setters ---
    public void setId(String id) { this.id = id; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setEmail(String email) { this.email = email; }
    public void setAddress(String address) { this.address = address; }
    public void setTravelMethod(String travelMethod) { this.travelMethod = travelMethod; }
    public void setSpecialtyInterests(HashMap<String, String> s) { this.specialtyInterests = s; }
    public void setWeightedPreferences(HashMap<String, String> w) { this.weightedPreferences = w; }
    public void setSessionNum(String sessionNum) { this.sessionNum = sessionNum; }
}
