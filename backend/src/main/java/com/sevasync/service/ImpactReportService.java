package com.sevasync.service;

import com.sevasync.model.BatchStatus;
import com.sevasync.model.Dispatch;
import com.sevasync.model.DispatchStatus;
import com.sevasync.model.Donor;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.Recipient;
import com.sevasync.storage.Repository;
import com.sevasync.storage.FileStorageManager;
import com.sevasync.util.CsvUtil;
import com.sevasync.util.TimeUtil;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Impact and accountability figures computed from the stored batches and dispatches. */
public class ImpactReportService {
    /** Rough planning assumptions - adjust to your campus. */
    public static final double KG_PER_SERVING = 0.35;
    public static final double CO2E_KG_PER_KG_FOOD = 2.5;

    public record RankEntry(String id, String name, int servings) {
    }

    public record ImpactReport(LocalDateTime generatedAt, int donors, int recipients, int batches,
                               int servingsDonated, int servingsDelivered, int servingsInTransit,
                               int servingsAvailable, int servingsWasted, double deliveryRatePercent,
                               double foodSavedKg, double co2AvoidedKg, int recipientsServed,
                               List<RankEntry> topDonors, List<RankEntry> topRecipients) {

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("generatedAt", generatedAt);
            m.put("donors", donors);
            m.put("recipients", recipients);
            m.put("batches", batches);
            m.put("servingsDonated", servingsDonated);
            m.put("servingsDelivered", servingsDelivered);
            m.put("servingsInTransit", servingsInTransit);
            m.put("servingsAvailable", servingsAvailable);
            m.put("servingsWasted", servingsWasted);
            m.put("deliveryRatePercent", deliveryRatePercent);
            m.put("foodSavedKg", foodSavedKg);
            m.put("co2AvoidedKg", co2AvoidedKg);
            m.put("recipientsServed", recipientsServed);
            m.put("topDonors", rank(topDonors));
            m.put("topRecipients", rank(topRecipients));
            return m;
        }

