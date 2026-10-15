package com.sevasync.matching;

import com.sevasync.model.GeoPoint;
import com.sevasync.model.VehicleType;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Everything a strategy may look at besides the batches and recipients themselves.
 *
 * @param now                 current time
 * @param donorLocations      donor id to pickup location
 * @param minDispatchMinutes  batches closer to expiry than this are not worth dispatching
 * @param referenceVehicle    vehicle assumed when estimating whether a trip fits before expiry
 */
public record MatchContext(LocalDateTime now, Map<String, GeoPoint> donorLocations, int minDispatchMinutes,
                           VehicleType referenceVehicle) {

    public double distanceKm(String donorId, GeoPoint to) {
        GeoPoint from = donorLocations.getOrDefault(donorId, GeoPoint.CAMPUS);
        return from.distanceKm(to);
    }
}
