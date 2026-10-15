package com.sevasync;

import com.sevasync.exception.SevaSyncException;
import com.sevasync.model.AccountStatus;
import com.sevasync.model.BatchStatus;
import com.sevasync.model.Dispatch;
import com.sevasync.model.DispatchStatus;
import com.sevasync.model.Donor;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.GeoPoint;
import com.sevasync.model.Recipient;
import com.sevasync.model.Role;
import com.sevasync.model.User;
import com.sevasync.model.Volunteer;
import com.sevasync.security.RegistrationRequest;
import com.sevasync.service.DonationService;
import com.sevasync.storage.AuditLogger;
import com.sevasync.util.TimeUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Realistic sample campus for demos and evaluation: one coordinator, four donors, five recipient organisations,
 * four volunteers, two weeks of delivery history and a few live pickups in every state.
 *
 * <p>All demo accounts share {@link #PASSWORD}; this is demo data, never use it for a real deployment.
 */
public final class DemoData {
    public static final String PASSWORD = "Demo@2026";
    public static final String ADMIN_EMAIL = "admin@sevasync.demo";

    private DemoData() {
    }

    /** @return false when the system already has participants (nothing is added then). */
    public static boolean load(SevaSyncSystem sys) throws SevaSyncException {
        if (sys.userRepo().count() > 0) {
            return false;
        }
        GeoPoint c = GeoPoint.CAMPUS;
        User admin = account(sys, Role.ADMIN, "Campus Coordinator", ADMIN_EMAIL, "9000000001", "Admin Block", null,
                null, null, null, null, null);

        Donor mess = (Donor) account(sys, Role.DONOR, "Boys Hostel Mess", "mess@sevasync.demo", "9876543210",
                "North Block", c.offsetKm(0.45, -0.20), "HOSTEL_MESS", null, null, null, null);
        Donor canteen = (Donor) account(sys, Role.DONOR, "Central Canteen", "canteen@sevasync.demo", "9876543211",
                "Admin Block", c.offsetKm(-0.10, 0.30), "CANTEEN", null, null, null, null);
        Donor fest = (Donor) account(sys, Role.DONOR, "TechFest Committee", "techfest@sevasync.demo", "9876543212",
                "Auditorium", c.offsetKm(-0.40, -0.35), "EVENT_COORDINATOR", null, null, null, null);
        Donor cafe = (Donor) account(sys, Role.DONOR, "Library Cafe", "cafe@sevasync.demo", "9876543213",
                "Central Library", c.offsetKm(0.15, 0.55), "CAFE", null, null, null, null);
        account(sys, Role.DONOR, "Girls Hostel Mess", "girls-mess@sevasync.demo", "9876543214", "South Block",
                c.offsetKm(-0.65, 0.10), "HOSTEL_MESS", null, null, null, AccountStatus.PENDING);

        Recipient asha = (Recipient) account(sys, Role.RECIPIENT, "Asha Bhavan Orphanage", "asha@sevasync.demo",
                "9812345670", "Church Road", c.offsetKm(2.1, 1.4), null, "ORPHANAGE", "CRITICAL", 60, null);
        Recipient sneha = (Recipient) account(sys, Role.RECIPIENT, "Sneha Old Age Home", "sneha@sevasync.demo",
                "9812345671", "Market Street", c.offsetKm(-1.6, 2.3), null, "OLD_AGE_HOME", "HIGH", 50, null);
        Recipient shelter = (Recipient) account(sys, Role.RECIPIENT, "Railway Night Shelter",
                "shelter@sevasync.demo", "9812345672", "Station Road", c.offsetKm(3.2, -1.9), null, "SHELTER", "HIGH",
                80, null);
        Recipient kitchen = (Recipient) account(sys, Role.RECIPIENT, "Community Kitchen Trust",
                "kitchen@sevasync.demo", "9812345673", "Temple Lane", c.offsetKm(-2.7, -2.2), null,
                "COMMUNITY_KITCHEN", "NORMAL", 120, null);
        Recipient ngo = (Recipient) account(sys, Role.RECIPIENT, "Annapurna Food Bank", "ngo@sevasync.demo",
                "9812345674", "Ring Road", c.offsetKm(1.2, -3.4), null, "NGO", "NORMAL", 90, null);

        Volunteer arjun = (Volunteer) account(sys, Role.VOLUNTEER, "Arjun Mehta", "arjun@sevasync.demo",
                "9900000001", "Hostel B", c.offsetKm(0.30, 0.05), null, null, null, null, null, "BICYCLE");
        Volunteer priya = (Volunteer) account(sys, Role.VOLUNTEER, "Priya Nair", "priya@sevasync.demo", "9900000002",
                "Hostel G", c.offsetKm(-0.55, 0.25), null, null, null, null, null, "SCOOTER");
        Volunteer rahul = (Volunteer) account(sys, Role.VOLUNTEER, "Rahul Verma", "rahul@sevasync.demo",
                "9900000003", "Hostel C", c.offsetKm(0.05, -0.45), null, null, null, null, null, "WALK");
        Volunteer meera = (Volunteer) account(sys, Role.VOLUNTEER, "Meera Iyer", "meera@sevasync.demo", "9900000004",
                "Staff Quarters", c.offsetKm(0.80, 0.60), null, null, null, null, null, "CAR");

        seedHistory(sys, List.of(mess, canteen, fest, cafe), List.of(asha, sneha, shelter, kitchen, ngo),
                List.of(arjun, priya, rahul, meera));
        sys.audit().log(admin.getId(), "DEMO_HISTORY_SEEDED", "System", "demo",
                "14 days of sample history inserted for the demo");

        // today: live food through the real services, so matching, audit and events all happen for real
        LocalDateTime now = TimeUtil.now(sys.clock());
        DonationService d = sys.donations();
        d.logBatch(mess, new DonationService.NewBatch("Vegetable Biryani", FoodCategory.COOKED_MEAL, true, 45,
                now.minusMinutes(50), 210, "Packed in steel containers"));
        d.logBatch(canteen, new DonationService.NewBatch("Chapati and Dal", FoodCategory.COOKED_MEAL, true, 30,
                now.minusMinutes(20), 240, ""));
        d.logBatch(fest, new DonationService.NewBatch("Paneer Wraps", FoodCategory.COOKED_MEAL, true, 40,
                now.minusMinutes(70), 200, "Left over from the hackathon dinner"));
        d.logBatch(cafe, new DonationService.NewBatch("Croissants and Muffins", FoodCategory.BAKERY, true, 25,
                now.minusMinutes(90), 600, ""));
        d.logBatch(fest, new DonationService.NewBatch("Fruit Boxes", FoodCategory.FRUITS_VEGETABLES, true, 35,
                now.minusMinutes(30), 900, ""));
        d.logBatch(mess, new DonationService.NewBatch("Lemon Rice", FoodCategory.COOKED_MEAL, true, 20,
                now.minusMinutes(15), 180, ""));

        // move a few of today's pickups along so every state is visible
        List<Dispatch> open = sys.dispatchRepo().findByStatus(DispatchStatus.ASSIGNED);
        List<Volunteer> crew = List.of(meera, priya, arjun);
        int i = 0;
        for (Dispatch dispatch : open) {
            if (i >= crew.size()) {
                break;
            }
            Volunteer v = crew.get(i);
            if (!v.canCarry(dispatch.getServings())) {
                continue;
            }
            sys.dispatches().claim(v, dispatch.getId());
            if (i == 0) {
                sys.dispatches().pickUp(v, dispatch.getId());
                sys.dispatches().deliver(v, dispatch.getId());
                Recipient r = (Recipient) sys.userRepo().findById(dispatch.getRecipientId()).orElseThrow();
                sys.dispatches().confirmReceipt(r, dispatch.getId(), 5, "Still warm - thank you!");
            } else if (i == 1) {
                sys.dispatches().pickUp(v, dispatch.getId());
            }
            i++;
        }
        return true;
    }

    /** Past days are inserted straight into the repositories (they describe what already happened). */
    private static void seedHistory(SevaSyncSystem sys, List<Donor> donors, List<Recipient> recipients,
                                    List<Volunteer> volunteers) {
        Random rnd = new Random(2026);
        LocalDateTime today = TimeUtil.now(sys.clock()).toLocalDate().atStartOfDay();
        String[][] menu = {
                {"Veg Pulao", "COOKED_MEAL"}, {"Rajma Chawal", "COOKED_MEAL"}, {"Idli Sambar", "COOKED_MEAL"},
                {"Sandwiches", "BAKERY"}, {"Banana Bread", "BAKERY"}, {"Seasonal Fruit", "FRUITS_VEGETABLES"},
                {"Buttermilk", "BEVERAGE"}, {"Poha", "COOKED_MEAL"}, {"Biscuit Packs", "PACKAGED"}};
        List<FoodBatch> batches = new ArrayList<>();
        List<Dispatch> dispatches = new ArrayList<>();
        for (int day = 13; day >= 1; day--) {
            LocalDateTime base = today.minusDays(day);
            int count = 3 + rnd.nextInt(4);
            for (int k = 0; k < count; k++) {
                Donor donor = donors.get(rnd.nextInt(donors.size()));
                String[] item = menu[rnd.nextInt(menu.length)];
                FoodCategory cat = FoodCategory.valueOf(item[1]);
                int servings = 20 + rnd.nextInt(9) * 5;
                LocalDateTime created = base.plusHours(9 + rnd.nextInt(12)).plusMinutes(rnd.nextInt(60));
                int shelf = Math.min(cat.getMaxShelfLifeMinutes(), 150 + rnd.nextInt(120));
                boolean wasted = rnd.nextInt(10) == 0;
                String id = sys.batchRepo().nextId();
                FoodBatch b = new FoodBatch(id, donor.getId(), item[0], cat, true, servings, wasted ? servings : 0,
                        created.minusMinutes(20), shelf, wasted ? BatchStatus.EXPIRED : BatchStatus.COMPLETED, "",
                        created);
                batches.add(b);
                if (wasted) {
                    continue;
                }
                Recipient r = recipients.get(rnd.nextInt(recipients.size()));
                Volunteer v = volunteers.get(rnd.nextInt(volunteers.size()));
                LocalDateTime claimed = created.plusMinutes(5 + rnd.nextInt(20));
                LocalDateTime picked = claimed.plusMinutes(5 + rnd.nextInt(10));
                LocalDateTime delivered = picked.plusMinutes(10 + rnd.nextInt(25));
                int rating = 3 + rnd.nextInt(3);
                dispatches.add(new Dispatch(sys.dispatchRepo().nextId(), id, r.getId(), servings,
                        DispatchStatus.DELIVERED, rnd.nextBoolean() ? "smart" : "expiry-first",
                        donor.distanceKmTo(r), created.plusMinutes(2), v.getId(), claimed, picked, delivered,
                        delivered.plusMinutes(30), rating, "", ""));
            }
        }
        sys.batchRepo().saveAll(batches);
        sys.dispatchRepo().saveAll(dispatches);
    }

    private static User account(SevaSyncSystem sys, Role role, String name, String email, String phone,
                                String address, GeoPoint at, String donorType, String recipientType, String need,
                                Integer capacity, AccountStatus status) throws SevaSyncException {
        return account(sys, role, name, email, phone, address, at, donorType, recipientType, need, capacity, status,
                null);
    }

    private static User account(SevaSyncSystem sys, Role role, String name, String email, String phone,
                                String address, GeoPoint at, String donorType, String recipientType, String need,
                                Integer capacity, AccountStatus status, String vehicle) throws SevaSyncException {
        RegistrationRequest req = new RegistrationRequest(role.name(), name, email, PASSWORD, phone, address,
                at == null ? null : at.lat(), at == null ? null : at.lng(), donorType, recipientType, need, capacity,
                vehicle);
        return sys.auth().createAccount(role, status == null ? AccountStatus.ACTIVE : status, req, AuditLogger.SYSTEM);
    }
}
