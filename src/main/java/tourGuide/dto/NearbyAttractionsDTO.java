package tourGuide.dto;

import gpsUtil.location.Location;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NearbyAttractionsDTO {

    private String tourtistAttractionName;
    private Location attractionLocation;

    private Location userLocation;

    private double distanceInMiles;

    private int reward;

}