        private static List<Map<String, Object>> rank(List<RankEntry> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (RankEntry e : list) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", e.id());
                m.put("name", e.name());
                m.put("servings", e.servings());
                out.add(m);
            }
            return out;
        }

        public String format() {
            StringBuilder sb = new StringBuilder();
            sb.append("========== SevaSync Impact Report ==========\n");
            sb.append(String.format("Generated        : %s%n", generatedAt));
            sb.append(String.format("Donors / Recipients / Batches : %d / %d / %d%n", donors, recipients, batches));
            sb.append(String.format("Servings donated : %d%n", servingsDonated));
            sb.append(String.format("  delivered      : %d (%.1f%%)%n", servingsDelivered, deliveryRatePercent));
            sb.append(String.format("  in transit     : %d%n", servingsInTransit));
            sb.append(String.format("  still available: %d%n", servingsAvailable));
            sb.append(String.format("  wasted/expired : %d%n", servingsWasted));
            sb.append(String.format("Food saved       : %.1f kg%n", foodSavedKg));
            sb.append(String.format("CO2e avoided     : %.1f kg (estimate)%n", co2AvoidedKg));
            sb.append(String.format("Recipients served: %d%n", recipientsServed));
            sb.append("Top donors:\n");
            topDonors.forEach(e -> sb.append(String.format("  %-9s %-24s %d servings%n", e.id(), e.name(), e.servings())));
            sb.append("Top recipients:\n");
            topRecipients.forEach(e -> sb.append(String.format("  %-9s %-24s %d servings%n", e.id(), e.name(), e.servings())));
            return sb.toString();
        }
    }

    private final Repository<Donor> donors;
    private final Repository<Recipient> recipients;
    private final Repository<FoodBatch> batches;
    private final Repository<Dispatch> dispatches;
    private final FileStorageManager storage;
    private final Clock clock;

    public ImpactReportService(Repository<Donor> donors, Repository<Recipient> recipients,
                               Repository<FoodBatch> batches, Repository<Dispatch> dispatches,
                               FileStorageManager storage, Clock clock) {
        this.donors = donors;
        this.recipients = recipients;
        this.batches = batches;
        this.dispatches = dispatches;
        this.storage = storage;
        this.clock = clock;
    }

    public ImpactReport generate() {
        List<FoodBatch> allBatches = batches.findAll();
        List<Dispatch> allDispatches = dispatches.findAll();

        Map<String, Integer> deliveredByBatch = new HashMap<>();
        Map<String, Integer> deliveredByRecipient = new HashMap<>();
        int inTransit = 0;
        for (Dispatch d : allDispatches) {
            if (d.getStatus() == DispatchStatus.DELIVERED) {
                deliveredByBatch.merge(d.getBatchId(), d.getServings(), Integer::sum);
                deliveredByRecipient.merge(d.getRecipientId(), d.getServings(), Integer::sum);
            } else if (d.getStatus() == DispatchStatus.ASSIGNED) {
                inTransit += d.getServings();
            }
        }

        int donated = 0;
        int available = 0;
        int wasted = 0;
        Map<String, Integer> deliveredByDonor = new HashMap<>();
        for (FoodBatch b : allBatches) {
            donated += b.getServings();
            int delivered = deliveredByBatch.getOrDefault(b.getId(), 0);
            deliveredByDonor.merge(b.getDonorId(), delivered, Integer::sum);
            if (b.getStatus() == BatchStatus.AVAILABLE) {
                available += b.getRemainingServings();
            } else if (b.getStatus() == BatchStatus.EXPIRED) {
                wasted += b.getServings() - delivered;
            }
        }
        int delivered = deliveredByBatch.values().stream().mapToInt(Integer::intValue).sum();
        double rate = donated == 0 ? 0 : delivered * 100.0 / donated;
        double kg = delivered * KG_PER_SERVING;
        Set<String> served = new HashSet<>(deliveredByRecipient.keySet());

        Map<String, String> donorNames = new HashMap<>();
        donors.findAll().forEach(d -> donorNames.put(d.getId(), d.getName()));
        Map<String, String> recipientNames = new HashMap<>();
        recipients.findAll().forEach(r -> recipientNames.put(r.getId(), r.getName()));

        return new ImpactReport(TimeUtil.now(clock), donors.count(), recipients.count(), allBatches.size(),
                donated, delivered, inTransit, available, wasted, round1(rate), round1(kg),
                round1(kg * CO2E_KG_PER_KG_FOOD), served.size(),
                top(deliveredByDonor, donorNames, 5), top(deliveredByRecipient, recipientNames, 5));
    }

    /** Writes the report as a two-column CSV and returns the file path. */
    public Path exportCsv(Path target) {
        ImpactReport r = generate();
        List<String> lines = new ArrayList<>();
        lines.add("metric,value");
        for (Map.Entry<String, Object> e : r.toMap().entrySet()) {
            if (!(e.getValue() instanceof List)) {
                lines.add(CsvUtil.join(new String[]{e.getKey(), String.valueOf(e.getValue())}));
            }
        }
        r.topDonors().forEach(e -> lines.add(CsvUtil.join(new String[]{"topDonor:" + e.name(), String.valueOf(e.servings())})));
        r.topRecipients().forEach(e -> lines.add(CsvUtil.join(new String[]{"topRecipient:" + e.name(), String.valueOf(e.servings())})));
        try {
            if (storage != null) {
                storage.writeLinesAtomically(target.toAbsolutePath(), lines);
            } else {
                java.nio.file.Files.write(target.toAbsolutePath(), lines);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to export CSV: " + ex.getMessage(), ex);
        }
        return target.toAbsolutePath();
    }

    private static List<RankEntry> top(Map<String, Integer> totals, Map<String, String> names, int limit) {
        List<RankEntry> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : totals.entrySet()) {
            if (e.getValue() > 0) {
                out.add(new RankEntry(e.getKey(), names.getOrDefault(e.getKey(), e.getKey()), e.getValue()));
            }
        }
        out.sort(Comparator.comparingInt(RankEntry::servings).reversed().thenComparing(RankEntry::id));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
