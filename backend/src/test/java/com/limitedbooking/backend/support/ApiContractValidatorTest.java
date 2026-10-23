package com.limitedbooking.backend.support;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ApiContractValidatorTest {
    static final String ID="10000000-0000-0000-0000-000000000001";
    static final Map<String,String> JSON=Map.of("Content-Type","application/json");
    static final String RESOURCE="{\"id\":\""+ID+"\",\"name\":\"대상\",\"capacity\":1,\"available\":1}";
    @Test void missingContractIsRejected() {
        assertThatThrownBy(()->ApiContractValidator.load("absent.yaml")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void validExchangePasses() throws Exception {
        ApiContractValidator.load("business.openapi.yaml").assertValid("POST","/api/resources",JSON,
                "{\"name\":\"대상\",\"capacity\":1}",201,JSON,RESOURCE);
    }
    @Test void explicitOperationRequired() throws Exception {
        var v=ApiContractValidator.load("business.openapi.yaml");
        assertThatThrownBy(()->v.assertValid("DELETE","/api/resources/"+ID,Map.of(),null,200,JSON,RESOURCE))
                .isInstanceOf(AssertionError.class).hasMessageContaining("operation");
    }
    @Test void defaultDoesNotApproveUnspecifiedStatus() throws Exception {
        var v=ApiContractValidator.load("business.openapi.yaml");
        assertThatThrownBy(()->v.assertValid("GET","/api/resources/"+ID,Map.of(),null,500,JSON,"{}"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("status");
    }
    @Test void requestBodyReachesSchemaValidator() throws Exception {
        var v=ApiContractValidator.load("business.openapi.yaml");
        assertThatThrownBy(()->v.assertValid("POST","/api/resources",JSON,"{}",201,JSON,RESOURCE))
                .isInstanceOf(AssertionError.class).hasMessageContaining("request-schema");
    }
    @Test void responseBodyReachesSchemaValidator() throws Exception {
        var v=ApiContractValidator.load("business.openapi.yaml");
        assertThatThrownBy(()->v.assertValid("GET","/api/resources/"+ID,Map.of(),null,200,JSON,"{}"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("response-schema");
    }
    @Test void rejectsInvalidVersionReferenceAndOperationCount() throws Exception {
        String yaml;
        try(var in=getClass().getClassLoader().getResourceAsStream("business.openapi.yaml")) {
            yaml=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        final String original=yaml;
        assertThatCode(()->ApiContractValidator.parse(original)).doesNotThrowAnyException();
        for(String broken : new String[]{original.replace("3.0.3","3.0.2"),
                original.replace("#/components/schemas/ResourceView","#/components/schemas/Absent"),
                "openapi: 3.0.3\ninfo: {title: broken, version: v1}\npaths: {}\n"}) {
            assertThatThrownBy(()->ApiContractValidator.parse(broken)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void duplicateSingletonRejectedBeforeFlattening() {
        assertThatThrownBy(()->ApiContractValidator.singletonHeaders(Map.of("Location",java.util.List.of("/a","/b"))))
                .isInstanceOf(AssertionError.class).hasMessageContaining("singleton");
        assertThatThrownBy(()->ApiContractValidator.singletonHeaders(Map.of(
                "Content-Type",java.util.List.of("application/json"),"content-type",java.util.List.of("text/plain"))))
                .isInstanceOf(AssertionError.class).hasMessageContaining("singleton");
    }
    @Test void mapInterfaceAlsoRejectsCaseVariantSingletons() throws Exception {
        var v=ApiContractValidator.load("business.openapi.yaml");
        assertThatThrownBy(()->v.assertValid("GET","/api/resources/"+ID,Map.of(),null,200,
                Map.of("Content-Type","application/json","content-type","application/json"),RESOURCE))
                .isInstanceOf(AssertionError.class).hasMessageContaining("singleton");
    }
    @Test void exchangePreservesNullEmptyRawPathBodiesAndHeaders() throws Exception {
        String raw=" { \"name\": \"대상\", \"capacity\": 1 } ";
        String path="/api/resources/%31";
        var headers=new java.util.HashMap<>(Map.of("cOnTeNt-TyPe","application/json; charset=UTF-8"));
        var exchange=new ApiContractValidator.HttpExchange("POST",path,headers,raw,201,JSON,RESOURCE);
        headers.clear();
        assertThat(exchange.path()).isEqualTo(path);
        assertThat(exchange.requestBody()).isEqualTo(raw);
        assertThat(exchange.requestHeaders()).containsKey("cOnTeNt-TyPe");
        var absent=new ApiContractValidator.HttpExchange("GET",path,Map.of(),null,200,JSON,RESOURCE);
        var empty=new ApiContractValidator.HttpExchange("GET",path,Map.of(),"",200,JSON,RESOURCE);
        assertThat(absent.requestBody()).isNull(); assertThat(empty.requestBody()).isEmpty();
        ApiContractValidator.load("business.openapi.yaml").assertValid("POST","/api/resources",
                exchange.requestHeaders(),raw,201,Map.of("content-type","application/json; charset=UTF-8"),RESOURCE);
    }
    @Test void expectationCollectionsAreImmutableAndKeepMultiplicity() {
        var violation=new ApiContractValidator.ValidationFailure("request-schema","exact.key","/name");
        var mutable=new java.util.ArrayList<>(java.util.List.of(violation,violation));
        var expected=new ApiContractValidator.ExpectedRequestViolations(mutable,java.util.List.of(),400,"INVALID_REQUEST");
        mutable.clear(); assertThat(expected.library()).containsExactly(violation,violation);
        assertThatThrownBy(()->expected.library().clear()).isInstanceOf(UnsupportedOperationException.class);
        var fields=new java.util.HashMap<>(Map.of("id",ID));
        var scenario=new ApiContractValidator.ScenarioExpectation(201,null,fields,"/api/resources/"+ID,false,null);
        fields.clear(); assertThat(scenario.initialFields()).containsEntry("id",ID);
    }
}
