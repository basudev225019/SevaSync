package com.sevasync.model;

import com.sevasync.contract.Locatable;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * Intermediate abstract class for every participant with a place on the map (donor, recipient, volunteer).
 * Shares the location handling so the three subclasses do not repeat it.
 */
public abstract class LocatedUser extends User implements Locatable {
    private GeoPoint location;

    protected LocatedUser(String id, String name, String email, String phone, String address, GeoPoint location,
                          AccountStatus status, LocalDateTime createdAt) {
        super(id, name, email, phone, address, status, createdAt);
        this.location = location == null ? GeoPoint.CAMPUS : location;
    }

    @Override
    public GeoPoint getLocation() {
        return location;
    }

    public void setLocation(GeoPoint location) {
        this.location = Objects.requireNonNull(location);
    }

    @Override
    protected void writeProfile(Map<String, Object> m) {
        m.put("location", location.toMap());
    }

    @Override
    public Map<String, Object> toPublicMap() {
        Map<String, Object> m = super.toPublicMap();
        m.put("location", location.toMap());
        return m;
    }
}
