package com.ofss.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ofss.services.ProtectedTransactionReleaseService;

@Component
@ConditionalOnProperty(
        prefix = "safepay.protection-scheduler",
        name = "enabled",
        havingValue = "true")
public class ProtectedTransactionScheduler {

    private final ProtectedTransactionReleaseService releaseService;
    private final ProtectionSchedulerProperties properties;

    public ProtectedTransactionScheduler(
            ProtectedTransactionReleaseService releaseService,
            ProtectionSchedulerProperties properties) {

        this.releaseService = releaseService;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString =
                    "${safepay.protection-scheduler.poll-interval}")
    public void releaseExpiredTransactions() {
        releaseService.releaseDueTransactions(properties.batchSize());
    }
}
