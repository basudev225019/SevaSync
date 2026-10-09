package com.sevasync;

import com.sevasync.api.ApiServer;
import com.sevasync.cli.ConsoleUI;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;

/**
 * Entry point.
 *
 * <pre>
 *   java -jar sevasync.jar                 interactive console (default)
 *   java -jar sevasync.jar serve           REST API on 127.0.0.1:8080
 *   options: --data DIR  --port N  --host H  --api-key KEY (or env SEVASYNC_API_KEY)  --demo
 * </pre>
 */
public final class SevaSyncApp {
    private SevaSyncApp() {
    }

    @SuppressWarnings("try")
    public static void main(String[] args) throws Exception {
        String mode = "cli";
        String dataDir = "data";
        String host = "127.0.0.1";
        int port = 8080;
        String apiKey = System.getenv("SEVASYNC_API_KEY");
        boolean demo = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "cli", "serve" -> mode = args[i];
                case "--data" -> dataDir = next(args, ++i);
                case "--port" -> port = Integer.parseInt(next(args, ++i));
                case "--host" -> host = next(args, ++i);
                case "--api-key" -> apiKey = next(args, ++i);
                case "--demo" -> demo = true;
                case "-h", "--help" -> {
                    System.out.println("Usage: java -jar sevasync.jar [cli|serve] [--data DIR] [--port N] "
                            + "[--host H] [--api-key KEY] [--demo]");
                    return;
                }
                default -> {
                    System.err.println("Unknown argument: " + args[i] + " (try --help)");
                    System.exit(2);
                }
            }
        }

        try (SevaSyncSystem sys = new SevaSyncSystem(Path.of(dataDir), Clock.systemDefaultZone())) {
            if (demo && DemoData.load(sys)) {
                System.out.println("Demo data loaded.");
            }
            sys.startExpiryMonitor(60_000);
            if (mode.equals("serve")) {
                ApiServer server = new ApiServer(sys, host, port, apiKey);
                server.start();
                System.out.println("SevaSync API listening on http://" + host + ":" + server.getPort()
                        + "/api  (data dir: " + sys.storage().getDataDir() + ")"
                        + (apiKey == null || apiKey.isBlank() ? "" : "  [API key required]"));
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    server.stop();
                    sys.close();
                }));
                Thread.currentThread().join();
            } else {
                BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
                new ConsoleUI(in, out, sys).run();
            }
        }
    }

    private static String next(String[] args, int i) {
        if (i >= args.length) {
            throw new IllegalArgumentException("Missing value for " + args[i - 1]);
        }
        return args[i];
    }
}
