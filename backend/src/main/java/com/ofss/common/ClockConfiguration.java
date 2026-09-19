package com.ofss.common;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }
}