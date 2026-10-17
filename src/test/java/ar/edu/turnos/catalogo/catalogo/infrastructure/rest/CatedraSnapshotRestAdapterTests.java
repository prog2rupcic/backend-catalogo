package ar.edu.turnos.catalogo.catalogo.infrastructure.rest;

import ar.edu.turnos.catalogo.config.CatedraIntegrationProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class CatedraSnapshotRestAdapterTests {

    @Test
    void readsCourseContractAndUsesTechnicalBearerToken() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/synchronization/snapshot", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            path.set(exchange.getRequestURI().getPath());
            byte[] json = """
                    {
                      "snapshotVersion": 7,
                      "generatedAt": "2026-09-10T13:44:59Z",
                      "professionalCategories": [
                        {"id":1,"name":"Clinica","description":null,"enabled":false,
                         "createdAt":"2026-07-01T12:00:00Z","updatedAt":"2026-09-10T13:40:00Z"}
                      ],
                      "professionals": [
                        {"id":15,"categoryId":1,"firstName":"Ana","lastName":"Gomez",
                         "enabled":true,"createdAt":"2026-07-01T12:00:00Z","updatedAt":"2026-09-10T13:40:00Z"}
                      ],
                      "weeklySchedules": [
                        {"id":21,"professionalId":15,"dayOfWeek":"MONDAY",
                         "startTime":"09:00:00","endTime":"12:00","slotDurationMinutes":30,
                         "enabled":true,"createdAt":"2026-07-01T12:00:00Z",
                         "updatedAt":"2026-09-10T13:40:00Z"}
                      ]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length);
            try (var out = exchange.getResponseBody()) {
                out.write(json);
            }
        });
        server.start();
        try {
            var properties = new CatedraIntegrationProperties(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    "test-token", "catedra.catalog.test");
            var adapter = new CatedraSnapshotRestAdapter(RestClient.builder(), properties);
            var snapshot = adapter.fetch();
            assertThat(authorization).hasValue("Bearer test-token");
            assertThat(path).hasValue("/api/synchronization/snapshot");
            assertThat(snapshot.version()).isEqualTo(7);
            assertThat(snapshot.categories().getFirst().enabled()).isFalse();
            assertThat(snapshot.professionals().getFirst().categoryId()).isEqualTo(1);
            assertThat(snapshot.schedules().getFirst().dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(snapshot.schedules().getFirst().startTime()).isEqualTo(LocalTime.of(9, 0));
        } finally {
            server.stop(0);
        }
    }
}
