package io.github.abdellahjebar.paraphe.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The header of the generated contract. The routes themselves come from the controllers. */
@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI paraphe() {
        return new OpenAPI().info(new Info()
                .title("Paraphe API")
                .version("0.1.0")
                .description("""
                        Tracking of the approval of construction execution documents.

                        /api/** is for the contractor (cookie session, CSRF token in X-XSRF-TOKEN).
                        /r/{token}/** is for reviewers, who have no account: the token is the credential.
                        See docs/api/conventions.md for errors and conventions."""));
    }
}
