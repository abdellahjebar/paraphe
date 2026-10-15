package io.github.abdellahjebar.paraphe.api;

import io.github.abdellahjebar.paraphe.shared.web.ErrorCode;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The header of the generated contract. The routes themselves come from the controllers. */
@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI paraphe() {
        return new OpenAPI().components(new Components().addSchemas("Problem", problem())).info(new Info()
                .title("Paraphe API")
                .version("0.1.0")
                .description("""
                        Tracking of the approval of construction execution documents.

                        /api/** is for the contractor (cookie session, CSRF token in X-XSRF-TOKEN).
                        /r/{token}/** is for reviewers, who have no account: the token is the credential.
                        See docs/api/conventions.md for errors and conventions."""));
    }

    /** The body of every error. Clients choose their wording from {@code code}, never from the text. */
    private static Schema<?> problem() {
        StringSchema code = new StringSchema();
        code.setEnum(Arrays.stream(ErrorCode.values()).map(Enum::name).toList());
        code.setDescription("Stable machine-readable code; the only field a client should branch on.");

        ObjectSchema fieldError = new ObjectSchema();
        fieldError.addProperty("field", new StringSchema());
        fieldError.addProperty("code", new StringSchema());

        ObjectSchema problem = new ObjectSchema();
        problem.addProperty("type", new StringSchema());
        problem.addProperty("title", new StringSchema());
        problem.addProperty("status", new IntegerSchema());
        problem.addProperty("detail", new StringSchema());
        problem.addProperty("code", code);
        problem.addProperty("errors", new ArraySchema().items(fieldError));
        problem.addProperty("reason", new StringSchema().description("For WEAK_PASSWORD: why."));
        problem.addProperty("limit", new IntegerSchema().description("For a length reason: the bound."));
        problem.addProperty("correlationId", new StringSchema());
        problem.addRequiredItem("status");
        problem.addRequiredItem("code");
        return problem;
    }
}
