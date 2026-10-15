package com.sevasync.event;

import com.sevasync.model.Role;
import com.sevasync.model.User;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Who may see an event: a set of roles and/or a set of specific users. Administrators see everything, so the
 * live feed never leaks one participant's activity to an unrelated participant.
 */
public final class Audience {
    private final Set<Role> roles;
    private final Set<String> userIds;

    private Audience(Set<Role> roles, Set<String> userIds) {
        this.roles = roles;
        this.userIds = userIds;
    }

    public static Audience everyone() {
        return new Audience(EnumSet.allOf(Role.class), Set.of());
    }

    public static Audience admins() {
        return new Audience(EnumSet.of(Role.ADMIN), Set.of());
    }

    public static Audience roles(Role first, Role... more) {
        return new Audience(EnumSet.of(first, more), Set.of());
    }

    public static Audience users(String... ids) {
        Set<String> s = new HashSet<>();
        Arrays.stream(ids).filter(Objects::nonNull).forEach(s::add);
        return new Audience(EnumSet.noneOf(Role.class), s);
    }

    /** Union of two audiences. */
    public Audience and(Audience other) {
        Set<Role> r = EnumSet.noneOf(Role.class);
        r.addAll(roles);
        r.addAll(other.roles);
        Set<String> u = new HashSet<>(userIds);
        u.addAll(other.userIds);
        return new Audience(r, u);
    }

    public boolean includes(User user) {
        return user.getRole() == Role.ADMIN || roles.contains(user.getRole()) || userIds.contains(user.getId());
    }
}
