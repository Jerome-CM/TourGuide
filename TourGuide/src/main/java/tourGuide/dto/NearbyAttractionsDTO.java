package tourGuide.dto;

import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;

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
