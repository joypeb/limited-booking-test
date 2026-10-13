package com.limitedbooking.backend.common;

import com.limitedbooking.backend.support.ToolchainHttpFixture;
import com.limitedbooking.backend.support.ToolchainContractValidator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("contract")
class ToolchainContractIT {
    @Test void normalActualHttpResponseSatisfiesRequiredIdContract() throws Exception {
        try(var fixture=new ToolchainHttpFixture(false)) {
            var exchange=fixture.get();
            var report=ToolchainContractValidator.load("toolchain-smoke.openapi.yaml").validate(exchange.request(),exchange.response());
            assertThat(exchange.response().statusCode()).isEqualTo(200);
            assertThat(report.getMessages()).isEmpty();
        }
    }
    @Test void actualHttpResponseWithoutRequiredIdIsRejected() throws Exception {
        try(var fixture=new ToolchainHttpFixture(true)) {
            var exchange=fixture.get();
            var report=ToolchainContractValidator.load("toolchain-smoke.openapi.yaml").validate(exchange.request(),exchange.response());
            assertThat(exchange.response().statusCode()).isEqualTo(200);
            assertThat(report.hasErrors()).isTrue();
            assertThat(report.getMessages().toString()).contains("id");
            System.out.println("missing-id rejected="+report.getMessages());
        }
    }
    @Test void fixedBusinessContractParsesAndResolvesReferences() throws Exception {
        assertThat(ToolchainContractValidator.load("business.openapi.yaml")).isNotNull();
        System.out.println("business OpenAPI3.0.3 parsed/resolved; business HTTP unverified");
    }
}
