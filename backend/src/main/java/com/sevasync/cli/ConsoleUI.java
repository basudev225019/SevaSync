package com.sevasync.cli;

import com.sevasync.DemoData;
import com.sevasync.SevaSyncSystem;
import com.sevasync.exception.InvalidInputException;
import com.sevasync.exception.SevaSyncException;
import com.sevasync.exception.StorageException;
import com.sevasync.model.BatchStatus;
import com.sevasync.model.Dispatch;
import com.sevasync.model.DispatchStatus;
import com.sevasync.model.Donor;
import com.sevasync.model.DonorType;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.FoodCategory;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.Recipient;
import com.sevasync.model.RecipientType;
import com.sevasync.service.MatchingService;
import com.sevasync.storage.AuditLogger;
import com.sevasync.storage.BackupService;
import com.sevasync.util.InputSanitizer;
import com.sevasync.util.TimeUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Interactive terminal menu implemented as a small state machine (each menu is a state). */
public class ConsoleUI {
    private enum State { MAIN, DONORS, RECIPIENTS, BATCHES, MATCHING, REPORTS, TOOLS, EXIT }

    /** Thrown when stdin is closed (Ctrl-D / end of piped input). */
    private static final class InputClosedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("dd MMM HH:mm");

    private final BufferedReader in;
    private final PrintWriter out;
    private final SevaSyncSystem sys;
    private State state = State.MAIN;

    public ConsoleUI(BufferedReader in, PrintWriter out, SevaSyncSystem sys) {
        this.in = in;
        this.out = out;
        this.sys = sys;
    }

    public void run() {
        out.println();
        out.println("  ============================================");
        out.println("   SevaSync - Campus Food Surplus Redistribution");
        out.println("   Don't just discard. Distribute.");
        out.println("  ============================================");
        try {
            while (state != State.EXIT) {
                try {
                    state = switch (state) {
                        case MAIN -> mainMenu();
                        case DONORS -> donorsMenu();
                        case RECIPIENTS -> recipientsMenu();
                        case BATCHES -> batchesMenu();
                        case MATCHING -> matchingMenu();
                        case REPORTS -> reportsMenu();
                        case TOOLS -> toolsMenu();
                        case EXIT -> State.EXIT;
                    };
                } catch (SevaSyncException e) {
                    out.println("  [!] " + e.getMessage());
                } catch (StorageException e) {
                    out.println("  [storage error] " + e.getMessage());
                }
            }
            out.println("Goodbye - thank you for reducing food waste.");
        } catch (InputClosedException e) {
            out.println();
            out.println("Input closed. Goodbye.");
        }
        out.flush();
    }

    // ------------------------------------------------------------------ menus

    private State mainMenu() {
        switch (choice("MAIN MENU", "Exit", "Donors", "Recipients", "Food batches", "Matching & dispatch",
                "Reports & audit trail", "Backup & demo data")) {
            case 1: return State.DONORS;
            case 2: return State.RECIPIENTS;
            case 3: return State.BATCHES;
            case 4: return State.MATCHING;
            case 5: return State.REPORTS;
            case 6: return State.TOOLS;
            case 0: return State.EXIT;
            default: out.println("  Please choose one of the numbers shown."); return State.MAIN;
        }
    }

    private State donorsMenu() throws SevaSyncException {
        switch (choice("DONORS", "Back", "Register donor", "List donors")) {
            case 1 -> {
                Donor d = sys.donations().registerDonor(ask("Name"), ask("Phone (10 digits)"),
                        ask("E-mail (optional)"), ask("Address"), askEnum(DonorType.class, "Type"));
                out.println("  Registered " + d.describe());
            }
            case 2 -> printDonors();
            case 0 -> { return State.MAIN; }
            default -> out.println("  Please choose one of the numbers shown.");
        }
        return State.DONORS;
    }

    private State recipientsMenu() throws SevaSyncException {
        switch (choice("RECIPIENTS", "Back", "Register recipient", "List recipients")) {
            case 1 -> {
                String name = ask("Name");
                String phone = ask("Phone (10 digits)");
                String email = ask("E-mail (optional)");
                String address = ask("Address");
                RecipientType type = askEnum(RecipientType.class, "Type");
                NeedLevel need = askEnum(NeedLevel.class, "Need level");
                int cap = InputSanitizer.integer(ask("Daily capacity (servings)"), "Capacity", 1, 5000);
                Recipient r = sys.recipients().register(name, phone, email, address, type, need, cap);
                out.println("  Registered " + r.describe());
            }
            case 2 -> printRecipients();
            case 0 -> { return State.MAIN; }
            default -> out.println("  Please choose one of the numbers shown.");
        }
        return State.RECIPIENTS;
    }

