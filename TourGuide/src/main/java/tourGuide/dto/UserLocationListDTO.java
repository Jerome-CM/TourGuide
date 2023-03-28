package tourGuide.dto;

import com.jsoniter.output.JsonStream;
import gpsUtil.location.Location;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class UserLocationListDTO {

    private Map<String, Location> locationMap = new HashMap<>();

    public void add(String userId, Location location){
        locationMap.put(userId, location);
    }

    @Override
    public String toString(){
        return JsonStream.serialize(locationMap);
    }
}
