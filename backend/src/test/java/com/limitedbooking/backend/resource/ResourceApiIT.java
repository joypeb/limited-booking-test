package com.limitedbooking.backend.resource;

import com.limitedbooking.backend.support.ContainerTestSupport;
import com.limitedbooking.backend.support.HttpClientTestSupport;
import com.limitedbooking.backend.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext
@TestExecutionListeners(
        listeners = ResourceApiIT.DependencyCleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResourceApiIT {
    static final ContainerTestSupport containers = ContainerTestSupport.postgresOnly();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    DataSource dataSource;

    TestData testData;
    JdbcTemplate jdbc;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry properties) {
        containers.registerLocalAuthAndDatabaseProperties(properties);
    }

    public static class DependencyCleanup extends AbstractTestExecutionListener {
        @Override
        public int getOrder() {
            return 2999;
        }

        @Override
        public void afterTestClass(TestContext testContext) {
            containers.close();
        }
    }

    @BeforeEach
    void setUp() {
        testData = new TestData(dataSource);
        testData.reset();
        jdbc = new JdbcTemplate(dataSource);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1000})
    void createsAndReadsResourceAtCapacityBoundaries(int capacity) throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        try {
            Instant before = Instant.now().minusSeconds(5);
            String payload = JSON.writeValueAsString(Map.of(
                    "name", "수량 경계 좌석 " + capacity,
                    "capacity", capacity
            ));
            var createRequest = HttpRequest.newBuilder(uri("/api/resources"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", adminAuth())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            var createResponse = HttpClientTestSupport.send(client, createRequest);
            assertThat(createResponse.statusCode()).isEqualTo(201);

            JsonNode created = JSON.readTree(createResponse.body());
            UUID resourceId = UUID.fromString(created.get("id").asString());
            String expectedLocation = "/api/resources/" + resourceId;
            assertThat(createResponse.headers().firstValue("Location")).contains(expectedLocation);
            assertThat(created.get("name").asString()).isEqualTo("수량 경계 좌석 " + capacity);
            assertThat(created.get("capacity").asInt()).isEqualTo(capacity);
            assertThat(created.get("available").asInt()).isEqualTo(capacity);

            Instant storedCreatedAt = jdbc.queryForObject(
                    "SELECT created_at FROM resources WHERE id = ?",
                    (rs, rowNum) -> rs.getObject(1, OffsetDateTime.class).toInstant(),
                    resourceId
            );
            assertThat(storedCreatedAt).isAfterOrEqualTo(before).isBeforeOrEqualTo(Instant.now().plusSeconds(5));

            var getRequest = HttpRequest.newBuilder(uri(expectedLocation))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", userAAuth())
                    .GET()
                    .build();
            var getResponse = HttpClientTestSupport.send(client, getRequest);
            assertThat(getResponse.statusCode()).isEqualTo(200);
            JsonNode fetched = JSON.readTree(getResponse.body());
            assertThat(fetched.get("id").asString()).isEqualTo(resourceId.toString());
            assertThat(fetched.get("name").asString()).isEqualTo("수량 경계 좌석 " + capacity);
            assertThat(fetched.get("capacity").asInt()).isEqualTo(capacity);
            assertThat(fetched.get("available").asInt()).isEqualTo(capacity);
        } finally {
            HttpClientTestSupport.close(client);
        }
    }

    @Test
    void normalizesSurroundingWhitespaceAndPreservesInternalSpacesNbspAndEmojiHundredCodePoints() throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        try {
            String emojiHundred = "😀".repeat(100);
            String[][] cases = {
                    {"  \t\n공연장  A구역\r\n ", "공연장  A구역"},
                    {"\u00A0비줄바꿈\u2007공백\u202F보존\u00A0", "\u00A0비줄바꿈\u2007공백\u202F보존\u00A0"},
                    {"  " + emojiHundred + "\t\n", emojiHundred}
            };

            for (String[] testCase : cases) {
                String rawName = testCase[0];
                String expectedNormalized = testCase[1];
                String payload = JSON.writeValueAsString(Map.of("name", rawName, "capacity", 5));

                var createRequest = HttpRequest.newBuilder(uri("/api/resources"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", adminAuth())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();
                var createResponse = HttpClientTestSupport.send(client, createRequest);
                assertThat(createResponse.statusCode()).isEqualTo(201);

                JsonNode created = JSON.readTree(createResponse.body());
                UUID id = UUID.fromString(created.get("id").asString());
                assertThat(created.get("name").asString()).isEqualTo(expectedNormalized);

                var getRequest = HttpRequest.newBuilder(uri("/api/resources/" + id))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", userAAuth())
                        .GET()
                        .build();
                var getResponse = HttpClientTestSupport.send(client, getRequest);
                assertThat(getResponse.statusCode()).isEqualTo(200);
                JsonNode fetched = JSON.readTree(getResponse.body());
                assertThat(fetched.get("name").asString()).isEqualTo(expectedNormalized);
            }
        } finally {
            HttpClientTestSupport.close(client);
        }
    }

    @Test
    void returns404NotFoundWhenResourceDoesNotExist() throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        try {
            String missingPath = "/api/resources/" + UUID.randomUUID();
            var getRequest = HttpRequest.newBuilder(uri(missingPath))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", userAAuth())
                    .GET()
                    .build();
            var response = HttpClientTestSupport.send(client, getRequest);
            assertProblem(response, 404, "NOT_FOUND", missingPath);
        } finally {
            HttpClientTestSupport.close(client);
        }
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static String adminAuth() {
        return HttpClientTestSupport.basicAuth(TestData.ADMIN_USERNAME, TestData.ADMIN_PASSWORD);
    }

    private static String userAAuth() {
        return HttpClientTestSupport.basicAuth(TestData.USER_A_USERNAME, TestData.USER_A_PASSWORD);
    }

    static void assertProblem(HttpResponse<String> response, int expectedStatus, String expectedCode, String expectedInstance) {
        assertThat(response.statusCode()).isEqualTo(expectedStatus);
        assertThat(response.headers().firstValue("Content-Type"))
                .isPresent()
                .get()
                .asString()
                .startsWith("application/problem+json");
        JsonNode problem = JSON.readTree(response.body());
        assertThat(problem.hasNonNull("type")).isTrue();
        assertThat(problem.get("type").asString()).isNotBlank();
        assertThat(problem.hasNonNull("title")).isTrue();
        assertThat(problem.get("title").asString()).isNotBlank();
        assertThat(problem.hasNonNull("status")).isTrue();
        assertThat(problem.get("status").asInt()).isEqualTo(expectedStatus);
        assertThat(problem.hasNonNull("detail")).isTrue();
        assertThat(problem.get("detail").asString())
                .isNotBlank()
                .doesNotContainIgnoringCase("select ")
                .doesNotContainIgnoringCase("insert ")
                .doesNotContainIgnoringCase("exception")
                .doesNotContain(TestData.ADMIN_PASSWORD)
                .doesNotContain(TestData.USER_A_PASSWORD)
                .doesNotContain(TestData.USER_B_PASSWORD);
        assertThat(problem.hasNonNull("instance")).isTrue();
        assertThat(problem.get("instance").asString()).isEqualTo(expectedInstance);
        assertThat(problem.hasNonNull("code")).isTrue();
        assertThat(problem.get("code").asString()).isEqualTo(expectedCode);
    }
}
