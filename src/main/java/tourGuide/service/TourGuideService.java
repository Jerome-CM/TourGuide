package tourGuide.service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import rewardCentral.RewardCentral;
import tourGuide.dto.NearbyAttractionsDTO;
import tourGuide.dto.UserLocationListDTO;
import tourGuide.helper.InternalTestHelper;
import tourGuide.tracker.Tracker;
import tourGuide.user.User;
import tourGuide.user.UserReward;
import tripPricer.Provider;
import tripPricer.TripPricer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final RewardCentral rewardCentral;
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;

	private final int  NB_NEAREST_ATTRACTIONS = 5;

	ExecutorService executorService = Executors.newFixedThreadPool(50);
	
	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;
		this.rewardCentral = rewardCentral;
		
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

	public List<NearbyAttractionsDTO> getNearByAttractions(VisitedLocation visitedLocation, User user) {

		List<Attraction> attractions = gpsUtil.getAttractions();
		List<NearbyAttractionsDTO> nearestAttractions = new ArrayList<>();

		List<Future> futuresList = new ArrayList<>();
		for (Attraction attraction : attractions) {
			// Load data in a thread
			Callable changeUserNearest = () -> new NearbyAttractionsDTO(
					attraction.attractionName,
					new Location(attraction.longitude, attraction.latitude),
					visitedLocation.location,
					rewardsService.getDistance(attraction, visitedLocation.location),
					rewardsService.getRewardPoints(attraction, user)
			);

			// Execute threads pool
			Future mapUserNearestAttractions = executorService.submit(changeUserNearest);
			futuresList.add(mapUserNearestAttractions);
		};

		for (Future future: futuresList) {
			NearbyAttractionsDTO at = null;
			try {
				// Get result in a DTO
				at = (NearbyAttractionsDTO) future.get();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
			nearestAttractions.add(at);
		}

		executorService.shutdown();

		// Sort list and limit to 5 attractions for the view
		List<NearbyAttractionsDTO> listAttractionsSorted = nearestAttractions
				.stream()
				.sorted(Comparator.comparing(NearbyAttractionsDTO::getDistanceInMiles))
				.limit(NB_NEAREST_ATTRACTIONS)
				.collect(Collectors.toList());

		return listAttractionsSorted;
	}
	
	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() { 
		      public void run() {
		        tracker.stopTracking();
		      } 
		    }); 
	}

	public String getAllCurrentLocations(){
		List<User> userList = getAllUsers();
		UserLocationListDTO userLocationListDTO = new UserLocationListDTO();

		userList.forEach(user ->
		{
			userLocationListDTO.add(user.getUserId().toString(), user.getLastVisitedLocation().location);
		});

		// Display uuid : {longitude, latitude}
		return userLocationListDTO.toString();

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
