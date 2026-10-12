package io.github.abdellahjebar.paraphe.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ClockConfig {

    /** Injected wherever "now" is needed, so tests can replace it with a fixed clock. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
