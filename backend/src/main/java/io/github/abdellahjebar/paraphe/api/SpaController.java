package io.github.abdellahjebar.paraphe.api;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Routes of the web interface. They all answer with the same entry page, and the interface decides
 * what to show. A reviewer's link ({@code /relecture/{token}}) is one of them.
 *
 * <p>The page itself carries no secret: the token in the path is only read by the interface, which
 * then calls the review API. Error pages never repeat it (see ProblemScrubbingAdvice).
 */
@Controller
@Hidden
class SpaController {

    @GetMapping({"/", "/connexion", "/projets", "/projets/**", "/relecture/{token}"})
    String entryPage() {
        return "forward:/index.html";
    }
}
