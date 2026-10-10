package com.sevasync;

import com.sevasync.exception.SevaSyncException;
import com.sevasync.model.DonorType;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.RecipientType;
import com.sevasync.util.TimeUtil;

import java.time.LocalDateTime;

/** Sample data for demos and evaluation. */
public final class DemoData {
    private DemoData() {
    }

    /** @return false when the system already contains donors (nothing is added then). */
    public static boolean load(SevaSyncSystem sys) throws SevaSyncException {
        if (!sys.donations().listDonors().isEmpty()) {
            return false;
        }
        var d = sys.donations();
        var r = sys.recipients();
        d.registerDonor("Boys Hostel Mess", "9876543210", "mess@campus.edu", "North Block, Campus", DonorType.HOSTEL_MESS);
        d.registerDonor("Central Canteen", "9876543211", "", "Admin Block, Campus", DonorType.CANTEEN);
        d.registerDonor("Tech Fest Committee", "9876543212", "", "Auditorium, Campus", DonorType.EVENT_COORDINATOR);

        r.register("Asha Bhavan Orphanage", "9812345670", "", "Church Road", RecipientType.ORPHANAGE, NeedLevel.CRITICAL, 60);
        r.register("Sneha Old Age Home", "9812345671", "", "Market Street", RecipientType.OLD_AGE_HOME, NeedLevel.HIGH, 50);
        r.register("Railway Night Shelter", "9812345672", "", "Station Road", RecipientType.SHELTER, NeedLevel.HIGH, 80);
        r.register("Community Kitchen Trust", "9812345673", "", "Temple Lane", RecipientType.COMMUNITY_KITCHEN, NeedLevel.NORMAL, 100);

        LocalDateTime now = TimeUtil.now(sys.clock());
        d.submitBatch("DON-0001", "Vegetable Biryani", FoodCategory.COOKED_MEAL, true, 80, now.minusMinutes(45), 180);
        d.submitBatch("DON-0001", "Chapati and Dal", FoodCategory.COOKED_MEAL, true, 60, now.minusMinutes(20), 240);
        d.submitBatch("DON-0002", "Veg Sandwiches", FoodCategory.BAKERY, true, 40, now.minusMinutes(60), 360);
        d.submitBatch("DON-0003", "Packed Fruit Boxes", FoodCategory.FRUITS_VEGETABLES, true, 50, now.minusMinutes(30), 600);
        d.submitBatch("DON-0003", "Fried Rice", FoodCategory.COOKED_MEAL, false, 70, now.minusMinutes(90), 150);
        return true;
    }
}
