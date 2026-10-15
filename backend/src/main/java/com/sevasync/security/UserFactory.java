package com.sevasync.security;

import com.sevasync.exception.InvalidInputException;
import com.sevasync.model.AccountStatus;
import com.sevasync.model.Admin;
import com.sevasync.model.Donor;
import com.sevasync.model.DonorType;
import com.sevasync.model.GeoPoint;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.Recipient;
import com.sevasync.model.RecipientType;
import com.sevasync.model.Role;
import com.sevasync.model.User;
import com.sevasync.model.VehicleType;
import com.sevasync.model.Volunteer;
import com.sevasync.util.InputSanitizer;

import java.time.LocalDateTime;

/**
 * Factory pattern: turns a validated sign-up form into the right {@link User} subclass. All input checking for
 * accounts happens here, once.
 */
public final class UserFactory {
    private UserFactory() {
    }

    public static Role parseRole(String raw) throws InvalidInputException {
        return InputSanitizer.enumValue(Role.class, raw, "role");
    }

    public static User create(String id, Role role, AccountStatus status, RegistrationRequest r, LocalDateTime now)
            throws InvalidInputException {
        String name = InputSanitizer.text(r.name(), "Name", 80, true);
        String email = InputSanitizer.email(r.email(), "E-mail");
        String phone = InputSanitizer.phone(r.phone(), "Phone");
        String address = InputSanitizer.text(r.address(), "Address", 160, false);
        GeoPoint location = location(r.lat(), r.lng());
        return switch (role) {
            case ADMIN -> new Admin(id, name, email, phone, status, now);
            case DONOR -> new Donor(id, name, email, phone, address, location,
                    InputSanitizer.enumValue(DonorType.class, r.donorType(), "donorType"), status, now);
            case RECIPIENT -> {
                if (r.dailyCapacity() == null) {
                    throw new InvalidInputException("dailyCapacity is required for recipients");
                }
                int capacity = InputSanitizer.range(r.dailyCapacity(), "Daily capacity", 1,
                        Recipient.MAX_DAILY_CAPACITY);
                yield new Recipient(id, name, email, phone, address, location,
                        InputSanitizer.enumValue(RecipientType.class, r.recipientType(), "recipientType"),
                        InputSanitizer.enumValue(NeedLevel.class, r.needLevel(), "needLevel"), capacity, status,
                        now);
            }
            case VOLUNTEER -> new Volunteer(id, name, email, phone, address, location,
                    InputSanitizer.enumValue(VehicleType.class, r.vehicle(), "vehicle"), status, now);
        };
    }

    public static GeoPoint location(Double lat, Double lng) throws InvalidInputException {
        if (lat == null && lng == null) {
            return null;
        }
        if (lat == null || lng == null) {
            throw new InvalidInputException("Both lat and lng are required for a location");
        }
        InputSanitizer.range(lat, "Latitude", -90, 90);
        InputSanitizer.range(lng, "Longitude", -180, 180);
        return new GeoPoint(lat, lng);
    }
}
