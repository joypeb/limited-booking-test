package com.limitedbooking.backend.support;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.math.BigInteger;

/** 제품 코드와 분리된 고정 업무 계약과 US1 응답 의미 검사. */
public final class ApiContractValidator {
    private static final Set<String> OPERATIONS=Set.of("POST /api/resources","GET /api/resources/{id}",
            "POST /api/reservations","GET /api/reservations/{id}","POST /api/reservations/{id}/cancel");
    private static final ObjectMapper JSON=new ObjectMapper();
    private final OpenAPI api;
    private final OpenApiInteractionValidator validator;
    private ApiContractValidator(OpenAPI api) {
        this.api=api;
        validator=OpenApiInteractionValidator.createFor(api).build();
    }
    public static ApiContractValidator load(String resource) throws Exception {
        try(var in=ApiContractValidator.class.getClassLoader().getResourceAsStream(resource)) {
            if(in==null) throw new IllegalArgumentException("계약 리소스 없음");
            return parse(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    static ApiContractValidator parse(String yaml) {
        var options=new ParseOptions(); options.setResolve(true); options.setResolveFully(true);
        var result=new OpenAPIV3Parser().readContents(yaml,null,options);
        var api=result.getOpenAPI();
        if(api==null || !result.getMessages().isEmpty()) throw new IllegalArgumentException("계약 parse/ref 오류");
        if(!"3.0.3".equals(api.getOpenapi())) throw new IllegalArgumentException("OpenAPI3.0.3 필요");
        var actual=new HashSet<String>();
        if(api.getPaths()!=null) api.getPaths().forEach((path,item)->item.readOperationsMap().forEach(
                (method,operation)->actual.add(method.name()+" "+path)));
        if(!actual.equals(OPERATIONS)) throw new IllegalArgumentException("업무 operation 5개 필요");
        return new ApiContractValidator(api);
    }
    public void assertValid(String method, String path, Map<String,String> requestHeaders,
            String requestBody, int status, Map<String,String> responseHeaders, String responseBody) {
        singletonHeaders(requestHeaders.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->List.of(e.getValue()))));
        singletonHeaders(responseHeaders.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->List.of(e.getValue()))));
        PathItem.HttpMethod httpMethod;
        try {httpMethod=PathItem.HttpMethod.valueOf(method);} catch(IllegalArgumentException e) {
            throw new AssertionError("operation: 미명시 method");
        }
        var operation=api.getPaths().entrySet().stream().filter(e->Pattern.matches(
                Arrays.stream(e.getKey().split("/",-1)).map(s->s.equals("{id}")?"[^/]+":Pattern.quote(s))
                        .collect(java.util.stream.Collectors.joining("/")),path))
                .map(e->e.getValue().readOperationsMap().get(httpMethod)).filter(Objects::nonNull).findFirst()
                .orElseThrow(()->new AssertionError("operation: 미명시 path/method"));
        if(!operation.getResponses().containsKey(Integer.toString(status))) throw new AssertionError("status: 미명시 응답");
        var request=new SimpleRequest.Builder(method,path);
        requestHeaders.forEach(request::withHeader);
        if(requestBody!=null) request.withBody(requestBody);
        var response=new SimpleResponse.Builder(status);
        responseHeaders.forEach(response::withHeader);
        if(responseBody!=null) response.withBody(responseBody);
        var failures=new ArrayList<String>();
        collect("request-schema",validator.validateRequest(request.build()),failures);
        collect("response-schema",validator.validateResponse(path,
                com.atlassian.oai.validator.model.Request.Method.valueOf(method),response.build()),failures);
        semantic(path,status,responseHeaders,responseBody,failures);
        if(!failures.isEmpty()) throw new AssertionError(String.join("; ",failures));
    }
    private static void semantic(String path,int status,Map<String,String> headers,String body,List<String> failures) {
        String expectedMedia=status<400?"application/json":"application/problem+json";
        String contentType=header(headers,"Content-Type");
        if(contentType==null || !contentType.split(";",2)[0].trim().equalsIgnoreCase(expectedMedia))
            violation(failures,"media-type","/headers/Content-Type");
        if(status==401) {
            String challenge=header(headers,"WWW-Authenticate");
            if(challenge==null || !challenge.matches("(?i)Basic(?:[ \t]+.*)?"))
                violation(failures,"basic-challenge","/headers/WWW-Authenticate");
        }
        if(status==429) {
            String retry=header(headers,"Retry-After");
            if(retry==null || !retry.matches("[0-9]+") || new BigInteger(retry).signum()<=0)
                violation(failures,"positive-integer","/headers/Retry-After");
        }
        if(status==201 && path.equals("/api/reservations")) {
            String replay=header(headers,"Idempotent-Replayed");
            if(!"false".equals(replay) && !"true".equals(replay))
                violation(failures,"replay-boolean","/headers/Idempotent-Replayed");
        }
        JsonNode json;
        try { json=body==null?null:JSON.readTree(body); }
        catch(JsonProcessingException malformed) {
            violation(failures,"json","/body"); return;
        }
        if(json==null || !json.isObject()) return; // 구조 결함은 response-schema 보고서가 담당한다.
        if(status==201) {
            String location=header(headers,"Location");
            if(location==null || !json.path("id").isTextual() || !location.equals(path+"/"+json.path("id").textValue()))
                violation(failures,"location-id","/headers/Location");
        }
        if(status>=400) return; // 오류 본문의 의미 계약은 US2에서 검증한다.
        if(path.startsWith("/api/resources")) {
            var name=json.get("name");
            if(name!=null && name.isTextual()) {
                String value=name.textValue();
                int length=value.codePointCount(0,value.length());
                if(!value.equals(value.strip()) || length<1 || length>100)
                    violation(failures,"normalized-name","/body/name");
            }
            var capacity=json.get("capacity");var available=json.get("available");
            if(capacity!=null && available!=null && capacity.isIntegralNumber() && available.isIntegralNumber()
                    && available.bigIntegerValue().compareTo(capacity.bigIntegerValue())>0)
                violation(failures,"available-capacity","/body/available");
        } else {
            utcMicros(json.get("createdAt"),"/body/createdAt",failures);
            var cancelled=json.get("cancelledAt");
            if(status!=201) {
                String state=json.path("status").asText();
                if("CONFIRMED".equals(state) && cancelled!=null && !cancelled.isNull())
                    violation(failures,"confirmed-null","/body/cancelledAt");
                if("CANCELLED".equals(state) && (cancelled==null || cancelled.isNull()))
                    violation(failures,"cancelled-time","/body/cancelledAt");
                if(cancelled!=null && !cancelled.isNull()) utcMicros(cancelled,"/body/cancelledAt",failures);
            }
        }
    }
    private static void utcMicros(JsonNode node,String location,List<String> failures) {
        if(node==null || !node.isTextual()) return; // 누락/타입은 스키마가 거부한다.
        String value=node.textValue();
        try {
            var time=OffsetDateTime.parse(value);
            if(!value.endsWith("Z") || !time.getOffset().equals(ZoneOffset.UTC) || time.getNano()%1000!=0)
                violation(failures,"utc-micros",location);
        } catch(DateTimeParseException malformed) { violation(failures,"utc-micros",location); }
    }
    private static String header(Map<String,String> headers,String name) {
        return headers.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }
    private static void violation(List<String> failures,String key,String location) {
        failures.add("semantic:"+key+":"+location);
    }
    public record HttpExchange(String method, String path, Map<String,String> requestHeaders,
            String requestBody, int status, Map<String,String> responseHeaders, String responseBody) {
        public HttpExchange { requestHeaders=Map.copyOf(requestHeaders); responseHeaders=Map.copyOf(responseHeaders); }
        public void assertValid(ApiContractValidator validator) {
            validator.assertValid(method,path,requestHeaders,requestBody,status,responseHeaders,responseBody);
        }
    }
    public record ValidationFailure(String stage, String key, String location) { }
    public record ExpectedRequestViolations(List<ValidationFailure> library, List<ValidationFailure> raw,
            int status, String code) {
        public ExpectedRequestViolations { library=List.copyOf(library); raw=List.copyOf(raw); }
    }
    public record ScenarioExpectation(int status, String code, Map<String,String> initialFields,
            String location, Boolean replayed, Long decisionPttl) {
        public ScenarioExpectation { initialFields=Map.copyOf(initialFields); }
    }
    public static Map<String,String> singletonHeaders(Map<String,List<String>> headers) {
        var result=new LinkedHashMap<String,String>();
        var seen=new HashSet<String>();
        var singleton=Set.of("content-type","content-length","authorization","idempotency-key",
                "location","idempotent-replayed","www-authenticate","retry-after");
        headers.forEach((name,values)->{
            String lower=name.toLowerCase(Locale.ROOT);
            if(singleton.contains(lower) && (values.size()!=1 || !seen.add(lower)))
                throw new AssertionError("raw:duplicate-singleton:"+lower);
            result.put(name,String.join(", ",values));
        });
        return Map.copyOf(result);
    }
    private static void collect(String stage, ValidationReport report, List<String> failures) {
        report.getMessages().stream().filter(m->m.getLevel()==ValidationReport.Level.ERROR)
                .forEach(m->failures.add(stage+":"+m.getKey()));
    }
}
