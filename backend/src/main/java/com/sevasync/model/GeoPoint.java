package com.sevasync.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable latitude/longitude pair (value object).
 *
 * @param lat degrees north, -90..90
 * @param lng degrees east, -180..180
 */
public record GeoPoint(double lat, double lng) {
    private static final double EARTH_RADIUS_KM = 6371.0088;

    /** Default campus centre, used when a party registers without picking a map location. */
    public static final GeoPoint CAMPUS = new GeoPoint(12.9716, 77.5946);

    public GeoPoint {
        if (Double.isNaN(lat) || lat < -90 || lat > 90) {
            throw new IllegalArgumentException("latitude out of range: " + lat);
        }
        if (Double.isNaN(lng) || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("longitude out of range: " + lng);
        }
    }

    /** Haversine distance in kilometres. */
    public double distanceKm(GeoPoint o) {
        double dLat = Math.toRadians(o.lat - lat);
        double dLng = Math.toRadians(o.lng - lng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(o.lat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /** A point {@code northKm} / {@code eastKm} away from this one (good enough for campus-sized distances). */
    public GeoPoint offsetKm(double northKm, double eastKm) {
        double dLat = northKm / 111.32;
        double dLng = eastKm / (111.32 * Math.cos(Math.toRadians(lat)));
        return new GeoPoint(lat + dLat, lng + dLng);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lat", lat);
        m.put("lng", lng);
        return m;
    }
}
