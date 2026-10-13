package io.github.abdellahjebar.paraphe.identity.infrastructure.security;

import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

/**
 * Spring's default error body repeats the request path. On a review link the path is the secret, so
 * it must never be echoed back, logged by a client, or stored by an intermediary.
 */
@Component
class NoPathErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        Map<String, Object> attributes = super.getErrorAttributes(webRequest, options);
        attributes.remove("path");
        return attributes;
    }
}
