package com.sevasync;

import com.sevasync.exception.InvalidInputException;
import com.sevasync.matching.StrategyRegistry;
import com.sevasync.simulation.BenchmarkService;
import com.sevasync.simulation.Scenario;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkTest {
    private final BenchmarkService bench = new BenchmarkService(new StrategyRegistry());

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> byStrategy(Map<String, Object> report) {
        Map<String, Map<String, Object>> out = new java.util.HashMap<>();
        for (Map<String, Object> row : (List<Map<String, Object>>) report.get("results")) {
            out.put((String) row.get("strategy"), row);
        }
        return out;
    }

    private static double num(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).doubleValue();
    }

    @Test
    void sameSeedGivesTheSameNumbers() throws Exception {
        Map<String, Object> a = bench.run(5, 99, Scenario.Config.defaults());
        Map<String, Object> b = bench.run(5, 99, Scenario.Config.defaults());
        assertEquals(a.get("results"), b.get("results"));
    }

    @Test
    void everyServingIsAccountedFor() throws Exception {
        for (Map<String, Object> row : byStrategy(bench.run(10, 7, Scenario.Config.defaults())).values()) {
            double offered = num(row, "servingsOffered");
            assertEquals(offered, num(row, "servingsDelivered") + num(row, "servingsWasted"), 0.11,
                    row.get("strategy") + " lost track of food");
        }
    }

    @Test
    void smartWastesNoMoreThanTheBaselineAndTravelsLess() throws Exception {
        Map<String, Map<String, Object>> r = byStrategy(bench.run(40, 2026, Scenario.Config.defaults()));
        double smartWaste = num(r.get("smart"), "wastedPercent");
        assertTrue(smartWaste <= num(r.get("fifo"), "wastedPercent"), "smart should not waste more than FIFO");
        assertTrue(num(r.get("smart"), "averageDistanceKm") < num(r.get("expiry-first"), "averageDistanceKm"),
                "distance-aware matching should shorten trips");
    }

    @Test
    void scenarioSizeIsBounded() {
        assertThrows(InvalidInputException.class, () -> bench.run(0, 1, Scenario.Config.defaults()));
        assertThrows(InvalidInputException.class, () -> bench.run(5, 1, new Scenario.Config(1, 1, 1, 10_000)));
    }
}
