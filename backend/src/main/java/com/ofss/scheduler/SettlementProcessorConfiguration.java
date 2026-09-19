package com.ofss.scheduler;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SettlementProcessorProperties.class)
public class SettlementProcessorConfiguration {
}
