package com.openclassrooms.tourguide.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
    private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	
	private static final ExecutorService executor = Executors.newFixedThreadPool(100);
	
	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}
	
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}
	
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}
	
	public CompletableFuture<Void> calculateRewardsForAllUsers(List<User> allUsers) {
		List<CompletableFuture<Void>> futures = allUsers.stream()
		        .map(user -> CompletableFuture.runAsync(() -> calculateRewards(user), executor))
		        .collect(Collectors.toList());

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}
	
	public void calculateRewards(User user) {
	    List<VisitedLocation> userLocations = new ArrayList<>(user.getVisitedLocations());
	    List<Attraction> attractions = new ArrayList<>(gpsUtil.getAttractions());

	    Set<String> rewardedAttractions = user.getUserRewards().stream()
	            .map(r -> r.attraction.attractionName)
	            .collect(Collectors.toSet());

	    attractions.parallelStream()
	        .filter(attraction -> !rewardedAttractions.contains(attraction.attractionName))
	        .flatMap(attraction -> userLocations.stream()
	                .filter(visitedLocation -> nearAttraction(visitedLocation, attraction))
	                .map(visitedLocation -> new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)))
	            )
	        .forEach(user::addUserReward);
	}

//	public void calculateRewardsForAllUsers(List<User> allUsers) {
//        allUsers.parallelStream().forEach(this::calculateRewards);
//    }
//
//    public void calculateRewards(User user) {
//        List<VisitedLocation> userLocations = new ArrayList<>(user.getVisitedLocations());
//        List<Attraction> attractions = new ArrayList<>(gpsUtil.getAttractions());
//
//        Set<String> rewardedAttractions = user.getUserRewards().stream()
//            .map(r -> r.attraction.attractionName)
//            .collect(Collectors.toSet());
//
//        List<CompletableFuture<Void>> futures = attractions.stream()
//            .filter(attraction -> !rewardedAttractions.contains(attraction.attractionName))
//            .map(attraction -> CompletableFuture.runAsync(() -> {
//                userLocations.parallelStream()
//                    .filter(visitedLocation -> nearAttraction(visitedLocation, attraction))
//                    .map(visitedLocation -> new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)))
//                    .forEach(user::addUserReward);
//            }, executor))
//            .collect(Collectors.toList());
//
//        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//    }
	
//	public CompletableFuture<Void> calculateRewardsForAllUsers(List<User> allUsers) {
//		List<CompletableFuture<Void>> futures = allUsers.stream()
//		        .map(user -> CompletableFuture.runAsync(() -> calculateRewards(user).join(), executor))
//		        .collect(Collectors.toList());
//
//		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
//	}
//	
//	public CompletableFuture<Void> calculateRewards(User user) {
//	    List<VisitedLocation> userLocations = new ArrayList<>(user.getVisitedLocations());
//	    List<Attraction> attractions = new ArrayList<>(gpsUtil.getAttractions());
//
//	    Set<String> rewardedAttractions = user.getUserRewards().stream()
//	            .map(r -> r.attraction.attractionName)
//	            .collect(Collectors.toSet());
//
//	    List<CompletableFuture<Void>> futures = attractions.stream()
//	        .filter(attraction -> !rewardedAttractions.contains(attraction.attractionName))
//	        .map(attraction -> CompletableFuture.runAsync(() -> {
//	            userLocations.stream()
//	                .filter(visitedLocation -> nearAttraction(visitedLocation, attraction))
//	                .map(visitedLocation -> new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)))
//	                .forEach(user::addUserReward);
//	        }, executor))
//	        .collect(Collectors.toList());
//
//	    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
//	}
	
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}
	
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}
	
	private int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}
	
	public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                               + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
        return statuteMiles;
	}

}
