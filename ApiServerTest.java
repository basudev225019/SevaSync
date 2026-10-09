package com.sevasync;

import com.sevasync.api.ApiServer;
import com.sevasync.util.Json;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiServerTest {
    private record Res(int status, String body) {
        Map<String, Object> json() throws Exception {
            return Json.parseObject(body);
        }
    }

    private Fixture f;
    private ApiServer server;
    private final HttpClient client = HttpClient.newHttpClient();
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        f = new Fixture();
        server = new ApiServer(f.sys, "127.0.0.1", 0, null);
        server.start();
        base = "http://127.0.0.1:" + server.getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop();
        f.close();
    }

    private Res call(String method, String path, String body, String apiKey, String baseUrl) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (apiKey != null) {
            b.header("X-API-Key", apiKey);
        }
        HttpResponse<String> r = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Res(r.statusCode(), r.body());
    }

    private Res call(String method, String path, String body) throws Exception {
        return call(method, path, body, null, base);
    }

    @Test
    void fullDonationToDeliveryFlow() throws Exception {
        assertEquals("UP", call("GET", "/api/health", null).json().get("status"));

        Res donor = call("POST", "/api/donors",
                "{\"name\":\"Boys Hostel Mess\",\"phone\":\"9876543210\",\"address\":\"North Block\",\"donorType\":\"hostel_mess\"}");
        assertEquals(201, donor.status());
        String donorId = (String) donor.json().get("id");

        Res rec = call("POST", "/api/recipients",
                "{\"name\":\"Asha Home\",\"phone\":\"9812345678\",\"recipientType\":\"ORPHANAGE\",\"needLevel\":\"CRITICAL\",\"dailyCapacity\":60}");
        assertEquals(201, rec.status());
        assertEquals(60L, rec.json().get("remainingCapacityToday"));

        Res batch = call("POST", "/api/batches", "{\"donorId\":\"" + donorId
                + "\",\"foodName\":\"Veg Biryani\",\"category\":\"COOKED_MEAL\",\"vegetarian\":true,\"servings\":80,\"shelfLifeHours\":3}");
        assertEquals(201, batch.status());
        assertEquals("AVAILABLE", batch.json().get("status"));

        Res preview = call("POST", "/api/matching/preview", null);
        assertEquals(200, preview.status());
        assertEquals(false, preview.json().get("committed"));
        assertEquals(20L, preview.json().get("unallocatedServings"));
        assertEquals("[]", call("GET", "/api/dispatches", null).body);

        Res run = call("POST", "/api/matching/run", null);
        assertEquals(60L, run.json().get("allocatedServings"));

        Res assigned = call("GET", "/api/dispatches?status=ASSIGNED", null);
        assertTrue(assigned.body.contains("DSP-0001"));
        Res delivered = call("POST", "/api/dispatches/DSP-0001/deliver", "{\"volunteer\":\"Ravi\"}");
        assertEquals(200, delivered.status());
        assertEquals("DELIVERED", delivered.json().get("status"));

        Res report = call("GET", "/api/reports/impact", null);
        assertEquals(60L, report.json().get("servingsDelivered"));
        assertEquals(80L, report.json().get("servingsDonated"));

        assertEquals(true, call("GET", "/api/audit/verify", null).json().get("valid"));
        assertTrue(call("GET", "/api/audit?limit=5", null).body.contains("DISPATCH_DELIVERED"));
    }

    @Test
    void errorsMapToSensibleStatusCodes() throws Exception {
        String donorBody = "{\"name\":\"Mess\",\"phone\":\"9876543210\",\"donorType\":\"CANTEEN\"}";
        call("POST", "/api/donors", donorBody);

        assertEquals(400, call("POST", "/api/donors", "{\"name\":\"X\",\"phone\":\"12\",\"donorType\":\"CANTEEN\"}").status());
        assertEquals(400, call("POST", "/api/donors", "not json").status());
        assertEquals(404, call("GET", "/api/batches/BAT-9999", null).status());
        assertEquals(404, call("GET", "/api/nothing", null).status());
        assertEquals(405, call("DELETE", "/api/donors", null).status());
        assertEquals(422, call("POST", "/api/batches", "{\"donorId\":\"DON-0001\",\"foodName\":\"Rice\","
                + "\"category\":\"COOKED_MEAL\",\"servings\":10,\"shelfLifeHours\":10}").status());
        assertEquals("UnsafeFoodException", call("POST", "/api/batches", "{\"donorId\":\"DON-0001\",\"foodName\":\"Rice\","
                + "\"category\":\"COOKED_MEAL\",\"servings\":10,\"shelfLifeHours\":10}").json().get("type"));
        assertEquals(404, call("POST", "/api/dispatches",
                "{\"batchId\":\"BAT-9999\",\"recipientId\":\"REC-9999\",\"servings\":1}").status());

        call("POST", "/api/recipients", "{\"name\":\"Tiny\",\"phone\":\"9812345678\",\"recipientType\":\"NGO\","
                + "\"needLevel\":\"NORMAL\",\"dailyCapacity\":5}");
        call("POST", "/api/batches", "{\"donorId\":\"DON-0001\",\"foodName\":\"Rice\",\"category\":\"COOKED_MEAL\","
                + "\"servings\":10,\"shelfLifeHours\":3}");
        Res tooMany = call("POST", "/api/dispatches", "{\"batchId\":\"BAT-0001\",\"recipientId\":\"REC-0001\",\"servings\":6}");
        assertEquals(409, tooMany.status());
        assertEquals("CapacityExceededException", tooMany.json().get("type"));
    }

    @Test
    void apiKeyIsEnforcedWhenConfigured() throws Exception {
        ApiServer secured = new ApiServer(f.sys, "127.0.0.1", 0, "s3cret");
        secured.start();
        try {
            String url = "http://127.0.0.1:" + secured.getPort();
            assertEquals(200, call("GET", "/api/health", null, null, url).status());
            assertEquals(401, call("GET", "/api/donors", null, null, url).status());
            assertEquals(401, call("GET", "/api/donors", null, "wrong", url).status());
            assertEquals(200, call("GET", "/api/donors", null, "s3cret", url).status());
        } finally {
            secured.stop();
        }
    }
}
