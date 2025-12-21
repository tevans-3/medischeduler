package dfm.medischeduler_routeservice;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.RedisHash;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Teacher {

    @NotNull
    public String id; 

    @Size(min = 3, max = 50)
    public String firstName;

    @Size(min = 3, max = 50)
    public String lastName; 

    @Email 
    public String email; 

    @NotNull
    public String address; 

    @NotNull
    public HashMap<String, String> availability; 

    @NotNull
    public HashMap<String, String> specialtyInterests;

    private Teacher(Builder builder){
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
    }
    
    public Builder id(String id){
        this.id = id;
        return this;
    }
    public Builder firstName(String firstName){
        this.firstName = firstName;
        return this;
    }
    public Builder lastName(String lastName){
        this.lastName = lastName;
        return this;
    }

    public Builder email(String email){
        this.email = email;
        return this;
    }

    public Builder address(String address){
        this.address = address;
        return this;
    }

    public Builder availability(HashMap<String, String> availability){
        this.availability = availability;
        return this;
    }

    public Builder specialtyInterests(HashMap<String, String> specialtyInterests){
        this.specialtyInterests = specialtyInterests;
        return this;
    }

    public Teacher build(){
        return new Teacher(this);
    }

    public getId(){
        return this.id; 
    }

    public getFirstName(){
        return this.firstName; 
    }

    public getLastName(){
        return this.lastName;
    }

    public getEmail(){
        return this.email;
    }

    public getAddress(){
        return this.address;
    }

    public getAvailability(){
        return this.travelMethods;
    }

    public getSpecialtyInterests(){
        return this.specialtyInterests;
    }

    public setId(String id){
        this.id = id; 
    }

    public setFirstName(String firstName){
        this.firstName = firstName;
    }

    public setLastName(String lastName){
        this.lastName = lastName;
    }

    public setEmail(String email){
        this.email = email;
    }

    public setAddress(String address){
        this.address = address;
    }

    public setAvailability(HashMap<String, String> availability){
        this.availability = availability; 
    }

    public setSpecialtyInterests(HashMap<String, String> interests){
        this.specialtyInterests = interests;
    }
}

/*
*  Example initialization: 
 * Teacher teacher = new Teacher.Builder()
 *                        .id('citizen!')
 *                        .firstName('tu')
 *                        .lastName('stultus')
 *                        .email('es!')
 *                        .address('lege')
 *                        .availability(<'onionem':'et disce'>) 
 *                        .specialtyInterests(<'et laude':'et vive bene'>)
 *                        .build();
 */