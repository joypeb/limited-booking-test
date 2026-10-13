package com.limitedbooking.backend;

import com.limitedbooking.backend.support.ContainerTestSupport;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.annotation.DirtiesContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext
@TestExecutionListeners(listeners = BackendApplicationIT.DependencyCleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendApplicationIT {
    static final ContainerTestSupport resources = new ContainerTestSupport();
    @LocalServerPort int port;
    @DynamicPropertySource static void connectionProperties(DynamicPropertyRegistry properties) {
        var pg=resources.postgres;
        properties.add("server.address",()->"127.0.0.1");
        properties.add("spring.datasource.url",pg::getJdbcUrl);
        properties.add("spring.datasource.username",pg::getUsername);
        properties.add("spring.datasource.password",pg::getPassword);
        properties.add("spring.flyway.locations",()->"classpath:db/toolchain");
        properties.add("spring.data.redis.host",resources.redis::getHost);
        properties.add("spring.data.redis.port",()->resources.redis.getMappedPort(6379));
        properties.add("spring.security.user.name",()->"toolchain");
        String password=UUID.randomUUID().toString();
        properties.add("spring.security.user.password",()->password);
        properties.add("management.endpoints.web.exposure.include",()->"health");
        properties.add("management.endpoint.health.show-details",()->"never");
    }
    // Spring의 AFTER_CLASS 컨텍스트 종료(order3000) 뒤에 이번 의존 자원을 종료한다.
    public static class DependencyCleanup extends AbstractTestExecutionListener {
        @Override public int getOrder() { return 2999; }
        @Override public void afterTestClass(TestContext testContext) { resources.close(); }
    }
    @Test void contextLoadsAndHealthRespondsOverHttp() throws Exception {
        try (var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/actuator/health"))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"status\":\"UP\"").doesNotContain("components");
        }
    }
}
