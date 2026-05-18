package com.typedgoose.calc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class CalcConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
