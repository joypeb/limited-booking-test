package com.limitedbooking.backend.security;

import com.limitedbooking.backend.support.ContainerTestSupport;
import com.limitedbooking.backend.support.HttpClientTestSupport;
import com.limitedbooking.backend.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DirtiesContext
@TestExecutionListeners(
        listeners = SecurityIT.DependencyCleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityIT {
    static final ContainerTestSupport containers = new ContainerTestSupport();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    DataSource dataSource;

    @Autowired
    LocalIdentityResolver identityResolver;

    @Autowired
    AuthenticationConfiguration authenticationConfiguration;

    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    @Autowired com.limitedbooking.backend.ratelimit.RateLimitProperties rateLimitPolicy;
    @Autowired org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory redisFactory;
    TestData testData;

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
    void resetDatabase() {
        new com.limitedbooking.backend.support.RateLimitTestSupport(redis).reset();
        com.limitedbooking.backend.support.RateLimitTestSupport.assertSettings(rateLimitPolicy, redisFactory, 20);
        testData = new TestData(dataSource);
        testData.reset();
    }

    @Test
    void unauthenticatedAndInvalidCredentialsReturn401ProblemJsonWithWwwAuthenticate() throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        try {
            String targetPath = "/api/resources/" + UUID.randomUUID();
            var unauthenticatedGet = HttpRequest.newBuilder(uri(targetPath))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            var unauthResponse = HttpClientTestSupport.send(client, unauthenticatedGet);
            assertProblem(unauthResponse, 401, "UNAUTHENTICATED", targetPath);
            assertThat(unauthResponse.headers().firstValue("WWW-Authenticate"))
                    .isPresent()
                    .get()
                    .asString()
                    .contains("Basic realm=\"reservation-local\"");

            String wrongPassword = "wrong-secret-password-value";
            var wrongPasswordPost = HttpRequest.newBuilder(uri("/api/resources"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", HttpClientTestSupport.basicAuth(TestData.ADMIN_USERNAME, wrongPassword))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"좌석\",\"capacity\":10}"))
                    .build();
            var wrongPasswordResponse = HttpClientTestSupport.send(client, wrongPasswordPost);
            assertProblem(wrongPasswordResponse, 401, "UNAUTHENTICATED", "/api/resources");
            assertThat(wrongPasswordResponse.headers().firstValue("WWW-Authenticate"))
                    .isPresent()
                    .get()
                    .asString()
                    .contains("Basic realm=\"reservation-local\"");
            assertThat(wrongPasswordResponse.body())
                    .doesNotContain(wrongPassword)
                    .doesNotContain(TestData.ADMIN_PASSWORD);

            var unknownUserGet = HttpRequest.newBuilder(uri(targetPath))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", HttpClientTestSupport.basicAuth("UNKNOWN_USER", "any-password"))
                    .GET()
                    .build();
            var unknownUserResponse = HttpClientTestSupport.send(client, unknownUserGet);
            assertProblem(unknownUserResponse, 401, "UNAUTHENTICATED", targetPath);
        } finally {
            HttpClientTestSupport.close(client);
        }
    }

    @Test
    void regularUsersAreForbiddenFromCreatingResourcesAndSpoofedUserIdHeaderIsIgnored() throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        try {
            for (String username : new String[]{TestData.USER_A_USERNAME, TestData.USER_B_USERNAME}) {
                String password = username.equals(TestData.USER_A_USERNAME)
                        ? TestData.USER_A_PASSWORD
                        : TestData.USER_B_PASSWORD;
                var createRequest = HttpRequest.newBuilder(uri("/api/resources"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", HttpClientTestSupport.basicAuth(username, password))
                        .header("X-User-Id", TestData.ADMIN_ID.toString())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"일반 사용자 생성 시도\",\"capacity\":10}"))
                        .build();
                var response = HttpClientTestSupport.send(client, createRequest);
                assertProblem(response, 403, "FORBIDDEN", "/api/resources");
                assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
            }
            assertThat(testData.resourceCount()).isZero();
        } finally {
            HttpClientTestSupport.close(client);
        }
    }

    @Test
    void adminCreatesResourceAndAllAuthenticatedAccountsCanReadStatelessly() throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        try {
            var createRequest = HttpRequest.newBuilder(uri("/api/resources"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", HttpClientTestSupport.basicAuth(TestData.ADMIN_USERNAME, TestData.ADMIN_PASSWORD))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"보안 검증 좌석\",\"capacity\":25}"))
                    .build();
            var createResponse = HttpClientTestSupport.send(client, createRequest);
            assertThat(createResponse.statusCode()).isEqualTo(201);
            assertThat(createResponse.headers().firstValue("Set-Cookie")).isEmpty();

            JsonNode created = JSON.readTree(createResponse.body());
            UUID resourceId = UUID.fromString(created.get("id").asString());
            String expectedLocation = "/api/resources/" + resourceId;
            assertThat(createResponse.headers().firstValue("Location")).contains(expectedLocation);
            assertThat(created.get("name").asString()).isEqualTo("보안 검증 좌석");
            assertThat(created.get("capacity").asInt()).isEqualTo(25);
            assertThat(created.get("available").asInt()).isEqualTo(25);

            String[][] accounts = {
                    {TestData.ADMIN_USERNAME, TestData.ADMIN_PASSWORD},
                    {TestData.USER_A_USERNAME, TestData.USER_A_PASSWORD},
                    {TestData.USER_B_USERNAME, TestData.USER_B_PASSWORD}
            };
            for (String[] account : accounts) {
                var getRequest = HttpRequest.newBuilder(uri(expectedLocation))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", HttpClientTestSupport.basicAuth(account[0], account[1]))
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .GET()
                        .build();
                var getResponse = HttpClientTestSupport.send(client, getRequest);
                assertThat(getResponse.statusCode()).isEqualTo(200);
                assertThat(getResponse.headers().firstValue("Set-Cookie")).isEmpty();
                JsonNode fetched = JSON.readTree(getResponse.body());
                assertThat(fetched.get("id").asString()).isEqualTo(resourceId.toString());
                assertThat(fetched.get("name").asString()).isEqualTo("보안 검증 좌석");
                assertThat(fetched.get("capacity").asInt()).isEqualTo(25);
                assertThat(fetched.get("available").asInt()).isEqualTo(25);
            }
        } finally {
            HttpClientTestSupport.close(client);
        }
    }

    @Test
    void rejectsNonEmptyInvalidUuidAndOversizedCancellationBeforeBusinessEntry() throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        try {
            // 1. 취소 요청(/api/reservations/{id}/cancel)에 8KiB 이하 비어 있지 않은 본문 -> 400 INVALID_REQUEST
            String cancelPath = "/api/reservations/" + UUID.randomUUID() + "/cancel";
            for (String nonEmptyCancelBody : new String[]{"{}", " ", "{\"reason\":\"cancel\"}"}) {
                var cancelWithBody = HttpRequest.newBuilder(uri(cancelPath))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", HttpClientTestSupport.basicAuth(TestData.USER_A_USERNAME, TestData.USER_A_PASSWORD))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(nonEmptyCancelBody))
                        .build();
                var cancelBodyResponse = HttpClientTestSupport.send(client, cancelWithBody);
                assertProblem(cancelBodyResponse, 400, "INVALID_REQUEST", cancelPath);
            }

            // 2. 취소 요청 경로의 잘못된 UUID -> 400 INVALID_REQUEST
            String invalidCancelUuidPath = "/api/reservations/invalid-uuid/cancel";
            var cancelBadUuid = HttpRequest.newBuilder(uri(invalidCancelUuidPath))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", HttpClientTestSupport.basicAuth(TestData.USER_A_USERNAME, TestData.USER_A_PASSWORD))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            var cancelBadUuidResponse = HttpClientTestSupport.send(client, cancelBadUuid);
            assertProblem(cancelBadUuidResponse, 400, "INVALID_REQUEST", invalidCancelUuidPath);

            // 3. 취소 요청에 8KiB 초과 본문(8,193바이트) -> 413 PAYLOAD_TOO_LARGE
            byte[] oversizedCancelBody = new byte[8193];
            java.util.Arrays.fill(oversizedCancelBody, (byte) 'x');
            var cancelOversized = HttpRequest.newBuilder(uri(cancelPath))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", HttpClientTestSupport.basicAuth(TestData.USER_A_USERNAME, TestData.USER_A_PASSWORD))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(oversizedCancelBody))
                    .build();
            var cancelOversizedResponse = HttpClientTestSupport.send(client, cancelOversized);
            assertProblem(cancelOversizedResponse, 413, "PAYLOAD_TOO_LARGE", cancelPath);

            assertThat(testData.resourceCount()).isZero();
        } finally {
            HttpClientTestSupport.close(client);
        }
    }

    @Test
    void localIdentityResolverReturnsDistinctFixedUuidsFromPrincipalOnly() throws Exception {
        AuthenticationManager authManager = authenticationConfiguration.getAuthenticationManager();

        Authentication adminAuth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(TestData.ADMIN_USERNAME, TestData.ADMIN_PASSWORD));
        Authentication userAAuth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(TestData.USER_A_USERNAME, TestData.USER_A_PASSWORD));
        Authentication userBAuth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(TestData.USER_B_USERNAME, TestData.USER_B_PASSWORD));

        UUID adminId = identityResolver.userId(adminAuth);
        UUID userAId = identityResolver.userId(userAAuth);
        UUID userBId = identityResolver.userId(userBAuth);

        assertThat(adminId).isEqualTo(TestData.ADMIN_ID);
        assertThat(userAId).isEqualTo(TestData.USER_A_ID);
        assertThat(userBId).isEqualTo(TestData.USER_B_ID);
        assertThat(Set.of(adminId, userAId, userBId)).hasSize(3);

        assertThatThrownBy(() -> identityResolver.userId(null))
                .isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> identityResolver.userId(
                new UsernamePasswordAuthenticationToken("USER_A", "ignored")))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void rejectsPayloadsOver8KiBWith413BeforeAuthenticationIncludingChunkedAndManipulatedLength() throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        try {
            byte[] oversized = ("{\"name\":\"" + "x".repeat(8180) + "\",\"capacity\":10}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(oversized.length).isGreaterThan(8192);

            // 1. 미인증 + Content-Length 포함 8,193바이트 이상 요청 -> 401보다 먼저 413 반환
            var unauthContentLengthReq = HttpRequest.newBuilder(uri("/api/resources"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(oversized))
                    .build();
            var unauthContentLengthRes = HttpClientTestSupport.send(client, unauthContentLengthReq);
            assertProblem(unauthContentLengthRes, 413, "PAYLOAD_TOO_LARGE", "/api/resources");

            // 2. 미인증 + Transfer-Encoding: chunked (Content-Length 없음) 8,193바이트 요청 -> 413 반환
            byte[] exact8193 = new byte[8193];
            java.util.Arrays.fill(exact8193, (byte) 'a');
            var unauthChunkedReq = HttpRequest.newBuilder(uri("/api/resources"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(exact8193)))
                    .build();
            var unauthChunkedRes = HttpClientTestSupport.send(client, unauthChunkedReq);
            assertProblem(unauthChunkedRes, 413, "PAYLOAD_TOO_LARGE", "/api/resources");

            // 3. 미인증 + raw HTTP로 Content-Length 없이 chunked 스트림 전송 (실제 바이트 8,193바이트) -> 413 반환
            String chunkHex = Integer.toHexString(exact8193.length);
            byte[] chunkedWireBody = (chunkHex + "\r\n"
                    + new String(exact8193, java.nio.charset.StandardCharsets.UTF_8)
                    + "\r\n0\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String rawHeaders = "POST /api/resources HTTP/1.1\r\n"
                    + "Host: 127.0.0.1:" + port + "\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Transfer-Encoding: chunked\r\n"
                    + "Connection: close\r\n\r\n";
            var rawRes = HttpClientTestSupport.sendRawHttp(port, rawHeaders, chunkedWireBody);
            assertThat(rawRes.statusCode()).isEqualTo(413);
            assertThat(rawRes.headers().get("content-type")).startsWith("application/problem+json");
            JsonNode rawProblem = JSON.readTree(rawRes.body());
            assertThat(rawProblem.get("status").asInt()).isEqualTo(413);
            assertThat(rawProblem.get("code").asString()).isEqualTo("PAYLOAD_TOO_LARGE");
            assertThat(rawProblem.get("instance").asString()).isEqualTo("/api/resources");

            assertThat(testData.resourceCount()).isZero();
        } finally {
            HttpClientTestSupport.close(client);
        }
    }

    @Test
    void forwardsBodyAtExact8KiBLimitViaReusableRequestWrapperWithoutDataLoss() throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        try {
            String prefix = "{\"name\":\"8KiB정확상한좌석\",\"capacity\":10";
            String suffix = "}";
            int currentBytes = (prefix + suffix).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            String paddedJson = prefix + " ".repeat(8192 - currentBytes) + suffix;
            byte[] bodyBytes = paddedJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(bodyBytes.length).isEqualTo(8192);

            var request = HttpRequest.newBuilder(uri("/api/resources"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", HttpClientTestSupport.basicAuth(TestData.ADMIN_USERNAME, TestData.ADMIN_PASSWORD))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                    .build();
            var response = HttpClientTestSupport.send(client, request);
            assertThat(response.statusCode()).isEqualTo(201);
            JsonNode created = JSON.readTree(response.body());
            assertThat(created.get("name").asString()).isEqualTo("8KiB정확상한좌석");
            assertThat(created.get("capacity").asInt()).isEqualTo(10);
            assertThat(created.get("available").asInt()).isEqualTo(10);
        } finally {
            HttpClientTestSupport.close(client);
        }
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
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
