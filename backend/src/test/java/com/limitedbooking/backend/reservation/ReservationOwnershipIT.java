package com.limitedbooking.backend.reservation;

import com.limitedbooking.backend.support.ContainerTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext
@TestExecutionListeners(listeners = ReservationOwnershipIT.Cleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationOwnershipIT extends ReservationApiTestSupport {
    static final ContainerTestSupport containers = new ContainerTestSupport();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        containers.registerLocalAuthAndDatabaseProperties(p);
        p.add("spring.datasource.hikari.maximum-pool-size", () -> 10);
        p.add("spring.datasource.hikari.connection-timeout", () -> 3000);
    }
    public static class Cleanup extends AbstractTestExecutionListener {
        @Override public int getOrder() { return 2999; }
        @Override public void afterTestClass(TestContext c) { containers.close(); }
    }
    @Test void ownerReadsCurrentConfirmedStateIncludingNullCancelledAt() throws Exception {
        UUID resource = resource(1), id = confirmed(resource);
        var response = get(id);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        var body = JSON.readTree(response.body());
        assertThat(body.size()).isEqualTo(5);
        assertThat(body.get("id").asString()).isEqualTo(id.toString());
        assertThat(body.get("resourceId").asString()).isEqualTo(resource.toString());
        assertThat(body.get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(body.get("createdAt").asString()).endsWith("Z");
        assertThat(body.has("cancelledAt")).isTrue();
        assertThat(body.get("cancelledAt").isNull()).isTrue();
        inventory(resource, 0, 1, 1);
    }
    @Test void otherUserAndAdminCannotDistinguishMissingReservation() throws Exception {
        UUID resource = resource(1), id = confirmed(resource);
        var missing = request("GET", path(UUID.randomUUID()), "USER_A", null, null);
        problem(missing, 404, "NOT_FOUND");
        for (String user : new String[]{"USER_B", "ADMIN"}) {
            var response = request("GET", path(id), user, null, null);
            problem(response, 404, "NOT_FOUND");
            var expected = JSON.readTree(missing.body());
            var actual = JSON.readTree(response.body());
            for (String field : new String[]{"type", "title", "status", "detail", "code"})
                assertThat(actual.get(field)).isEqualTo(expected.get(field));
        }
        inventory(resource, 0, 1, 1);
    }
    @Test void getRequiresAuthenticationAndValidUuid() throws Exception {
        var unauth = request("GET", path(UUID.randomUUID()), null, null, null);
        problem(unauth, 401, "UNAUTHENTICATED");
        assertThat(unauth.headers().firstValue("WWW-Authenticate").orElseThrow()).startsWith("Basic ");
        for (String invalid : new String[]{"not-a-uuid", "1-1-1-1-1"})
            problem(request("GET", "/api/reservations/" + invalid, "USER_A", null, null), 400, "INVALID_REQUEST");
    }
    @Test void cancelProtectsOwnershipAndLeavesInventoryUnchanged() throws Exception {
        UUID resource = resource(1), id = confirmed(resource);
        for (String user : new String[]{"USER_B", "ADMIN"}) {
            var other = request("POST", path(id) + "/cancel", user, null, null);
            var missing = request("POST", path(UUID.randomUUID()) + "/cancel", user, null, null);
            problem(other, 404, "NOT_FOUND");
            problem(missing, 404, "NOT_FOUND");
            for (String field : new String[]{"type", "title", "status", "detail", "code"})
                assertThat(JSON.readTree(other.body()).get(field)).isEqualTo(JSON.readTree(missing.body()).get(field));
            inventory(resource, 0, 1, 1);
        }
        var unauth = request("POST", path(id) + "/cancel", null, null, null);
        problem(unauth, 401, "UNAUTHENTICATED");
        assertThat(unauth.headers().firstValue("WWW-Authenticate").orElseThrow()).startsWith("Basic ");
        for (String invalid : new String[]{"not-a-uuid", "1-1-1-1-1"})
            problem(request("POST", "/api/reservations/" + invalid + "/cancel", "USER_A", null, null), 400, "INVALID_REQUEST");
        inventory(resource, 0, 1, 1);
        assertThat(JSON.readTree(get(id).body()).get("status").asString()).isEqualTo("CONFIRMED");
    }
    @Test void oversizedCancelIsRejectedBeforeAuthentication() throws Exception {
        UUID resource = resource(1), id = confirmed(resource);
        problem(request("POST", path(id) + "/cancel", null, "x".repeat(8193), null), 413, "PAYLOAD_TOO_LARGE");
        inventory(resource, 0, 1, 1);
        assertThat(JSON.readTree(get(id).body()).get("status").asString()).isEqualTo("CONFIRMED");
    }

}
