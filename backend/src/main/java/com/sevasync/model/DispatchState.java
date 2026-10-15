package com.sevasync.model;

import com.sevasync.exception.IllegalTransitionException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * State pattern for the dispatch life-cycle.
 *
 * <pre>
 *   ASSIGNED --claim--> CLAIMED --pickUp--> PICKED_UP --deliver--> DELIVERED --confirm--> (confirmed)
 *      ^                   |
 *      +-----release-------+          ASSIGNED / CLAIMED / PICKED_UP --cancel--> CANCELLED
 * </pre>
 *
 * Every state answers every event; an event a state does not support falls through to the default method and is
 * rejected with {@link IllegalTransitionException}. Adding a state means adding one class here, never editing
 * {@code if/else} chains in the services. The interface is {@code sealed}, so the compiler knows the full set.
 */
public sealed interface DispatchState {
    DispatchState ASSIGNED = new Assigned();
    DispatchState CLAIMED = new Claimed();
    DispatchState PICKED_UP = new PickedUp();
    DispatchState DELIVERED = new Delivered();
    DispatchState CANCELLED = new Cancelled();

    DispatchStatus status();

    /** Action names this state accepts; the frontend renders exactly these buttons. */
    List<String> allowedActions();

    default void claim(Dispatch d, String volunteerId, LocalDateTime now) throws IllegalTransitionException {
        throw reject(d, "claimed");
    }

    default void release(Dispatch d, String volunteerId) throws IllegalTransitionException {
        throw reject(d, "released");
    }

    default void pickUp(Dispatch d, String volunteerId, LocalDateTime now) throws IllegalTransitionException {
        throw reject(d, "picked up");
    }

    default void deliver(Dispatch d, String volunteerId, LocalDateTime now) throws IllegalTransitionException {
        throw reject(d, "delivered");
    }

    default void confirm(Dispatch d, int rating, String feedback, LocalDateTime now)
            throws IllegalTransitionException {
        throw reject(d, "confirmed");
    }

    default void cancel(Dispatch d, String reason) throws IllegalTransitionException {
        throw reject(d, "cancelled");
    }

    private IllegalTransitionException reject(Dispatch d, String verb) {
        return new IllegalTransitionException("Dispatch " + d.getId() + " is " + status() + " and cannot be " + verb);
    }

    /** Shared guard: only the volunteer holding the dispatch may move it forward. */
    private static void requireHolder(Dispatch d, String volunteerId) throws IllegalTransitionException {
        if (d.getVolunteerId() == null || !d.getVolunteerId().equals(volunteerId)) {
            throw new IllegalTransitionException("Dispatch " + d.getId() + " is held by another volunteer");
        }
    }

    // ------------------------------------------------------------------ concrete states

    final class Assigned implements DispatchState {
        private Assigned() {
        }

        @Override
        public DispatchStatus status() {
            return DispatchStatus.ASSIGNED;
        }

        @Override
        public List<String> allowedActions() {
            return List.of("claim", "cancel");
        }

        @Override
        public void claim(Dispatch d, String volunteerId, LocalDateTime now) {
            d.recordClaim(volunteerId, now);
            d.moveTo(DispatchStatus.CLAIMED);
        }

        @Override
        public void cancel(Dispatch d, String reason) {
            d.recordNote(reason);
            d.moveTo(DispatchStatus.CANCELLED);
        }
    }

    final class Claimed implements DispatchState {
        private Claimed() {
        }

        @Override
        public DispatchStatus status() {
            return DispatchStatus.CLAIMED;
        }

        @Override
        public List<String> allowedActions() {
            return List.of("pickup", "release", "cancel");
        }

        @Override
        public void release(Dispatch d, String volunteerId) throws IllegalTransitionException {
            requireHolder(d, volunteerId);
            d.recordClaim(null, null);
            d.moveTo(DispatchStatus.ASSIGNED);
        }

        @Override
        public void pickUp(Dispatch d, String volunteerId, LocalDateTime now) throws IllegalTransitionException {
            requireHolder(d, volunteerId);
            d.recordPickup(now);
            d.moveTo(DispatchStatus.PICKED_UP);
        }

        @Override
        public void cancel(Dispatch d, String reason) {
            d.recordNote(reason);
            d.moveTo(DispatchStatus.CANCELLED);
        }
    }

    final class PickedUp implements DispatchState {
        private PickedUp() {
        }

        @Override
        public DispatchStatus status() {
            return DispatchStatus.PICKED_UP;
        }

        @Override
        public List<String> allowedActions() {
            return List.of("deliver", "cancel");
        }

        @Override
        public void deliver(Dispatch d, String volunteerId, LocalDateTime now) throws IllegalTransitionException {
            requireHolder(d, volunteerId);
            d.recordDelivery(now);
            d.moveTo(DispatchStatus.DELIVERED);
        }

        @Override
        public void cancel(Dispatch d, String reason) {
            d.recordNote(reason);
            d.moveTo(DispatchStatus.CANCELLED);
        }
    }

    final class Delivered implements DispatchState {
        private Delivered() {
        }

        @Override
        public DispatchStatus status() {
            return DispatchStatus.DELIVERED;
        }

        @Override
        public List<String> allowedActions() {
            return List.of("confirm");
        }

        @Override
        public void confirm(Dispatch d, int rating, String feedback, LocalDateTime now)
                throws IllegalTransitionException {
            if (d.getConfirmedAt() != null) {
                throw new IllegalTransitionException("Dispatch " + d.getId() + " was already confirmed");
            }
            if (rating < 1 || rating > 5) {
                throw new IllegalTransitionException("Rating must be between 1 and 5");
            }
            d.recordConfirmation(rating, feedback, now);
        }
    }

    final class Cancelled implements DispatchState {
        private Cancelled() {
        }

        @Override
        public DispatchStatus status() {
            return DispatchStatus.CANCELLED;
        }

        @Override
        public List<String> allowedActions() {
            return List.of();
        }
    }
}
