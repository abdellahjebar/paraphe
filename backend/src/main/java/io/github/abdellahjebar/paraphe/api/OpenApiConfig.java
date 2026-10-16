package io.github.abdellahjebar.paraphe.api;

import io.github.abdellahjebar.paraphe.identity.domain.WeakPasswordException;
import io.github.abdellahjebar.paraphe.shared.web.ErrorCode;
import io.github.abdellahjebar.paraphe.shared.web.FieldErrorCode;
import io.swagger.v3.oas.models.Components;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
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
        StringSchema fieldCode = new StringSchema();
        fieldCode.setEnum(Arrays.stream(FieldErrorCode.values()).map(Enum::name).toList());
        fieldError.addProperty("code", fieldCode);
        fieldError.addRequiredItem("field");
        fieldError.addRequiredItem("code");

        StringSchema reason = new StringSchema();
        reason.setDescription("For WEAK_PASSWORD: why.");
        reason.setEnum(Arrays.stream(WeakPasswordException.Reason.values()).map(Enum::name).toList());

        ObjectSchema problem = new ObjectSchema();
        problem.addProperty("type", new StringSchema());
        problem.addProperty("title", new StringSchema());
        problem.addProperty("status", new IntegerSchema());
        problem.addProperty("detail", new StringSchema());
        problem.addProperty("code", code);
        problem.addProperty("errors", new ArraySchema().items(fieldError));
        problem.addProperty("reason", reason);
        problem.addProperty("limit", new IntegerSchema().description("For a length reason: the bound."));
        problem.addProperty("correlationId", new StringSchema());
        problem.addRequiredItem("status");
        problem.addRequiredItem("code");
        return problem;
    }

    /**
     * Properties that can be null in a response. Every other property of a response is always present, so a
     * client can rely on it. This table is the one place that says so; a name that no longer exists fails the
     * contract generation instead of silently turning a field back into "optional".
     */
    private static final Map<String, Set<String>> NULLABLE = Map.ofEntries(
            Map.entry("WorkPackageView", Set.of("label")),
            Map.entry("ParticipantView", Set.of("specialty", "email", "phone")),
            Map.entry("DocumentSummary", Set.of("workPackage", "type", "latestRevision")),
            Map.entry("DocumentDetail", Set.of("workPackage", "type")),
            Map.entry("RevisionView", Set.of("externalLink")),
            Map.entry("RevisionSummary", Set.of("externalLink")),
            Map.entry("OpinionLine", Set.of("comment", "enteredBy")),
            Map.entry("ReviewerLine", Set.of("specialty", "lastOpinion", "linkStatus")),
            Map.entry("MyOpinion", Set.of("comment")),
            Map.entry("ReviewerPage", Set.of("myLastOpinion")),
            Map.entry("Row", Set.of("workPackage", "type", "revision")),
            Map.entry("ReviewerCell", Set.of("name", "role", "specialty", "statusCode", "statusLabel", "effect",
                    "answeredAt", "waitingDays", "linkStatus")));

    /** Marks every property of every response as required, except the ones listed as nullable above. */
    @Bean
    OpenApiCustomizer responsesAreComplete() {
        return openApi -> {
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            NULLABLE.keySet().forEach(name -> {
                if (!schemas.containsKey(name)) {
                    throw new IllegalStateException("NULLABLE names a schema that does not exist: " + name);
                }
            });
            schemas.forEach((name, schema) -> {
                if (name.endsWith("Request") || name.equals("Problem") || schema.getProperties() == null) {
                    return;
                }
                Map<String, Schema> properties = schema.getProperties();
                Set<String> nullable = NULLABLE.getOrDefault(name, Set.of());
                for (String property : nullable) {
                    if (!properties.containsKey(property)) {
                        throw new IllegalStateException("NULLABLE names a property that does not exist: "
                                + name + "." + property);
                    }
                }
                schema.setRequired(new ArrayList<>(properties.keySet()));
                for (String property : nullable) {
                    properties.put(property, allowingNull(properties.get(property)));
                }
            });
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema allowingNull(Schema original) {
        Schema orNull = new Schema().types(Set.of("null"));
        if (original.get$ref() != null || original.getTypes() == null || original.getTypes().isEmpty()) {
            // a reference, or a schema without a plain type: "this, or null"
            Schema wrapper = new Schema();
            wrapper.setOneOf(new ArrayList<>(java.util.List.of(original, orNull)));
            return wrapper;
        }
        Set<String> types = new java.util.LinkedHashSet<>(original.getTypes());
        types.add("null");
        original.setTypes(types);
        return original;
    }
}
