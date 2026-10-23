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

/** 제품 코드와 분리된 고정 업무 계약 검사. 의미 검사는 후속 사용자 이야기에서 추가한다. */
public final class ApiContractValidator {
    private static final Set<String> OPERATIONS=Set.of("POST /api/resources","GET /api/resources/{id}",
            "POST /api/reservations","GET /api/reservations/{id}","POST /api/reservations/{id}/cancel");
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
        if(!failures.isEmpty()) throw new AssertionError(String.join("; ",failures));
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
