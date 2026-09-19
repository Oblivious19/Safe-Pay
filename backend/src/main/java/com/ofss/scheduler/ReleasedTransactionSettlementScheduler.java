package com.ofss.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ofss.services.ReleasedTransactionSettlementProcessor;

@Component
@ConditionalOnProperty(
        prefix = "safepay.settlement-processor",
        name = "enabled",
        havingValue = "true")
public class ReleasedTransactionSettlementScheduler {

    private final ReleasedTransactionSettlementProcessor processor;
    private final SettlementProcessorProperties properties;

    public ReleasedTransactionSettlementScheduler(
            ReleasedTransactionSettlementProcessor processor,
            SettlementProcessorProperties properties) {
        this.processor = processor;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${safepay.settlement-processor.poll-interval}")
    public void settleReleasedTransactions() {
        processor.processDueTransactions(properties.batchSize());
    }
}
