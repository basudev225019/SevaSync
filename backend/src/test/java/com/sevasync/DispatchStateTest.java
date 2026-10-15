package com.sevasync;

import com.sevasync.exception.IllegalTransitionException;
import com.sevasync.model.Dispatch;
import com.sevasync.model.DispatchStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pure unit tests of the State pattern - no database involved. */
class DispatchStateTest {
    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 9, 12, 0);

    private static Dispatch fresh() {
        return Dispatch.assigned("DSP-1", "BAT-1", "REC-1", 20, "smart", 1.5, T);
    }

    @Test
    void happyPathWalksEveryState() throws Exception {
        Dispatch d = fresh();
        assertEquals(List.of("claim", "cancel"), d.state().allowedActions());
        d.claim("VOL-1", T.plusMinutes(1));
        assertEquals(DispatchStatus.CLAIMED, d.getStatus());
        d.pickUp("VOL-1", T.plusMinutes(10));
        assertEquals(DispatchStatus.PICKED_UP, d.getStatus());
        d.deliver("VOL-1", T.plusMinutes(25));
        assertEquals(DispatchStatus.DELIVERED, d.getStatus());
        d.confirm(5, "Thanks", T.plusMinutes(40));
        assertEquals(5, d.getRating());
        assertEquals(T.plusMinutes(25), d.getDeliveredAt());
    }

    @Test
    void stepsCannotBeSkipped() {
        Dispatch d = fresh();
        assertThrows(IllegalTransitionException.class, () -> d.pickUp("VOL-1", T));
        assertThrows(IllegalTransitionException.class, () -> d.deliver("VOL-1", T));
        assertThrows(IllegalTransitionException.class, () -> d.confirm(5, "", T));
        assertThrows(IllegalTransitionException.class, () -> d.release("VOL-1"));
    }

    @Test
    void onlyTheHolderCanMoveAClaimedDispatch() throws Exception {
        Dispatch d = fresh();
        d.claim("VOL-1", T);
        assertThrows(IllegalTransitionException.class, () -> d.claim("VOL-2", T));
        assertThrows(IllegalTransitionException.class, () -> d.pickUp("VOL-2", T));
        assertThrows(IllegalTransitionException.class, () -> d.release("VOL-2"));
        d.release("VOL-1");
        assertEquals(DispatchStatus.ASSIGNED, d.getStatus());
        assertNull(d.getVolunteerId());
    }

    @Test
    void deliveredAndCancelledAreFinal() throws Exception {
        Dispatch d = fresh();
        d.cancel("no longer needed");
        assertEquals(List.of(), d.state().allowedActions());
        assertThrows(IllegalTransitionException.class, () -> d.claim("VOL-1", T));
        assertThrows(IllegalTransitionException.class, () -> d.cancel("again"));

        Dispatch e = fresh();
        e.claim("VOL-1", T);
        e.pickUp("VOL-1", T);
        e.deliver("VOL-1", T);
        assertThrows(IllegalTransitionException.class, () -> e.cancel("too late"));
        e.confirm(4, "", T);
        assertThrows(IllegalTransitionException.class, () -> e.confirm(5, "", T));
    }

    @Test
    void ratingsMustBeOneToFive() throws Exception {
        Dispatch d = fresh();
        d.claim("VOL-1", T);
        d.pickUp("VOL-1", T);
        d.deliver("VOL-1", T);
        assertThrows(IllegalTransitionException.class, () -> d.confirm(0, "", T));
        assertThrows(IllegalTransitionException.class, () -> d.confirm(6, "", T));
    }
}
