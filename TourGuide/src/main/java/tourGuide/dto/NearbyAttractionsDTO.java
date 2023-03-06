package tourGuide.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.List;

@Data
public class NearbyAttractionsDTO {

    private String tourtistAttractionName;
    private HashMap<String, String> attractionLocation; //lat/long, distance, reward
    private HashMap<String, String> userLocation;

    private double distanceInMiles;

// Les points de récompense d'attraction peuvent être collectés auprès de RewardsCentral


    /*Name of Tourist attraction,
     Tourist attractions lat/long,
     The user's location lat/long,
     The distance in miles between the user's location and each of the attractions.
     The reward points for visiting each Attraction.*/
}
