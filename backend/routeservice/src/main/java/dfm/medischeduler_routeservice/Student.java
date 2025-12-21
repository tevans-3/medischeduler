package dfm.medischeduler_routeservice; 

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger; 
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.RedisHash;
import com.fasterxml.jackson.databind.ObjectMapper;


public class Student {
    /*
     * St
     * 
     */
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
    public String travelMethod;

    @NotNull
    public HashMap<String, String> specialtyInterests; 

    @NotNull
    public HashMap<String, String> weightedPreferences;

    @NotNull
    public String sessionNum; 

    private Student(Builder builder){
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
    public static class Builder {
        private String id; 
        private String firstName;
        private String lastName; 
        private String email;
        private String address;
        private String travelMethods;
        private HashMap<String, String> specialtyInterests;
        private HashMap<String, String> weightedPreferences;
        private String sessionNum;
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

    public Builder travelMethod(String travelMethod){
        this.travelMethod = travelMethod;
        return this;
    }

    public Builder specialtyInterests(HashMap<String, String> specialtyInterests){
        this.specialtyInterests = specialtyInterests;
        return this;
    }

    public Builder weightedPreferences(HashMap<String, String> weightedPreferences){
        this.weightedPreferences = weightedPreferences; 
        return this; 
    }

    public Builder sessionNum(String sessionNum){
        this.sessionNum = sessionNum;
        return this;
    }

    public Student build(){
        return new Student(this);
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

    public getTravelMethod(){
        return this.travelMethodd;
    }

    public getSpecialtyInterests(){
        return this.specialtyInterests;
    }

    public getWeightedPreferences(){
        return this.weightedPreferences;
    }

    public getSessionNum(){
        return this.sessionNum;
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

    public setTravelMethod(HashMap<String, String> travelMethod){
        this.travelMethod = travelMethod;
    }

    public setSpecialtyInterests(HashMap<String, String> specialtyInterests){
        this.specialtyInterests = specialtyInterests;
    }

    public setWeightedPreferences(HashMap<String, String> weightedPreferences){
        this.weightedPreferences = weightedPreferences;
    }

    public setSessionNum(String sessionNum){
        this.sessionNum = sessionNum;
    }
    
}

/*
*  Example initialization: 
 * Student student = new Student.Builder()
 *                        .id('citizen!')
 *                        .firstName('tu')
 *                        .lastName('stultus')
 *                        .email('es!')
 *                        .address('lege')
 *                        .travelMethod(<'onionem': 'et disce'>)
 *                        .specialtyInterests(<'et laude':'et vive bene'>)
 *                        .sessionNum('0asja99jq.')
 *                        .build();
 */