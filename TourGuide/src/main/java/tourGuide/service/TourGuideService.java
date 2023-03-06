package tourGuide.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import tourGuide.dto.NearbyAttractionsDTO;
import tourGuide.helper.InternalTestHelper;
import tourGuide.tracker.Tracker;
import tourGuide.user.User;
import tourGuide.user.UserReward;
import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;
	
	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;
		
		if(testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}
	
	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}
	
	public VisitedLocation getUserLocation(User user) {
		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ?
			user.getLastVisitedLocation() :
			trackUserLocation(user);
		return visitedLocation;
	}
	
	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}
	
	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}
	
	public void addUser(User user) {
		if(!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}
	
	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(), user.getUserPreferences().getNumberOfAdults(), 
				user.getUserPreferences().getNumberOfChildren(), user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}
	
	public VisitedLocation trackUserLocation(User user) {
		VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
		user.addToVisitedLocations(visitedLocation);
		rewardsService.calculateRewards(user);
		return visitedLocation;
	}

	/*public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
		List<Attraction> nearbyAttractions = new ArrayList<>();
		for(Attraction attraction : gpsUtil.getAttractions()) {
			if(rewardsService.isWithinAttractionProximity(attraction, visitedLocation.location)) {
				nearbyAttractions.add(attraction);
			}
		}
		
		return nearbyAttractions;
	}*/

	public List<NearbyAttractionsDTO> getNearByAttractions(VisitedLocation visitedLocation, String username) {

		List<NearbyAttractionsDTO> returnList = new ArrayList<>();

		// Load minimum 5 attraction
		int loadFirstTime = 0;
		// Load attraction nearby only
		double distanceInstantMax = 0;
		for(Attraction attraction : gpsUtil.getAttractions()) {

			double distanceUserBetweenAttraction = rewardsService.getDistance(visitedLocation.location, new Location(attraction.latitude, attraction.longitude));

			NearbyAttractionsDTO dto = new NearbyAttractionsDTO();
			HashMap<String, String> infoAttraction = new HashMap<>();
			HashMap<String, String> infoUser = new HashMap<>();
			// if he have 5 loaded attractions,
			// We add the next attraction only if it is closer than the attractions already added before
			if(loadFirstTime > 5){
				if(distanceInstantMax > distanceUserBetweenAttraction){

					// User user = getUser(username);
					dto.setTourtistAttractionName(attraction.attractionName);
					infoAttraction.put("Latitude", String.valueOf(attraction.latitude));
					infoAttraction.put("Longitude", String.valueOf(attraction.longitude));
					// infoAttraction.put("Reward",); // TODO ADD REWARD
					dto.setAttractionLocation(infoAttraction);
					infoUser.put("Latitude", String.valueOf(visitedLocation.location.latitude));
					infoUser.put("Longitude", String.valueOf(visitedLocation.location.longitude));
					dto.setUserLocation(infoUser);
					dto.setDistanceInMiles(distanceUserBetweenAttraction);
					distanceInstantMax = distanceUserBetweenAttraction;
					returnList.add(dto);
					logger.info("--- distanceInstantMax in the if 5 : {} ---", distanceInstantMax);
				}
			// load 5 attractions minimum
			} else {
				// User user = getUser(username);
				dto.setTourtistAttractionName(attraction.attractionName);
				infoAttraction.put("Latitude", String.valueOf(attraction.latitude));
				infoAttraction.put("Longitude", String.valueOf(attraction.longitude));
				// infoAttraction.put("Reward",); // TODO ADD REWARD
				dto.setAttractionLocation(infoAttraction);
				infoUser.put("Latitude", String.valueOf(visitedLocation.location.latitude));
				infoUser.put("Longitude", String.valueOf(visitedLocation.location.longitude));
				dto.setUserLocation(infoUser);
				dto.setDistanceInMiles(distanceUserBetweenAttraction);
				if(distanceInstantMax == 0){
					distanceInstantMax = distanceUserBetweenAttraction;
				} else if (distanceInstantMax > distanceUserBetweenAttraction){
					distanceInstantMax = distanceUserBetweenAttraction;
				}
				logger.info("--- distanceInstantMax in the else : {} ---", distanceInstantMax);
				returnList.add(dto);
			}

			loadFirstTime++;

		}

		return sortProximityAttraction(returnList);

	}

	private List<NearbyAttractionsDTO> sortProximityAttraction(List<NearbyAttractionsDTO> list){

		List<NearbyAttractionsDTO> listSorted = new ArrayList<>();

		for(int x = 0; x < list.size(); x++){
			logger.info("--- in first loop x = {} ---", x);
			NearbyAttractionsDTO getElement = list.get(x); // ELEMENT 2 loaded
			logger.info("--- element loaded : {} ---", getElement);
			if(x == 0){
				logger.info("--- if first entry : yes, first element load ---");
				listSorted.add(getElement);
			} else {
				int addToIndex = 0;
				for(int i = 0; i < listSorted.size(); i++){
					logger.info("--- in second loop x = {} , i = {} ---", x, i);
					logger.info("--- getElement.getDistanceInMiles() = {} ---", getElement.getDistanceInMiles());
					logger.info("--- listSorted.get(i).getDistanceInMiles() = {} ---", listSorted.get(i).getDistanceInMiles());
					logger.info("if element is best proximity ? {}", getElement.getDistanceInMiles() < listSorted.get(i).getDistanceInMiles());
					if(getElement.getDistanceInMiles() < listSorted.get(i).getDistanceInMiles()){
						addToIndex = 0;
						for(int z = 0; z < listSorted.size(); z++){
							if(getElement.getDistanceInMiles() < listSorted.get(z).getDistanceInMiles()){
								addToIndex++;
							}
						}
						logger.info("Element {} at the place {}", getElement, addToIndex);
						listSorted.add(addToIndex, getElement);
					} else {
						listSorted.add(listSorted.size(), getElement);
					}

				}

			}

		}

		return listSorted;
	}
	
	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() { 
		      public void run() {
		        tracker.stopTracking();
		      } 
		    }); 
	}

	public List<Map<String, Map<String, Double>>> getAllCurrentLocations(){
		List<Map<String, Map<String, Double>>> returnList = new ArrayList<>();

		for(int i = 0; i < InternalTestHelper.getInternalUserNumber(); i++){

			User user = getUser("internalUser"+i);
			Map<String, Double> userPosition = new HashMap<>();
			Map<String, Map<String, Double>> formatResponse = new HashMap<>();

			String id = String.valueOf(user.getUserId());

			userPosition.put("longitude", user.getLastVisitedLocation().location.longitude);
			userPosition.put("latitude", user.getLastVisitedLocation().location.latitude);

			formatResponse.put(id, userPosition);

			returnList.add(formatResponse);

		}

		return returnList;
	}
	
	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();
	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);
			
			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}
	
	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i-> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(), new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}
	
	private double generateRandomLongitude() {
		double leftLimit = -180;
	    double rightLimit = 180;
	    return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}
	
	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
	    double rightLimit = 85.05112878;
	    return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}
	
	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
	    return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}
	
}
