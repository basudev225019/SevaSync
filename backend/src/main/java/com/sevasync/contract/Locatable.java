package com.sevasync.contract;

import com.sevasync.model.GeoPoint;

/**
 * Anything that has a place on the campus map: donors, recipients and volunteers (but not administrators).
 * The matching engine, the volunteer pickup list and the 3D network view only ever talk to this interface.
 */
public interface Locatable {

    GeoPoint getLocation();

    /** Great-circle distance in kilometres to another located party. */
    default double distanceKmTo(Locatable other) {
        return getLocation().distanceKm(other.getLocation());
    }
}
