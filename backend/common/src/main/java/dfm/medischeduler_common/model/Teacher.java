package dfm.medischeduler_common.model;

import java.util.HashMap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Represents a preceptor (teacher/physician) who supervises medical students
 * during clerkship rotations.
 *
 * Each teacher has an address (used for commute calculations), an availability
 * map indicating which tracks/sessions they can take students for, and
 * specialty interests that influence student matching.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Teacher {

    @NotNull private String id;
    @Size(min = 1, max = 50) private String firstName;
    @Size(min = 1, max = 50) private String lastName;
    @Email private String email;
    @NotNull private String address;
    @NotNull private HashMap<String, String> availability;
    @NotNull private HashMap<String, String> specialtyInterests;

    public Teacher() {}

    private Teacher(Builder builder) {
        this.id = builder.id;
        this.firstName = builder.firstName;
        this.lastName = builder.lastName;
        this.email = builder.email;
        this.address = builder.address;
        this.availability = builder.availability;
        this.specialtyInterests = builder.specialtyInterests;
    }

    public static class Builder {
        private String id;
        private String firstName;
        private String lastName;
        private String email;
        private String address;
        private HashMap<String, String> availability;
        private HashMap<String, String> specialtyInterests;

        public Builder id(String id) { this.id = id; return this; }
        public Builder firstName(String firstName) { this.firstName = firstName; return this; }
        public Builder lastName(String lastName) { this.lastName = lastName; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder address(String address) { this.address = address; return this; }
        public Builder availability(HashMap<String, String> a) { this.availability = a; return this; }
        public Builder specialtyInterests(HashMap<String, String> s) { this.specialtyInterests = s; return this; }

        public Teacher build() { return new Teacher(this); }
    }

    public String getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public HashMap<String, String> getAvailability() { return availability; }
    public HashMap<String, String> getSpecialtyInterests() { return specialtyInterests; }

    public void setId(String id) { this.id = id; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setEmail(String email) { this.email = email; }
    public void setAddress(String address) { this.address = address; }
    public void setAvailability(HashMap<String, String> a) { this.availability = a; }
    public void setSpecialtyInterests(HashMap<String, String> s) { this.specialtyInterests = s; }
}
