package com.sevasync.matching;

import com.sevasync.exception.InvalidInputException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All available matching strategies, by id. New strategies are plugged in here and nowhere else. */
public final class StrategyRegistry {
    public static final String DEFAULT = "smart";

    private final Map<String, MatchingStrategy> strategies = new LinkedHashMap<>();

    public StrategyRegistry() {
        register(new SmartStrategy());
        register(new ExpiryFirstStrategy());
        register(new FifoStrategy());
    }

    public void register(MatchingStrategy s) {
        strategies.put(s.id(), s);
    }

    public MatchingStrategy get(String id) throws InvalidInputException {
        MatchingStrategy s = strategies.get(id);
        if (s == null) {
            throw new InvalidInputException("Unknown strategy '" + id + "'. Use one of " + strategies.keySet());
        }
        return s;
    }

    public List<MatchingStrategy> all() {
        return new ArrayList<>(strategies.values());
    }

    public List<Map<String, Object>> describe() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (MatchingStrategy s : strategies.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.id());
            m.put("name", s.displayName());
            m.put("description", s.description());
            out.add(m);
        }
        return out;
    }
}
