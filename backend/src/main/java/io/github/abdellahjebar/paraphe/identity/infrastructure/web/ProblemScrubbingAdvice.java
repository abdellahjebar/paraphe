package io.github.abdellahjebar.paraphe.identity.infrastructure.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Keeps the request path out of every error body.
 *
 * <p>Spring fills a problem's {@code instance} with the request path, and some framework errors put
 * it in {@code detail} as well. On a review link the path <em>is</em> the secret token, so an error
 * page must never repeat it. Nothing a client needs is lost: the status and title say what happened.
 */
@RestControllerAdvice
class ProblemScrubbingAdvice implements ResponseBodyAdvice<Object> {

    private static final String REVIEWER_PREFIX = "/r/";

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ProblemDetail problem) {
            problem.setInstance(null);
            String path = request.getURI().getPath();
            // Always on review links; elsewhere only when the text would repeat the path.
            if (path.startsWith(REVIEWER_PREFIX) || repeatsPath(problem.getDetail(), path)) {
                problem.setDetail(null);
            }
        }
        return body;
    }

    private static boolean repeatsPath(String detail, String path) {
        if (detail == null || path == null || path.length() < 2) {
            return false;
        }
        String withoutLeadingSlash = path.startsWith("/") ? path.substring(1) : path;
        return detail.contains(path) || detail.contains(withoutLeadingSlash);
    }
}