    private State batchesMenu() throws SevaSyncException {
        switch (choice("FOOD BATCHES", "Back", "Log surplus batch", "List available batches", "List all batches")) {
            case 1 -> {
                printDonors();
                String donorId = ask("Donor id (e.g. DON-0001)");
                String food = ask("Food name");
                FoodCategory cat = askEnum(FoodCategory.class, "Category");
                boolean veg = InputSanitizer.yesNo(ask("Vegetarian? (y/n)"), "Vegetarian");
                int servings = InputSanitizer.integer(ask("Servings"), "Servings", 1, 5000);
                String ago = ask("Prepared how many minutes ago? [0]");
                int agoMin = ago.isBlank() ? 0 : InputSanitizer.integer(ago, "Minutes", 0, 10_000);
                double hours;
                try {
                    hours = Double.parseDouble(ask("Safe to consume within how many hours? (max for "
                            + cat + " = " + cat.getMaxShelfLifeMinutes() / 60.0 + ")").trim());
                } catch (NumberFormatException e) {
                    throw new InvalidInputException("Hours must be a number");
                }
                LocalDateTime prepared = TimeUtil.now(sys.clock()).minusMinutes(agoMin);
                FoodBatch b = sys.donations().submitBatch(donorId.trim(), food, cat, veg, servings, prepared,
                        (int) Math.round(hours * 60));
                out.println("  Accepted " + b + ", expires " + b.getExpiresAt().format(SHORT));
            }
            case 2 -> printBatches(sys.donations().listBatches(BatchStatus.AVAILABLE));
            case 3 -> printBatches(sys.donations().listBatches(null));
            case 0 -> { return State.MAIN; }
            default -> out.println("  Please choose one of the numbers shown.");
        }
        return State.BATCHES;
    }

    private State matchingMenu() throws SevaSyncException {
        switch (choice("MATCHING & DISPATCH", "Back", "Preview matching (dry run)", "Run matching engine",
                "List active dispatches", "List all dispatches", "Mark dispatch delivered",
                "Cancel dispatch", "Assign manually", "Expire overdue batches now")) {
            case 1 -> printMatch(sys.matching().preview());
            case 2 -> printMatch(sys.matching().runMatching());
            case 3 -> printDispatches(sys.dispatches().list(DispatchStatus.ASSIGNED));
            case 4 -> printDispatches(sys.dispatches().list(null));
            case 5 -> {
                printDispatches(sys.dispatches().list(DispatchStatus.ASSIGNED));
                Dispatch d = sys.dispatches().markDelivered(ask("Dispatch id"), ask("Volunteer name"));
                out.println("  Delivered " + d);
            }
            case 6 -> {
                Dispatch d = sys.dispatches().cancel(ask("Dispatch id"), ask("Reason"));
                out.println("  Cancelled " + d);
            }
            case 7 -> {
                String batch = ask("Batch id");
                String recipient = ask("Recipient id");
                int n = InputSanitizer.integer(ask("Servings"), "Servings", 1, 5000);
                out.println("  Created " + sys.dispatches().assignManually(batch.trim(), recipient.trim(), n));
            }
            case 8 -> out.println("  " + sys.dispatches().expireOverdue() + " batch(es) expired.");
            case 0 -> { return State.MAIN; }
            default -> out.println("  Please choose one of the numbers shown.");
        }
        return State.MATCHING;
    }

    private State reportsMenu() throws SevaSyncException {
        switch (choice("REPORTS & AUDIT", "Back", "Impact report", "Export impact report to CSV",
                "Show recent audit entries", "Verify audit trail integrity")) {
            case 1 -> out.print(sys.reports().generate().format());
            case 2 -> {
                String f = ask("File name [impact_report.csv]");
                Path p = sys.reports().exportCsv(sys.storage().resolve(f.isBlank() ? "impact_report.csv" : f.trim()));
                out.println("  Written to " + p);
            }
            case 3 -> {
                for (AuditLogger.AuditEntry e : sys.audit().tail(20)) {
                    out.printf("  #%-4d %s  %-20s %-9s %s%n", e.seq(), e.timestamp().format(SHORT), e.action(),
                            e.entityId(), cut(e.details(), 60));
                }
            }
            case 4 -> {
                AuditLogger.Verification v = sys.audit().verify();
                out.println(v.valid() ? "  Audit trail intact (" + v.entries() + " entries)."
                        : "  AUDIT TRAIL TAMPERED - first bad entry #" + v.firstBadSeq());
            }
            case 0 -> { return State.MAIN; }
            default -> out.println("  Please choose one of the numbers shown.");
        }
        return State.REPORTS;
    }

