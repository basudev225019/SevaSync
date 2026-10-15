package com.sevasync;

import com.sevasync.api.ApiServer;
import com.sevasync.model.Donor;
import com.sevasync.model.NeedLevel;
import com.sevasync.model.Recipient;
import com.sevasync.model.User;
import com.sevasync.util.Json;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The HTTP contract, exercised over a real socket. */
class ApiServerTest {
    private static final String ORIGIN = "http://localhost:5173";

    static {
        // HttpURLConnection silently drops "Origin" unless restricted headers are allowed
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    private record Res(int status, String body, Map<String, List<String>> headers) {
        @SuppressWarnings("unchecked")
        Map<String, Object> json() throws Exception {
            return Json.parseObject(body);
        }

        List<Object> list() throws Exception {
            @SuppressWarnings("unchecked")
            List<Object> l = (List<Object>) Json.parse(body);
            return l;
        }

        String header(String name) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                    return e.getValue().get(0);
                }
            }
            return null;
        }
    }

    private Fixture f;
    private ApiServer server;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        f = new Fixture();
        server = new ApiServer(f.sys, "127.0.0.1", 0, Set.of(ORIGIN), null);
        server.start();
        base = "http://127.0.0.1:" + server.getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        f.close();
    }

    private Res call(String method, String path, String token, String json, Map<String, String> headers)
            throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(5000);
        c.setReadTimeout(10000);
        if (token != null) {
            c.setRequestProperty("Authorization", "Bearer " + token);
        }
        headers.forEach(c::setRequestProperty);
        if (json != null) {
            c.setDoOutput(true);
            if (!headers.containsKey("Content-Type")) {
                c.setRequestProperty("Content-Type", "application/json");
            }
            try (OutputStream os = c.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = c.getResponseCode();
        InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        return new Res(status, body, c.getHeaderFields());
    }

    private Res get(String path, String token) throws IOException {
        return call("GET", path, token, null, Map.of());
    }

    private Res post(String path, String token, String json) throws IOException {
        return call("POST", path, token, json == null ? "{}" : json, Map.of());
    }

    private String login(User u) throws Exception {
        Res r = post("/api/auth/login", null, "{\"email\":\"" + u.getEmail() + "\",\"password\":\""
                + Fixture.PASSWORD + "\"}");
        assertEquals(200, r.status(), r.body());
        return (String) r.json().get("token");
    }

    @Test
    void publicEndpointsNeedNoSession() throws Exception {
        assertEquals("UP", get("/api/health", null).json().get("status"));
        assertTrue(get("/api/meta", null).json().containsKey("categories"));
        assertTrue(get("/api/public/stats", null).json().containsKey("mealsServed"));
        Map<String, Object> net = get("/api/public/network", null).json();
        assertEquals(List.of(), net.get("volunteers"), "volunteers are never shown publicly");
    }

    @Test
    void registrationLoginAndPermissionChecks() throws Exception {
        Res reg = post("/api/auth/register", null, "{\"role\":\"VOLUNTEER\",\"name\":\"Priya Nair\","
                + "\"email\":\"priya@test.campus\",\"password\":\"Sc00terGirl\",\"phone\":\"9900000002\","
                + "\"vehicle\":\"SCOOTER\",\"lat\":12.97,\"lng\":77.59}");
        assertEquals(201, reg.status(), reg.body());
        String token = (String) reg.json().get("token");

        Res me = get("/api/auth/me", token);
        assertEquals(200, me.status());
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) me.json().get("user");
        assertEquals("VOLUNTEER", user.get("role"));
        assertNull(user.get("password"));

        Res anonymous = get("/api/auth/me", null);
        assertEquals(401, anonymous.status());
        assertEquals("Bearer", anonymous.header("WWW-Authenticate"));
        assertEquals(403, get("/api/users", token).status());
        assertEquals(403, post("/api/matching/run", token, null).status());

        assertEquals(204, post("/api/auth/logout", token, null).status());
        assertEquals(401, get("/api/auth/me", token).status());
    }

    @Test
    void corsIsLimitedToConfiguredOrigins() throws Exception {
        Res allowed = call("GET", "/api/health", null, null, Map.of("Origin", ORIGIN));
        assertEquals(ORIGIN, allowed.header("Access-Control-Allow-Origin"));
        Res evil = call("GET", "/api/health", null, null, Map.of("Origin", "https://evil.example"));
        assertNull(evil.header("Access-Control-Allow-Origin"));
    }

    @Test
    void malformedRequestsGetPreciseErrors() throws Exception {
        assertEquals(404, get("/api/nope", null).status());
        Res wrongMethod = call("DELETE", "/api/health", null, null, Map.of());
        assertEquals(405, wrongMethod.status());
        assertEquals("GET", wrongMethod.header("Allow"));
        Res formPost = call("POST", "/api/auth/login", null, "email=a&password=b",
                Map.of("Content-Type", "application/x-www-form-urlencoded"));
        assertEquals(400, formPost.status());
        assertEquals("INVALID_INPUT", formPost.json().get("code"));
        Res badJson = post("/api/auth/login", null, "{\"email\":");
        assertEquals(400, badJson.status());
    }

    @Test
    void wholeDonationToDeliveryFlowOverHttp() throws Exception {
        Donor donor = f.donor();
        Recipient recipient = f.recipient("Asha Bhavan", NeedLevel.CRITICAL, 100);
        String donorToken = login(donor);
        String adminToken = login(f.admin);
        String recipientToken = login(recipient);
        Res vol = post("/api/auth/register", null, "{\"role\":\"VOLUNTEER\",\"name\":\"Arjun\","
                + "\"email\":\"arjun@test.campus\",\"password\":\"Cycl1ngFast\",\"phone\":\"9900000001\","
                + "\"vehicle\":\"BICYCLE\"}");
        String volunteerToken = (String) vol.json().get("token");

        Res batch = post("/api/batches", donorToken, "{\"foodName\":\"Veg Biryani\",\"category\":\"COOKED_MEAL\","
                + "\"servings\":30,\"shelfLifeHours\":3,\"vegetarian\":true}");
        assertEquals(201, batch.status(), batch.body());
        assertEquals(403, post("/api/batches", volunteerToken, "{\"foodName\":\"x\",\"category\":\"BAKERY\","
                + "\"servings\":5,\"shelfLifeMinutes\":60}").status());

        Res run = post("/api/matching/run", adminToken, "{}");
        assertEquals(200, run.status(), run.body());
        assertEquals(30L, run.json().get("allocatedServings"));

        List<Object> open = get("/api/dispatches/open", volunteerToken).list();
        assertEquals(1, open.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> pickup = (Map<String, Object>) open.get(0);
        String id = (String) pickup.get("id");
        assertNotNull(pickup.get("etaMinutes"));

        assertEquals(200, post("/api/dispatches/" + id + "/claim", volunteerToken, null).status());
        assertEquals(200, post("/api/dispatches/" + id + "/pickup", volunteerToken, null).status());
        Res delivered = post("/api/dispatches/" + id + "/deliver", volunteerToken, null);
        assertEquals("DELIVERED", delivered.json().get("status"));
        assertEquals(List.of("confirm"), delivered.json().get("allowedActions"));
        Res confirmed = post("/api/dispatches/" + id + "/confirm", recipientToken, "{\"rating\":5}");
        assertEquals(200, confirmed.status(), confirmed.body());

        Map<String, Object> impact = get("/api/reports/impact", adminToken).json();
        assertEquals(30L, impact.get("servingsDelivered"));
        assertEquals(403, get("/api/reports/impact", donorToken).status());
        assertEquals(true, get("/api/audit/verify", adminToken).json().get("valid"));
    }

    @Test
    void liveEventsReachSubscribedClients() throws Exception {
        Donor donor = f.donor();
        String donorToken = login(donor);
        HttpURLConnection c = (HttpURLConnection) new URL(base + "/api/events").openConnection();
        c.setRequestProperty("Authorization", "Bearer " + donorToken);
        c.setReadTimeout(10000);
        assertEquals(200, c.getResponseCode());
        BufferedReader reader = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
        CompletableFuture<String> seen = CompletableFuture.supplyAsync(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.equals("event: BATCH_LOGGED")) {
                        return line;
                    }
                }
                return null;
            } catch (IOException e) {
                return null;
            }
        });
        // wait for the stream to be ready, then cause an event
        Thread.sleep(300);
        post("/api/batches", donorToken, "{\"foodName\":\"Idli\",\"category\":\"COOKED_MEAL\",\"servings\":12,"
                + "\"shelfLifeMinutes\":180}");
        assertEquals("event: BATCH_LOGGED", seen.get(10, TimeUnit.SECONDS));
        c.disconnect();
    }
}
