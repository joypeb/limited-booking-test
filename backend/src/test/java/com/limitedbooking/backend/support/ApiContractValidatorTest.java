package com.limitedbooking.backend.support;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
                "{\"name\":\"대상\",\"capacity\":1}",201,resourceCreatedHeaders(),RESOURCE);
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
        var exchange=new ApiContractValidator.HttpExchange("POST",path,headers,raw,201,resourceCreatedHeaders(),RESOURCE);
        headers.clear();
        assertThat(exchange.path()).isEqualTo(path);
        assertThat(exchange.requestBody()).isEqualTo(raw);
        assertThat(exchange.requestHeaders()).containsKey("cOnTeNt-TyPe");
        var absent=new ApiContractValidator.HttpExchange("GET",path,Map.of(),null,200,JSON,RESOURCE);
        var empty=new ApiContractValidator.HttpExchange("GET",path,Map.of(),"",200,JSON,RESOURCE);
        assertThat(absent.requestBody()).isNull(); assertThat(empty.requestBody()).isEmpty();
        ApiContractValidator.load("business.openapi.yaml").assertValid("POST","/api/resources",
                exchange.requestHeaders(),raw,201,Map.of("content-type","application/json; charset=UTF-8",
                        "Location","/api/resources/"+ID),RESOURCE);
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

    private static final String RESERVATION_ID="20000000-0000-0000-0000-000000000001";
    private static final String TIME="2026-10-23T00:00:00.123456Z";
    private static final String CREATED="{\"id\":\""+RESERVATION_ID+"\",\"resourceId\":\""+ID
            +"\",\"status\":\"CONFIRMED\",\"createdAt\":\""+TIME+"\"}";
    private static final String CURRENT=CREATED.substring(0,CREATED.length()-1)+",\"cancelledAt\":null}";
    private static final String CANCELLED=CURRENT.replace("CONFIRMED","CANCELLED")
            .replace("\"cancelledAt\":null","\"cancelledAt\":\"2026-10-23T00:01:00.123456Z\"");

    private static Map<String,String> resourceCreatedHeaders() {
        return Map.of("Content-Type","application/json","Location","/api/resources/"+ID);
    }
    private static ApiContractValidator.HttpExchange resource() {
        return new ApiContractValidator.HttpExchange("GET","/api/resources/"+ID,Map.of(),null,200,JSON,RESOURCE);
    }
    private static ApiContractValidator.HttpExchange resourceCreated() {
        return new ApiContractValidator.HttpExchange("POST","/api/resources",JSON,
                "{\"name\":\"대상\",\"capacity\":1}",201,resourceCreatedHeaders(),RESOURCE);
    }
    private static ApiContractValidator.HttpExchange created() {
        return new ApiContractValidator.HttpExchange("POST","/api/reservations",
                Map.of("Content-Type","application/json","Idempotency-Key","unit_key"),
                "{\"resourceId\":\""+ID+"\"}",201,
                Map.of("Content-Type","application/json","Location","/api/reservations/"+RESERVATION_ID,
                        "Idempotent-Replayed","false"),CREATED);
    }
    private static ApiContractValidator.HttpExchange current(String body) {
        return new ApiContractValidator.HttpExchange("GET","/api/reservations/"+RESERVATION_ID,
                Map.of(),null,200,JSON,body);
    }
    private static ApiContractValidator.HttpExchange problem(int status) {
        String code=status==401?"UNAUTHENTICATED":"RATE_LIMITED";
        var headers=new LinkedHashMap<>(Map.of("Content-Type","application/problem+json"));
        headers.put(status==401?"WWW-Authenticate":"Retry-After",status==401?"Basic realm=\"reservation-local\"":"60");
        return new ApiContractValidator.HttpExchange("POST","/api/reservations",created().requestHeaders(),
                created().requestBody(),status,headers,
                "{\"type\":\"urn:reservation:error:test\",\"title\":\"요청 거부\",\"status\":"+status
                        +",\"detail\":\"요청을 처리할 수 없습니다.\",\"instance\":\"/api/reservations\",\"code\":\""+code+"\"}");
    }
    private static ApiContractValidator.HttpExchange body(ApiContractValidator.HttpExchange e,String body) {
        return new ApiContractValidator.HttpExchange(e.method(),e.path(),e.requestHeaders(),e.requestBody(),
                e.status(),e.responseHeaders(),body);
    }
    private static ApiContractValidator.HttpExchange header(ApiContractValidator.HttpExchange e,String name,String value) {
        var headers=new LinkedHashMap<>(e.responseHeaders());
        if(value==null) headers.remove(name); else headers.put(name,value);
        return new ApiContractValidator.HttpExchange(e.method(),e.path(),e.requestHeaders(),e.requestBody(),
                e.status(),headers,e.responseBody());
    }
    private static String omit(String body,String field) {
        return body.replaceFirst("\""+field+"\":(?:\"[^\"]*\"|null|[0-9]+),?","").replace(",}","}");
    }
    private static String extra(String body,String field) {
        return body.substring(0,body.length()-1)+","+field+"}";
    }
    private record Defect(String name,ApiContractValidator.HttpExchange baseline,
            ApiContractValidator.HttpExchange broken,String stage) {
        @Override public String toString() { return name; }
    }
    private static void changed(List<Defect> cases,String name,ApiContractValidator.HttpExchange baseline,
            String broken,String stage) {
        cases.add(new Defect(name,baseline,body(baseline,broken),stage));
    }

    // 라이브러리 스키마가 담당하는 제약. 정상 사본을 먼저 통과시켜 다른 결함의 오탐을 막는다.
    @ParameterizedTest(name="{0}") @MethodSource("schemaDefects")
    void schemaRejectsOneResponseDefect(Defect defect) throws Exception {
        assertSingleDefect(defect);
    }
    static Stream<Defect> schemaDefects() {
        var cases=new ArrayList<Defect>();
        for(var e:List.of(resource(),created(),current(CURRENT))) {
            String kind=e.path()+"/"+e.status();
            String[] fields=e.status()==201?new String[]{"id","resourceId","status","createdAt"}:
                    e.path().startsWith("/api/resources")?new String[]{"id","name","capacity","available"}:
                            new String[]{"id","resourceId","status","createdAt","cancelledAt"};
            for(String field:fields) changed(cases,kind+" missing "+field,e,omit(e.responseBody(),field),"response-schema");
            changed(cases,kind+" invalid id UUID",e,e.responseBody().replace(
                    e.path().startsWith("/api/resources")?ID:RESERVATION_ID,"not-a-uuid"),"response-schema");
            changed(cases,kind+" additional field",e,extra(e.responseBody(),"\"unexpected\":true"),"response-schema");
        }
        for(String field:List.of("capacity","available")) {
            for(String value:List.of("-1","1001")) changed(cases,field+" range "+value,resource(),
                    RESOURCE.replace("\""+field+"\":1","\""+field+"\":"+value),"response-schema");
        }
        for(String name:List.of("","가".repeat(101))) changed(cases,"name length "+name.length(),resource(),
                RESOURCE.replace("대상",name),"response-schema");
        changed(cases,"name number",resource(),RESOURCE.replace("\"name\":\"대상\"","\"name\":1"),"response-schema");
        changed(cases,"capacity fractional",resource(),RESOURCE.replace("\"capacity\":1","\"capacity\":1.5"),"response-schema");
        changed(cases,"capacity string",resource(),RESOURCE.replace("\"capacity\":1","\"capacity\":\"1\""),"response-schema");
        changed(cases,"available string",resource(),RESOURCE.replace("\"available\":1","\"available\":\"1\""),"response-schema");
        changed(cases,"created resourceId UUID",created(),CREATED.replace(ID,"invalid"),"response-schema");
        changed(cases,"created status CANCELLED",created(),CREATED.replace("CONFIRMED","CANCELLED"),"response-schema");
        changed(cases,"created cancelledAt forbidden",created(),extra(CREATED,"\"cancelledAt\":null"),"response-schema");
        changed(cases,"current unknown status",current(CURRENT),CURRENT.replace("CONFIRMED","UNKNOWN"),"response-schema");
        changed(cases,"malformed createdAt",created(),CREATED.replace(TIME,"not-a-time"),"response-schema");
        cases.add(new Defect("success media",resource(),header(resource(),"Content-Type","text/plain"),"response-schema"));
        cases.add(new Defect("problem media",problem(401),header(problem(401),"Content-Type","application/json"),"response-schema"));
        return cases.stream();
    }

    // 스키마 선언만으로 부족한 의미 검사. T009의 RED 확인 후 T010에서 거부를 검증했다.
    @ParameterizedTest(name="{0}") @MethodSource("semanticDefects")
    void semanticRulesRejectOneResponseDefect(Defect defect) throws Exception {
        assertSingleDefect(defect);
    }
    static Stream<Defect> semanticDefects() {
        var cases=new ArrayList<Defect>();
        changed(cases,"available exceeds capacity",resource(),RESOURCE.replace("\"available\":1","\"available\":2"),"semantic");
        changed(cases,"resource name not normalized",resource(),RESOURCE.replace("대상"," 대상 "),"semantic");
        changed(cases,"CONFIRMED requires null",current(CURRENT),CURRENT.replace("\"cancelledAt\":null","\"cancelledAt\":\""+TIME+"\""),"semantic");
        changed(cases,"CANCELLED requires timestamp",current(CANCELLED),CANCELLED.replace("\"cancelledAt\":\"2026-10-23T00:01:00.123456Z\"","\"cancelledAt\":null"),"semantic");
        for(String time:List.of("2026-10-23T09:00:00.123456+09:00","2026-10-23T00:00:00.123456+00:00",
                "2026-10-23T00:00:00.123456001Z")) {
            changed(cases,"createdAt "+time,created(),CREATED.replace(TIME,time),"semantic");
            changed(cases,"cancelledAt "+time,current(CANCELLED),CANCELLED.replace("2026-10-23T00:01:00.123456Z",time),"semantic");
        }
        for(var e:List.of(resourceCreated(),created(),problem(401),problem(429))) {
            var names=e.status()==401?List.of("WWW-Authenticate"):e.status()==429?List.of("Retry-After"):
                    e.path().equals("/api/resources")?List.of("Location"):List.of("Location","Idempotent-Replayed");
            for(String name:names) cases.add(new Defect("missing "+name+" "+e.path(),e,header(e,name,null),"semantic"));
        }
        for(var e:List.of(resourceCreated(),created())) cases.add(new Defect("Location wrong id "+e.path(),e,
                header(e,"Location",e.path()+"/30000000-0000-0000-0000-000000000001"),"semantic"));
        cases.add(new Defect("Replay invalid",created(),header(created(),"Idempotent-Replayed","TRUE"),""));
        cases.add(new Defect("non Basic challenge",problem(401),header(problem(401),"WWW-Authenticate","Bearer"),"semantic"));
        for(String retry:List.of("0","1.5","later")) cases.add(new Defect("Retry-After "+retry,problem(429),
                header(problem(429),"Retry-After",retry),""));
        return cases.stream();
    }
    // 헤더 값 타입은 라이브러리의 변환 정책에 따라 schema/semantic 중 먼저 거부될 수 있다.
    // 빈 stage는 그 두 단계만 허용하며 operation 등 다른 실패를 탐지로 세지 않는다.
    private void assertSingleDefect(Defect defect) throws Exception {
        var validator=ApiContractValidator.load("business.openapi.yaml");
        assertThatCode(()->defect.baseline().assertValid(validator)).as("정상 대조: %s",defect.name()).doesNotThrowAnyException();
        var failure=catchThrowable(()->defect.broken().assertValid(validator));
        assertThat(failure).as("단일 결함: %s",defect.name()).isInstanceOf(AssertionError.class);
        if(defect.stage().isEmpty()) {
            assertThat(failure.getMessage()).containsAnyOf("response-schema", "semantic");
        } else {
            assertThat(failure).hasMessageContaining(defect.stage());
        }
    }

    @ParameterizedTest @MethodSource("validResponseFixtures")
    void validResponseFixturePasses(ApiContractValidator.HttpExchange exchange) throws Exception {
        assertThatCode(()->exchange.assertValid(ApiContractValidator.load("business.openapi.yaml"))).doesNotThrowAnyException();
    }
    static Stream<ApiContractValidator.HttpExchange> validResponseFixtures() {
        var fixtures=new ArrayList<>(List.of(resource(),resourceCreated(),created(),current(CURRENT),current(CANCELLED),problem(401),problem(429)));
        for(String time:List.of("2026-10-23T00:00:00Z","2026-10-23T00:00:00.123Z","2026-10-23T00:00:00.123456000Z"))
            fixtures.add(body(created(),CREATED.replace(TIME,time)));
        fixtures.add(body(resource(),RESOURCE.replace("\"capacity\":1","\"capacity\":0").replace("\"available\":1","\"available\":0")));
        fixtures.add(body(resource(),RESOURCE.replace("\"capacity\":1","\"capacity\":1000").replace("\"available\":1","\"available\":1000")));
        fixtures.add(body(resource(),RESOURCE.replace("대상","😀".repeat(100))));
        fixtures.add(body(resource(),RESOURCE.replace("대상","\u00a0")));
        fixtures.add(header(created(),"Idempotent-Replayed","true"));
        fixtures.add(header(resource(),"Content-Type","application/json; charset=UTF-8"));
        fixtures.add(header(problem(401),"Content-Type","application/problem+json; charset=UTF-8"));
        fixtures.add(header(resource(),"X-Trace-Id","unit-fixture"));
        return fixtures.stream();
    }
    private static String invalidProblem() {
        return "{\"type\":\"about:blank\",\"title\":\"입력 오류\",\"status\":400,\"detail\":\"잘못된 요청입니다\",\"instance\":\"/api/resources\",\"code\":\"INVALID_REQUEST\"}";
    }
    private static ApiContractValidator.ValidationFailure required() {
        return new ApiContractValidator.ValidationFailure("request-schema","validation.request.body.schema.required","/body");
    }
    private static ApiContractValidator.ExpectedRequestViolations missingFields(List<ApiContractValidator.ValidationFailure> failures) {
        return new ApiContractValidator.ExpectedRequestViolations(failures,List.of(),400,"INVALID_REQUEST");
    }
    @Test void exactExpectedInvalidRequestPassesAndBaseRemainsStrict() throws Exception {
        var base=ApiContractValidator.load("business.openapi.yaml");
        var expected=base.expectInvalidRequest(missingFields(List.of(required(),required())));
        assertThatCode(()->expected.assertValid("POST","/api/resources",JSON,"{}",400,
                Map.of("Content-Type","application/problem+json"),invalidProblem())).doesNotThrowAnyException();
        assertThatThrownBy(()->base.assertValid("POST","/api/resources",JSON,"{}",400,
                Map.of("Content-Type","application/problem+json"),invalidProblem())).isInstanceOf(AssertionError.class);
    }
    @Test void missingExtraDuplicateAndUnrelatedExpectedViolationsFail() throws Exception {
        var base=ApiContractValidator.load("business.openapi.yaml");
        for(var expected:List.of(List.of(required()),List.of(required(),required(),required()),
                List.of(new ApiContractValidator.ValidationFailure("request-schema","wrong","/body"),required()),
                List.of(new ApiContractValidator.ValidationFailure("request-schema",required().key(),"/body/name"),required()))) {
            assertThatThrownBy(()->base.expectInvalidRequest(missingFields(expected)).assertValid("POST","/api/resources",JSON,
                    "{}",400,Map.of("Content-Type","application/problem+json"),invalidProblem())).isInstanceOf(AssertionError.class);
        }
        assertThatThrownBy(()->base.expectInvalidRequest(missingFields(List.of(required(),required()))).assertValid(
                "POST","/api/resources",JSON,"{\"name\":\"정상\",\"capacity\":1}",400,
                Map.of("Content-Type","application/problem+json"),invalidProblem())).isInstanceOf(AssertionError.class);
    }
    @Test void rawDuplicateMustBeObservedAndMatchedExactly() throws Exception {
        String duplicate="{\"name\":\"앞\",\"name\":\"뒤\",\"capacity\":1}";
        var base=ApiContractValidator.load("business.openapi.yaml");
        assertThatThrownBy(()->base.assertValid("POST","/api/resources",JSON,duplicate,400,
                Map.of("Content-Type","application/problem+json"),invalidProblem())).isInstanceOf(AssertionError.class).hasMessageContaining("raw");
        var raw=new ApiContractValidator.ValidationFailure("raw","duplicate-json","/body/name");
        var expected=new ApiContractValidator.ExpectedRequestViolations(List.of(),List.of(raw),400,"INVALID_REQUEST");
        assertThatCode(()->base.expectInvalidRequest(expected).assertValid("POST","/api/resources",JSON,duplicate,400,
                Map.of("Content-Type","application/problem+json"),invalidProblem())).doesNotThrowAnyException();
        assertThatThrownBy(()->base.expectInvalidRequest(new ApiContractValidator.ExpectedRequestViolations(List.of(),
                List.of(raw,raw),400,"INVALID_REQUEST")).assertValid("POST","/api/resources",JSON,duplicate,400,
                Map.of("Content-Type","application/problem+json"),invalidProblem())).isInstanceOf(AssertionError.class);
    }
    @Test void invalidRequestNeverSkipsResponseSchemaOrSemantic() throws Exception {
        var v=ApiContractValidator.load("business.openapi.yaml").expectInvalidRequest(missingFields(List.of(required(),required())));
        assertThatThrownBy(()->v.assertValid("POST","/api/resources",JSON,"{}",400,
                Map.of("Content-Type","application/problem+json"),invalidProblem().replace("about:blank","not a URI").replace("\"title\":\"입력 오류\",","")))
                .isInstanceOf(AssertionError.class).hasMessageContaining("response-schema").hasMessageContaining("semantic");
    }
    @Test void unknownKeyAndMissingContextCannotBeWhitelistedEvenWithExactSentinel() {
        for(var message:List.of(
                com.atlassian.oai.validator.report.ValidationReport.Message.create("fixture.unknown","비밀값을 출력하면 안 됨")
                        .withContext(com.atlassian.oai.validator.report.ValidationReport.MessageContext.create()
                                .in(com.atlassian.oai.validator.report.ValidationReport.MessageContext.Location.REQUEST).build()).build(),
                com.atlassian.oai.validator.report.ValidationReport.Message.create("validation.request.body.schema.type","비밀값을 출력하면 안 됨").build())) {
            var report=com.atlassian.oai.validator.report.ValidationReport.singleton(message);
            var sentinel=ApiContractValidator.normalizeRequest(report);
            assertThat(ApiContractValidator.compareRequestReport(report,sentinel)).isNotEmpty();
            assertThat(ApiContractValidator.compareRequestReport(report,sentinel).toString()).contains(message.getKey()).doesNotContain("비밀값");
        }
    }
    @ParameterizedTest(name="오류응답 훼손 {index}") @org.junit.jupiter.params.provider.ValueSource(strings={"different-path","SELECT password FROM users",
            TestData.ADMIN_PASSWORD,"java.lang.IllegalStateException at com.example.Test.run(Test.java:12)"})
    void problemInstanceAndSensitiveDetailsAreRejected(String replacement) throws Exception {
        var v=ApiContractValidator.load("business.openapi.yaml");
        var baseline=problem(401);baseline.assertValid(v);
        {
            String broken=replacement.equals("different-path")?baseline.responseBody().replace("\"instance\":\"/api/reservations\"","\"instance\":\"/api/resources\""):
                    baseline.responseBody().replace("요청을 처리할 수 없습니다.",replacement);
            assertThatThrownBy(()->body(baseline,broken).assertValid(v)).isInstanceOf(AssertionError.class).hasMessageContaining("semantic");
        }
    }
    // 스키마는 title·detail을 string으로만 정의하므로 빈 문자열·공백만 있는 값은 의미 검사가 거부해야 한다.
    @ParameterizedTest(name="Problem {0}={1}")
    @org.junit.jupiter.params.provider.CsvSource(value={"title,''","title,'   '","detail,''","detail,'   '"})
    void problemTitleAndDetailMustNotBeBlank(String field,String blank) throws Exception {
        var v=ApiContractValidator.load("business.openapi.yaml");
        var baseline=problem(401);baseline.assertValid(v);
        String original=field.equals("title")?"\"title\":\"요청 거부\"":"\"detail\":\"요청을 처리할 수 없습니다.\"";
        String broken=baseline.responseBody().replace(original,"\""+field+"\":\""+blank+"\"");
        assertThat(broken).isNotEqualTo(baseline.responseBody());
        assertThatThrownBy(()->body(baseline,broken).assertValid(v)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("semantic:problem-text").hasMessageContaining("/body/"+field);
    }
    @Test void positiveRetryAfterMustMatchIndependentDecisionPttl() throws Exception {
        var v=ApiContractValidator.load("business.openapi.yaml");
        var baseline=problem(429);
        assertThatCode(()->v.assertRetryAfter(baseline,60000)).doesNotThrowAnyException();
        assertThatThrownBy(()->v.assertRetryAfter(header(baseline,"Retry-After","59"),60000))
                .isInstanceOf(AssertionError.class).hasMessageContaining("decision-pttl");
    }
    private static ApiContractValidator.ScenarioExpectation creationExpectation(boolean replayed) {
        return new ApiContractValidator.ScenarioExpectation(201,null,
                Map.of("id",RESERVATION_ID,"resourceId",ID,"status","CONFIRMED","createdAt",TIME),
                "/api/reservations/"+RESERVATION_ID,replayed,null);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"first-true","retry-false","replayed-field"})
    void scenarioDetectsValidBooleanAtWrongTimeAndChangedInitialField(String defect) throws Exception {
        var validator=ApiContractValidator.load("business.openapi.yaml");
        boolean retry=!defect.equals("first-true");
        var baseline=retry?header(created(),"Idempotent-Replayed","true"):created();
        var expected=creationExpectation(retry);
        validator.assertScenario(baseline,expected);
        var broken=defect.equals("replayed-field")?body(baseline,CREATED.replace(TIME,"2026-10-23T00:00:00.123457Z")):
                header(baseline,"Idempotent-Replayed",retry?"false":"true");
        broken.assertValid(validator); // 개별 교환으로는 유효한 값이다.
        assertThatThrownBy(()->validator.assertScenario(broken,expected)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("semantic:scenario");
    }
    @Test void scenarioComparesFieldValuesWithoutJsonWhitespaceOrOrderEquality() throws Exception {
        var reordered=body(created()," { \"createdAt\" : \""+TIME+"\", \"status\":\"CONFIRMED\", \"resourceId\":\""+ID
                +"\", \"id\":\""+RESERVATION_ID+"\" } ");
        assertThatCode(()->ApiContractValidator.load("business.openapi.yaml").assertScenario(reordered,creationExpectation(false)))
                .doesNotThrowAnyException();
    }
}
