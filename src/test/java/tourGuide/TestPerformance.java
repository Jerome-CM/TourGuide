package tourGuide;

import static org.junit.Assert.assertTrue;
import static org.springframework.test.util.AssertionErrors.assertEquals;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import tourGuide.dto.NearbyAttractionsDTO;
import tourGuide.helper.InternalTestHelper;
import tourGuide.service.RewardsService;
import tourGuide.service.TourGuideService;
import tourGuide.user.User;
import tourGuide.user.UserReward;

public class TestPerformance {
	
	/*
	 * A note on performance improvements:
	 *     
	 *     The number of users generated for the high volume tests can be easily adjusted via this method:
	 *     
	 *     		InternalTestHelper.setInternalUserNumber(100000);
	 *     
	 *     
	 *     These tests can be modified to suit new solutions, just as long as the performance metrics
	 *     at the end of the tests remains consistent. 
	 * 
	 *     These are performance metrics that we are trying to hit:
	 *     
	 *     highVolumeTrackLocation: 100,000 users within 15 minutes:
	 *     		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
     *
     *     highVolumeGetRewards: 100,000 users within 20 minutes:
	 *          assertTrue(TimeUnit.MINUTES.toSeconds(20) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 */
	@Before
	public void setUp() throws Exception {

		Locale.setDefault(Locale.US);
	}

	ExecutorService executorService = Executors.newFixedThreadPool(100);

	@Test
	public void highVolumeTrackLocation() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		// Users should be incremented up to 100,000, and test finishes within 15 minutes
		RewardCentral rewardCentral = new RewardCentral();
		InternalTestHelper.setInternalUserNumber(1000);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService, rewardCentral);

		List<User> allUsers = new ArrayList<>();
		allUsers = tourGuideService.getAllUsers();
		List<Future> futureList = new ArrayList<>();

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		for(User user : allUsers) {
			Callable signatureLocation = () -> tourGuideService.trackUserLocation(user);
			Future getLocationInFuture = executorService.submit(signatureLocation);
			futureList.add(getLocationInFuture);
		}

		List<VisitedLocation> realVisitedInformations = new ArrayList<>();
		for(Future future : futureList){
			VisitedLocation test = null;
			try {
				test = (VisitedLocation) future.get();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
			realVisitedInformations.add(test);
		}

		System.out.println(realVisitedInformations.get(0).location.longitude);
		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		System.out.println("highVolumeTrackLocation: Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}

	@Test
	public void highVolumeGetRewards() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		// Users should be incremented up to 100,000, and test finishes within 20 minutes
		InternalTestHelper.setInternalUserNumber(100000);
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		RewardCentral rewardCentral = new RewardCentral();
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService, rewardCentral);
		Attraction attraction = gpsUtil.getAttractions().get(0);
		CopyOnWriteArrayList<User> allUsers = new CopyOnWriteArrayList<>();
		allUsers.addAll(tourGuideService.getAllUsers());

		try {
			allUsers.forEach(u -> {
				Runnable runnable = () -> {
					u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date()));
					rewardsService.calculateRewards(u);
					assertTrue(u.getUserRewards().size() > 0);
				};
				executorService.execute(runnable);
			});

			executorService.shutdown();
			executorService.awaitTermination(20, TimeUnit.MINUTES);

		} catch (InterruptedException e){
			e.printStackTrace();
		}

		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		System.out.println("highVolumeGetRewards: Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
		assertTrue(TimeUnit.MINUTES.toSeconds(20) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}
	
}
