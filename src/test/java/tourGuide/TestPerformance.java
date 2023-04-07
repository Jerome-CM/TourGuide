package tourGuide;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.Before;
import org.junit.Test;
import rewardCentral.RewardCentral;
import tourGuide.helper.InternalTestHelper;
import tourGuide.service.RewardsService;
import tourGuide.service.TourGuideService;
import tourGuide.user.User;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;

import static org.junit.Assert.assertTrue;

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
		RewardCentral rewardCentral = new RewardCentral();
		// Users should be incremented up to 100,000, and test finishes within 15 minutes
		InternalTestHelper.setInternalUserNumber(10);
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
			VisitedLocation visit = null;
			try {
				visit = (VisitedLocation) future.get();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
			realVisitedInformations.add(visit);
		}

		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		assertTrue("", realVisitedInformations.size() > 0 );
		System.out.println("highVolumeTrackLocation: Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	}

	@Test
	public void highVolumeGetRewards() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		// Users should be incremented up to 100,000, and test finishes within 20 minutes
		InternalTestHelper.setInternalUserNumber(10);
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
