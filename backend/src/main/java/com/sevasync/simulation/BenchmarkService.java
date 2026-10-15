package com.sevasync.simulation;

import com.sevasync.exception.InvalidInputException;
import com.sevasync.matching.MatchingStrategy;
import com.sevasync.matching.StrategyRegistry;
import com.sevasync.util.InputSanitizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares every matching strategy on the same set of seeded synthetic days and reports averages: how much food
 * is delivered or wasted, travel distance, delivery speed, how much reaches urgent recipients, and fairness.
 * This is the evidence behind "the smart strategy wastes less food".
 */
public class BenchmarkService {
    public static final int MAX_RUNS = 200;

    private final StrategyRegistry strategies;

    public BenchmarkService(StrategyRegistry strategies) {
        this.strategies = strategies;
    }

    public Map<String, Object> run(int runs, long seed, Scenario.Config cfg) throws InvalidInputException {
        InputSanitizer.range(runs, "runs", 1, MAX_RUNS);
        InputSanitizer.range(cfg.donors(), "donors", 1, 50);
        InputSanitizer.range(cfg.recipients(), "recipients", 1, 50);
        InputSanitizer.range(cfg.volunteers(), "volunteers", 1, 50);
        InputSanitizer.range(cfg.batches(), "batches", 1, 500);

        List<MatchingStrategy> all = strategies.all();
        double[][] acc = new double[all.size()][11];
        long started = System.nanoTime();
        for (int r = 0; r < runs; r++) {
            Scenario scenario = Scenario.generate(seed + r, cfg);
            for (int i = 0; i < all.size(); i++) {
                Simulator.Outcome o = Simulator.run(scenario, all.get(i));
                acc[i][0] += o.offered();
                acc[i][1] += o.delivered();
                acc[i][2] += o.wasted();
                acc[i][3] += o.deliveries() == 0 ? 0 : o.distanceKm() / o.deliveries();
                acc[i][4] += o.deliveries() == 0 ? 0 : o.deliveryMinutes() / (double) o.deliveries();
                acc[i][5] += o.delivered() == 0 ? 0 : o.deliveredToUrgent() * 100.0 / o.delivered();
                acc[i][6] += o.fairness();
                acc[i][7] += o.offered() == 0 ? 0 : o.delivered() * 100.0 / o.offered();
                acc[i][8] += o.wastedUnmatched();
                acc[i][9] += o.wastedWaiting();
                acc[i][10] += o.wastedLate();
            }
        }

        List<Map<String, Object>> results = new ArrayList<>();
        String best = null;
        double bestWaste = Double.MAX_VALUE;
        for (int i = 0; i < all.size(); i++) {
            MatchingStrategy s = all.get(i);
            double offered = acc[i][0] / runs;
            double wasted = acc[i][2] / runs;
            double wastePct = offered == 0 ? 0 : wasted * 100.0 / offered;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("strategy", s.id());
            m.put("name", s.displayName());
            m.put("servingsOffered", round1(offered));
            m.put("servingsDelivered", round1(acc[i][1] / runs));
            m.put("servingsWasted", round1(wasted));
            m.put("deliveredPercent", round1(acc[i][7] / runs));
            m.put("wastedPercent", round1(wastePct));
            m.put("averageDistanceKm", round2(acc[i][3] / runs));
            m.put("averageDeliveryMinutes", round1(acc[i][4] / runs));
            m.put("urgentSharePercent", round1(acc[i][5] / runs));
            m.put("fairnessIndex", round2(acc[i][6] / runs));
            Map<String, Object> why = new LinkedHashMap<>();
            why.put("noRecipientRoom", round1(acc[i][8] / runs));
            why.put("waitingForVolunteer", round1(acc[i][9] / runs));
            why.put("unreachableInTime", round1(acc[i][10] / runs));
            m.put("wasteCauses", why);
            results.add(m);
            if (wastePct < bestWaste) {
                bestWaste = wastePct;
                best = s.id();
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runs", runs);
        out.put("seed", seed);
        Map<String, Object> scenario = new LinkedHashMap<>();
        scenario.put("donors", cfg.donors());
        scenario.put("recipients", cfg.recipients());
        scenario.put("volunteers", cfg.volunteers());
        scenario.put("batchesPerDay", cfg.batches());
        out.put("scenario", scenario);
        out.put("results", results);
        out.put("lowestWaste", best);
        out.put("elapsedMillis", (System.nanoTime() - started) / 1_000_000);
        return out;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
