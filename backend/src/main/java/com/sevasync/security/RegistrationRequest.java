package com.sevasync.security;

/**
 * Raw sign-up form. Everything is a string or nullable wrapper on purpose: {@link UserFactory} validates and
 * converts it, so no unchecked value ever reaches a domain object.
 */
public record RegistrationRequest(
        String role,
        String name,
        String email,
        String password,
        String phone,
        String address,
        Double lat,
        Double lng,
        String donorType,
        String recipientType,
        String needLevel,
        Integer dailyCapacity,
        String vehicle) {
}
