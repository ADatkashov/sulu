package com.sulu.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {
    /** Injectable clock so time-dependent rules (lead time, cancellation window) are testable. */
    @Bean
    public Clock clock() { return Clock.systemUTC(); }
}
