package com.sevasync.cli;

import com.sevasync.SevaSyncSystem;
import com.sevasync.exception.SevaSyncException;
import com.sevasync.model.AccountStatus;
import com.sevasync.model.DispatchStatus;
import com.sevasync.model.FoodBatch;
import com.sevasync.model.Permission;
import com.sevasync.model.User;
import com.sevasync.security.AuthService;
import com.sevasync.service.MatchingService;
import com.sevasync.simulation.Scenario;
import com.sevasync.storage.AuditLogger;
import com.sevasync.util.TimeUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * Coordinator console: a text-mode state machine (SIGN_IN -> MENU -> action -> MENU ... -> EXIT) over the same
 * services the web app uses. Handy on a server without a browser, and a clear demonstration that the business
 * logic does not depend on the HTTP layer.
 */
public class ConsoleUI {
    private enum State { SIGN_IN, MENU, EXIT }

    private final BufferedReader in;
    private final PrintWriter out;
    private final SevaSyncSystem sys;
    private State state = State.SIGN_IN;
    private User admin;

    public ConsoleUI(BufferedReader in, PrintWriter out, SevaSyncSystem sys) {
        this.in = in;
        this.out = out;
        this.sys = sys;
    }

    public void run() throws IOException {
        out.println("SevaSync " + SevaSyncSystem.VERSION + " - coordinator console");
        while (state != State.EXIT) {
            try {
                state = switch (state) {
                    case SIGN_IN -> signIn();
                    case MENU -> menu();
                    case EXIT -> State.EXIT;
                };
            } catch (SevaSyncException e) {
                out.println("  ! " + e.getMessage());
            }
        }
        out.println("Bye.");
    }

    private State signIn() throws IOException, SevaSyncException {
        String email = ask("Coordinator e-mail (blank to quit)");
        if (email == null || email.isBlank()) {
            return State.EXIT;
        }
        String password = ask("Password");
        AuthService.AuthResult r = sys.auth().login(email, password == null ? "" : password, "console");
        if (!r.user().can(Permission.MANAGE_USERS)) {
            sys.auth().logout(r.token());
            out.println("  ! The console is for coordinators; please use the web app.");
            return State.SIGN_IN;
        }
        admin = r.user();
        out.println("Welcome, " + admin.getName() + ".");
        return State.MENU;
    }

    private State menu() throws IOException, SevaSyncException {
        out.println();
        out.println(" 1) Impact summary         5) Verify audit trail");
        out.println(" 2) Open food              6) Benchmark strategies");
        out.println(" 3) Pending verifications  7) Backup + CSV export");
        out.println(" 4) Preview / run matching 0) Sign out");
        String choice = ask("Choose");
        if (choice == null) {
            return State.EXIT;
        }
        switch (choice.trim()) {
            case "1" -> impact();
            case "2" -> openFood();
            case "3" -> pending();
            case "4" -> matching();
            case "5" -> verify();
            case "6" -> benchmark();
            case "7" -> backup();
            case "0" -> {
                admin = null;
                return State.SIGN_IN;
            }
            default -> out.println("  ? Unknown option");
        }
        return State.MENU;
    }

    private void impact() {
        Map<String, Object> s = sys.reports().publicStats();
        out.printf("  Meals served %s | food saved %s kg | CO2e avoided %s kg | delivery rate %s%%%n",
                s.get("mealsServed"), s.get("foodSavedKg"), s.get("co2AvoidedKg"), s.get("deliveryRatePercent"));
        out.printf("  Active donors %s, recipients %s, volunteers %s | dispatches in progress %d%n",
                s.get("donors"), s.get("recipients"), s.get("volunteers"), sys.dispatchRepo().findActive().size());
    }

    private void openFood() {
        List<FoodBatch> open = sys.batchRepo().findOpen();
        if (open.isEmpty()) {
            out.println("  No open food right now.");
        }
        var now = TimeUtil.now(sys.clock());
        for (FoodBatch b : open) {
            out.printf("  %-9s %-26s %3d/%-3d %-9s %4d min left%n", b.getId(), b.getFoodName(),
                    b.getRemainingServings(), b.getServings(), b.getStatus(), Math.max(0, b.minutesRemaining(now)));
        }
    }

    private void pending() throws IOException, SevaSyncException {
        List<User> list = sys.users().list(admin, null, AccountStatus.PENDING);
        if (list.isEmpty()) {
            out.println("  Nobody is waiting for verification.");
            return;
        }
        for (User u : list) {
            out.println("  " + u.describe() + " <" + u.getEmail() + ">");
            String a = ask("    Verify this account? (y/n/skip)");
            if ("y".equalsIgnoreCase(a)) {
                sys.users().setStatus(admin, u.getId(), AccountStatus.ACTIVE);
                out.println("    Verified.");
            } else if ("n".equalsIgnoreCase(a)) {
                sys.users().setStatus(admin, u.getId(), AccountStatus.SUSPENDED);
                out.println("    Rejected.");
            }
        }
    }

    private void matching() throws IOException, SevaSyncException {
        MatchingService.MatchResult p = sys.matching().preview(admin, null);
        out.printf("  Strategy %s would create %d dispatches (%d servings, %d still waiting).%n", p.strategy(),
                p.items().size(), p.allocatedServings(), p.unallocatedServings());
        if (!p.items().isEmpty() && "y".equalsIgnoreCase(ask("  Run it now? (y/n)"))) {
            MatchingService.MatchResult r = sys.matching().run(admin, null);
            out.printf("  Created %d dispatches. Volunteers can see them now.%n", r.items().size());
        }
        out.println("  Pickups waiting for a volunteer: " + sys.dispatchRepo().findByStatus(DispatchStatus.ASSIGNED)
                .size());
    }

    private void verify() {
        AuditLogger.Verification v = sys.audit().verify();
        out.println(v.valid() ? "  Audit chain intact (" + v.entries() + " entries)."
                : "  TAMPERING DETECTED at entry " + v.firstBadSeq() + ": " + v.reason());
    }

    private void benchmark() throws SevaSyncException {
        out.println("  Simulating 30 campus days per strategy...");
        Map<String, Object> r = sys.benchmark().run(30, 2026, Scenario.Config.defaults());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) r.get("results");
        out.printf("  %-14s %10s %10s %10s %10s%n", "strategy", "delivered%", "wasted%", "avg km", "fairness");
        for (Map<String, Object> row : rows) {
            out.printf("  %-14s %10s %10s %10s %10s%n", row.get("strategy"), row.get("deliveredPercent"),
                    row.get("wastedPercent"), row.get("averageDistanceKm"), row.get("fairnessIndex"));
        }
        out.println("  Lowest waste: " + r.get("lowestWaste"));
    }

    private void backup() {
        out.println("  Snapshot written: " + sys.backups().snapshot());
        out.println("  CSV export folder: " + sys.backups().exportCsv());
    }

    private String ask(String prompt) throws IOException {
        out.print(prompt + ": ");
        out.flush();
        return in.readLine();
    }
}
