package com.sevasync;

import com.sevasync.exception.SevaSyncException;
import com.sevasync.model.Dispatch;
import com.sevasync.model.DispatchStatus;
import com.sevasync.model.Donor;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.Recipient;
import com.sevasync.model.Role;
import com.sevasync.model.User;
import com.sevasync.model.Volunteer;
import com.sevasync.service.DonationService;
import com.sevasync.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Exhibition helper ({@code --live-demo}): every few seconds one simulated participant does one realistic thing
 * through the real services - a donor logs food, a volunteer claims, picks up or delivers, a recipient confirms.
 * The 3D network, live feed and audit trail therefore keep moving while visitors watch.
 */
public final class DemoPulse implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(DemoPulse.class);
    private static final String[][] MENU = {
            {"Veg Pulao", "COOKED_MEAL", "200"}, {"Masala Dosa", "COOKED_MEAL", "180"},
            {"Paneer Rolls", "COOKED_MEAL", "210"}, {"Banana Bread", "BAKERY", "540"},
            {"Fresh Fruit Cups", "FRUITS_VEGETABLES", "720"}, {"Buttermilk", "BEVERAGE", "300"},
            {"Curd Rice", "COOKED_MEAL", "200"}, {"Veg Sandwiches", "BAKERY", "360"}};

    private final SevaSyncSystem sys;
    private final Random rnd = new Random();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sevasync-demo-pulse");
        t.setDaemon(true);
        return t;
    });

    public DemoPulse(SevaSyncSystem sys) {
        this.sys = sys;
    }

    public void start(long periodSeconds) {
        executor.scheduleWithFixedDelay(this::beat, periodSeconds, periodSeconds, TimeUnit.SECONDS);
    }

    void beat() {
        try {
            if (advanceOne()) {
                return;
            }
            // nothing left to move: put new food on the board, but never flood it
            if (openFood() < MAX_OPEN_BATCHES) {
                if (shelterRoomLeft() < MIN_ROOM) {
                    topUpShelters();
                }
                logFood();
            }
        } catch (SevaSyncException | RuntimeException e) {
            LOG.debug("demo pulse skipped a beat: {}", e.getMessage());
        }
    }

    private static final int MAX_OPEN_BATCHES = 4;
    private static final int MIN_ROOM = 40;

    private long openFood() {
        return sys.batchRepo().findOpen().stream().filter(b -> b.getRemainingServings() > 0).count();
    }

    private int shelterRoomLeft() {
        var used = sys.dispatchRepo().servingsByRecipientSince(TimeUtil.now(sys.clock()).toLocalDate().atStartOfDay());
        int room = 0;
        for (User u : sys.userRepo().findByRoleAndStatus(Role.RECIPIENT, com.sevasync.model.AccountStatus.ACTIVE)) {
            Recipient r = (Recipient) u;
            room += Math.max(0, r.getDailyCapacity() - used.getOrDefault(r.getId(), 0));
        }
        return room;
    }

    /**
     * A long exhibition would otherwise use up every shelter's capacity for the day and the map would go still.
     * In live-demo mode only, the simulated coordinator raises each shelter's daily capacity - through the normal
     * service, so the change is permission-checked and written to the audit trail like any other edit.
     */
    private void topUpShelters() throws SevaSyncException {
        User admin = sys.userRepo().findByRole(Role.ADMIN).stream().findFirst().orElse(null);
        if (admin == null) {
            return;
        }
        for (User u : sys.userRepo().findByRoleAndStatus(Role.RECIPIENT, com.sevasync.model.AccountStatus.ACTIVE)) {
            Recipient r = (Recipient) u;
            int raised = Math.min(Recipient.MAX_DAILY_CAPACITY, r.getDailyCapacity() + 60);
            sys.users().updateByAdmin(admin, r.getId(), new com.sevasync.service.UserService.ProfileUpdate(null, null,
                    null, null, null, null, null, raised, null));
        }
        LOG.info("Live demo: shelter capacity topped up so the exhibition keeps flowing");
    }

    private void logFood() throws SevaSyncException {
        List<User> donors = sys.userRepo().findByRole(Role.DONOR).stream().filter(u -> u.can(
                com.sevasync.model.Permission.LOG_BATCH)).toList();
        if (donors.isEmpty()) {
            return;
        }
        Donor donor = (Donor) donors.get(rnd.nextInt(donors.size()));
        String[] item = MENU[rnd.nextInt(MENU.length)];
        LocalDateTime now = TimeUtil.now(sys.clock());
        sys.donations().logBatch(donor, new DonationService.NewBatch(item[0], FoodCategory.valueOf(item[1]), true,
                15 + rnd.nextInt(8) * 5, now.minusMinutes(rnd.nextInt(20)), Integer.parseInt(item[2]), ""));
    }

    /** Moves the oldest piece of work one step forward. @return false when nothing could move */
    private boolean advanceOne() throws SevaSyncException {
        List<Dispatch> active = sys.dispatchRepo().findActive();
        // finish what is already moving before starting something new
        for (DispatchStatus stage : new DispatchStatus[]{DispatchStatus.PICKED_UP, DispatchStatus.CLAIMED}) {
            for (Dispatch d : active) {
                if (d.getStatus() == stage && rnd.nextBoolean()) {
                    Volunteer v = (Volunteer) sys.userRepo().findById(d.getVolunteerId()).orElseThrow();
                    if (stage == DispatchStatus.PICKED_UP) {
                        sys.dispatches().deliver(v, d.getId());
                        Recipient r = (Recipient) sys.userRepo().findById(d.getRecipientId()).orElseThrow();
                        if (rnd.nextInt(3) > 0) {
                            sys.dispatches().confirmReceipt(r, d.getId(), 4 + rnd.nextInt(2), "");
                        }
                    } else {
                        sys.dispatches().pickUp(v, d.getId());
                    }
                    return true;
                }
            }
        }
        for (Dispatch d : active) {
            if (d.getStatus() == DispatchStatus.ASSIGNED) {
                for (User u : sys.userRepo().findByRole(Role.VOLUNTEER)) {
                    Volunteer v = (Volunteer) u;
                    if (v.canCarry(d.getServings())
                            && sys.dispatchRepo().countActiveByVolunteer(v.getId()) < Volunteer.MAX_ACTIVE_DISPATCHES) {
                        sys.dispatches().claim(v, d.getId());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
