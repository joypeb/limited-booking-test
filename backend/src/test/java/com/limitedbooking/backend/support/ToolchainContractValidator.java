package com.limitedbooking.backend.support;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.net.http.*;
import java.nio.charset.StandardCharsets;

public final class ToolchainContractValidator {
    private final OpenApiInteractionValidator validator;
    private ToolchainContractValidator(OpenApiInteractionValidator validator) { this.validator=validator; }
    public static ToolchainContractValidator load(String resource) throws Exception {
        try(var stream=ToolchainContractValidator.class.getClassLoader().getResourceAsStream(resource)) {
            if(stream==null) throw new IllegalArgumentException("계약 리소스 없음: "+resource);
            String yaml=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            var options=new ParseOptions();options.setResolve(true);options.setResolveFully(true);
            var parsed=new OpenAPIV3Parser().readContents(yaml,null,options);
            if(parsed.getOpenAPI()==null || !parsed.getMessages().isEmpty()) {
                throw new IllegalArgumentException("계약 parse/ref 오류: "+parsed.getMessages());
            }
            if(!"3.0.3".equals(parsed.getOpenAPI().getOpenapi())) throw new IllegalArgumentException("계약은 OpenAPI3.0.3이어야 한다");
            return new ToolchainContractValidator(OpenApiInteractionValidator.createFor(parsed.getOpenAPI()).build());
        }
    }
    public ValidationReport validate(HttpRequest request,HttpResponse<String> response) {
        if(request.bodyPublisher().isPresent()) throw new IllegalArgumentException("최소 GET fixture는 요청 본문 없음");
        var input=new SimpleRequest.Builder(request.method(),request.uri().getRawPath());
        request.headers().map().forEach(input::withHeader);
        var output=new SimpleResponse.Builder(response.statusCode()).withBody(response.body());
        response.headers().map().forEach(output::withHeader);
        return validator.validate(input.build(),output.build());
    }
}
