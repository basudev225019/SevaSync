package com.sevasync;

import com.sevasync.api.ApiServer;
import com.sevasync.cli.ConsoleUI;
import com.sevasync.security.PasswordHasher;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Entry point.
 *
 * <pre>
 *   java -jar sevasync.jar [serve]   web app + REST API on http://127.0.0.1:8080 (default)
 *   java -jar sevasync.jar cli       coordinator console
 *
 *   --data DIR       data directory (default ./data)
 *   --host H         interface to bind (default 127.0.0.1; use 0.0.0.0 to expose on the network)
 *   --port N         HTTP port (default 8080)
 *   --static DIR     built frontend to serve (default: auto-detect ../frontend/dist)
 *   --cors LIST      comma-separated browser origins allowed to call the API (default: Vite dev server)
 *   --demo           seed demo accounts and two weeks of history on an empty database
 *   --live-demo      keep simulated participants busy (implies --demo)
 *
 *   env: SEVASYNC_SECRET (base64 server key), SEVASYNC_ADMIN_EMAIL, SEVASYNC_ADMIN_PASSWORD, SEVASYNC_CORS
 * </pre>
 */
public final class SevaSyncApp {
    private SevaSyncApp() {
    }

    public static void main(String[] args) throws Exception {
        String mode = "serve";
        Path dataDir = Path.of("data");
        String host = "127.0.0.1";
        int port = 8080;
        Path staticDir = null;
        String cors = System.getenv("SEVASYNC_CORS");
        boolean demo = false;
        boolean live = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "cli", "serve" -> mode = args[i];
                case "--data" -> dataDir = Path.of(next(args, ++i));
                case "--port" -> port = Integer.parseInt(next(args, ++i));
                case "--host" -> host = next(args, ++i);
                case "--static" -> staticDir = Path.of(next(args, ++i));
                case "--cors" -> cors = next(args, ++i);
                case "--demo" -> demo = true;
                case "--live-demo" -> {
                    demo = true;
                    live = true;
                }
                case "-h", "--help" -> {
                    System.out.println("Usage: java -jar sevasync.jar [serve|cli] [--data DIR] [--host H] [--port N]"
                            + " [--static DIR] [--cors ORIGINS] [--demo] [--live-demo]");
                    return;
                }
                default -> {
                    System.err.println("Unknown argument: " + args[i] + " (try --help)");
                    System.exit(2);
                }
            }
        }

        configureSocketDir(dataDir);
        SevaSyncSystem sys = new SevaSyncSystem(new SevaSyncSystem.Options(dataDir, Clock.systemDefaultZone(),
                PasswordHasher.DEFAULT_ITERATIONS, System.getenv("SEVASYNC_SECRET")));
        if (demo && DemoData.load(sys)) {
            System.out.println("Demo data loaded. Every demo account uses the password " + DemoData.PASSWORD
                    + " (coordinator: " + DemoData.ADMIN_EMAIL + ").");
        }
        bootstrapAdmin(sys);
        sys.startBackgroundJobs();

        if (mode.equals("cli")) {
            try (sys) {
                new ConsoleUI(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                        new PrintWriter(System.out, true, StandardCharsets.UTF_8), sys).run();
            }
            return;
        }

        Set<String> origins = new LinkedHashSet<>(Arrays.asList("http://localhost:5173", "http://127.0.0.1:5173",
                "http://localhost:4173", "http://127.0.0.1:4173"));
        if (cors != null && !cors.isBlank()) {
            origins.clear();
            for (String o : cors.split(",")) {
                if (!o.isBlank()) {
                    origins.add(o.trim());
                }
            }
        }
        Path frontend = staticDir != null ? staticDir : detectFrontend();
        ApiServer server = new ApiServer(sys, host, port, origins, frontend);
        server.start();
        DemoPulse pulse = live ? new DemoPulse(sys) : null;
        if (pulse != null) {
            pulse.start(6);
        }
        String base = "http://" + (host.equals("0.0.0.0") ? "localhost" : host) + ":" + server.getPort();
        System.out.println("SevaSync " + SevaSyncSystem.VERSION + " is running");
        System.out.println("  API      " + base + "/api  (data: " + sys.storage().getDataDir() + ")");
        System.out.println("  Web app  " + (server.servesFrontend() ? base : "not bundled - run `npm run dev` in frontend/"));
        if (live) {
            System.out.println("  Live demo: simulated participants are active");
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (pulse != null) {
                pulse.close();
            }
            server.stop();
            sys.close();
        }, "sevasync-shutdown"));
        Thread.currentThread().join();
    }

    /** Creates the first coordinator on a fresh, non-demo installation. */
    private static void bootstrapAdmin(SevaSyncSystem sys) throws Exception {
        String email = envOr("SEVASYNC_ADMIN_EMAIL", "admin@sevasync.local");
        String password = System.getenv("SEVASYNC_ADMIN_PASSWORD");
        boolean generated = password == null || password.isBlank();
        if (generated) {
            byte[] raw = new byte[9];
            new SecureRandom().nextBytes(raw);
            password = "Sv" + java.util.HexFormat.of().formatHex(raw) + "9";
        }
        if (sys.auth().ensureAdmin(email, password, "Coordinator")) {
            System.out.println("Created coordinator account " + email
                    + (generated ? " with one-time password " + password + " - change it after signing in." : "."));
        }
    }

    /**
     * On Windows the JDK (16+) builds every NIO selector on an AF_UNIX socket pair created in the temp folder.
     * Security software on some machines blocks AF_UNIX sockets inside {@code AppData\Local\Temp}, and then the
     * HTTP server cannot even start ("Unable to establish loopback connection"). The JDK reads the folder once,
     * so it is pointed at our own data directory before any socket exists, unless the user set it already.
     */
    private static void configureSocketDir(Path dataDir) throws java.io.IOException {
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        if (!windows || System.getProperty("jdk.net.unixdomain.tmpdir") != null) {
            return;
        }
        Path dir = dataDir.toAbsolutePath().normalize().resolve("sockets");
        Files.createDirectories(dir);
        System.setProperty("jdk.net.unixdomain.tmpdir", dir.toString());
    }

    private static Path detectFrontend() {
        for (String candidate : new String[]{"../frontend/dist", "frontend/dist", "dist"}) {
            Path p = Path.of(candidate);
            if (Files.isRegularFile(p.resolve("index.html"))) {
                return p;
            }
        }
        return null;
    }

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }

    private static String next(String[] args, int i) {
        if (i >= args.length) {
            throw new IllegalArgumentException("Missing value for " + args[i - 1]);
        }
        return args[i];
    }
}
