package com.sevasync.service;

import com.sevasync.exception.EntityNotFoundException;
import com.sevasync.exception.InvalidInputException;
import com.sevasync.model.Dispatch;
import com.sevasync.model.DispatchStatus;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.Recipient;
import com.sevasync.model.RecipientType;
import com.sevasync.storage.AuditLogger;
import com.sevasync.storage.Repository;
import com.sevasync.util.InputSanitizer;
import com.sevasync.util.TimeUtil;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/** Recipient registry and daily-capacity bookkeeping. */
public class RecipientService {
    public static final int MAX_DAILY_CAPACITY = 5000;

    private final Repository<Recipient> recipients;
    private final Repository<Dispatch> dispatches;
    private final AuditLogger audit;
    private final Clock clock;
    private final ReentrantLock lock;

    public RecipientService(Repository<Recipient> recipients, Repository<Dispatch> dispatches,
                            AuditLogger audit, Clock clock, ReentrantLock lock) {
        this.recipients = recipients;
        this.dispatches = dispatches;
        this.audit = audit;
        this.clock = clock;
        this.lock = lock;
    }

    public Recipient register(String name, String phone, String email, String address, RecipientType type,
                              NeedLevel need, int dailyCapacity) throws InvalidInputException {
        String cleanName = InputSanitizer.text(name, "Name", 80, true);
        String cleanPhone = InputSanitizer.phone(phone, "Phone");
        String cleanEmail = InputSanitizer.email(email, "E-mail");
        String cleanAddress = InputSanitizer.text(address, "Address", 160, false);
        if (type == null || need == null) {
            throw new InvalidInputException("Recipient type and need level are required");
        }
        InputSanitizer.range(dailyCapacity, "Daily capacity", 1, MAX_DAILY_CAPACITY);
        lock.lock();
        try {
            Recipient r = new Recipient(recipients.nextId(), cleanName, cleanPhone, cleanEmail, cleanAddress, type,
                    need, dailyCapacity, TimeUtil.now(clock));
            recipients.save(r);
            audit.log("RECIPIENT_REGISTERED", "Recipient", r.getId(),
                    cleanName + " (" + type + ", need " + need + ", capacity " + dailyCapacity + ")");
            return r;
        } finally {
            lock.unlock();
        }
    }

    public List<Recipient> list() {
        return recipients.findAll();
    }

    public Recipient get(String id) throws EntityNotFoundException {
        return recipients.findById(id).orElseThrow(() -> new EntityNotFoundException("Recipient", id));
    }

    /** Servings already assigned or delivered to each recipient today. */
    public Map<String, Integer> usedTodayByRecipient() {
        LocalDate today = TimeUtil.now(clock).toLocalDate();
        Map<String, Integer> used = new HashMap<>();
        for (Dispatch d : dispatches.findAll()) {
            if (d.getStatus() != DispatchStatus.CANCELLED && d.getAssignedAt().toLocalDate().equals(today)) {
                used.merge(d.getRecipientId(), d.getServings(), Integer::sum);
            }
        }
        return used;
    }

    public int remainingCapacityToday(Recipient r) {
        return Math.max(0, r.getDailyCapacity() - usedTodayByRecipient().getOrDefault(r.getId(), 0));
    }
}