    private State toolsMenu() throws SevaSyncException {
        switch (choice("BACKUP & DEMO DATA", "Back", "Load demo data", "Create backup (.ser)", "Restore backup")) {
            case 1 -> out.println(DemoData.load(sys) ? "  Demo data loaded." : "  Data already present - nothing added.");
            case 2 -> {
                String f = ask("Backup file [backup.ser]");
                BackupService.Snapshot s = sys.backups().backup(sys.storage().resolve(f.isBlank() ? "backup.ser" : f.trim()));
                out.println("  Backup written: " + s.summary());
            }
            case 3 -> {
                String f = ask("Backup file [backup.ser]");
                Path p = sys.storage().resolve(f.isBlank() ? "backup.ser" : f.trim());
                BackupService.Snapshot s = sys.backups().inspect(p);
                out.println("  Backup contains " + s.summary());
                if (InputSanitizer.yesNo(ask("Replace ALL current data with it? (y/n)"), "Answer")) {
                    sys.backups().restore(p);
                    out.println("  Restored.");
                }
            }
            case 0 -> { return State.MAIN; }
            default -> out.println("  Please choose one of the numbers shown.");
        }
        return State.TOOLS;
    }

    // ------------------------------------------------------------------ printing

    private void printDonors() {
        List<Donor> list = sys.donations().listDonors();
        if (list.isEmpty()) {
            out.println("  (no donors yet)");
        }
        for (Donor d : list) {
            out.printf("  %-9s %-26s %-11s %s%n", d.getId(), cut(d.getName(), 26), d.getPhone(), d.getType());
        }
    }

    private void printRecipients() {
        List<Recipient> list = sys.recipients().list();
        if (list.isEmpty()) {
            out.println("  (no recipients yet)");
        }
        for (Recipient r : list) {
            out.printf("  %-9s %-26s %-18s %-9s free today: %d/%d%n", r.getId(), cut(r.getName(), 26), r.getType(),
                    r.getNeedLevel(), sys.recipients().remainingCapacityToday(r), r.getDailyCapacity());
        }
    }

    private void printBatches(List<FoodBatch> list) {
        if (list.isEmpty()) {
            out.println("  (no batches)");
        }
        LocalDateTime now = TimeUtil.now(sys.clock());
        for (FoodBatch b : list) {
            out.printf("  %-9s %-22s %-17s %4d/%-4d expires %s (%s) %s%n", b.getId(), cut(b.getFoodName(), 22),
                    b.getCategory(), b.getRemainingServings(), b.getServings(), b.getExpiresAt().format(SHORT),
                    b.isExpiredAt(now) ? "expired" : b.minutesRemaining(now) + " min left", b.getStatus());
        }
    }

    private void printDispatches(List<Dispatch> list) {
        if (list.isEmpty()) {
            out.println("  (no dispatches)");
        }
        Map<String, String> names = new HashMap<>();
        sys.recipients().list().forEach(r -> names.put(r.getId(), r.getName()));
        for (Dispatch d : list) {
            out.printf("  %-9s %-9s -> %-26s %4d servings  %s%n", d.getId(), d.getBatchId(),
                    cut(names.getOrDefault(d.getRecipientId(), d.getRecipientId()), 26), d.getServings(), d.getStatus());
        }
    }

    private void printMatch(MatchingService.MatchResult r) {
        out.println(r.committed() ? "  Matching run saved." : "  Preview only - nothing saved.");
        out.printf("  Batches considered: %d, recipients with capacity: %d%n", r.batchesConsidered(), r.recipientsAvailable());
        printDispatches(r.dispatches());
        out.printf("  Allocated %d servings, %d left unallocated.%n", r.allocatedServings(), r.unallocatedServings());
    }

    // ------------------------------------------------------------------ input helpers

    private int choice(String title, String zeroLabel, String... options) {
        out.println();
        out.println("--- " + title + " ---");
        for (int i = 0; i < options.length; i++) {
            out.println("  " + (i + 1) + ". " + options[i]);
        }
        out.println("  0. " + zeroLabel);
        String raw = ask("Choose");
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String ask(String label) {
        out.print("  " + label + "> ");
        out.flush();
        try {
            String line = in.readLine();
            if (line == null) {
                throw new InputClosedException();
            }
            return line;
        } catch (IOException e) {
            throw new InputClosedException();
        }
    }

    private <E extends Enum<E>> E askEnum(Class<E> type, String label) throws InvalidInputException {
        E[] values = type.getEnumConstants();
        StringBuilder options = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            options.append(i == 0 ? "" : ", ").append(i + 1).append('=').append(values[i]);
        }
        String raw = ask(label + " [" + options + "]");
        try {
            int n = Integer.parseInt(raw.trim());
            if (n >= 1 && n <= values.length) {
                return values[n - 1];
            }
            throw new InvalidInputException(label + " must be between 1 and " + values.length);
        } catch (NumberFormatException e) {
            return InputSanitizer.enumValue(type, raw, label);
        }
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }
}
