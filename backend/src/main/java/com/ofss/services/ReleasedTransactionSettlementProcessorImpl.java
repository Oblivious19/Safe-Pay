package com.ofss.services;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ofss.repository.TransactionDao;
import com.ofss.scheduler.SettlementProcessorProperties;

@Service
public class ReleasedTransactionSettlementProcessorImpl
        implements ReleasedTransactionSettlementProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ReleasedTransactionSettlementProcessorImpl.class);
    private static final String CORRELATION_PREFIX = "SETTLEMENT-";

    private final TransactionDao transactionDao;
    private final SettlementService settlementService;
    private final SettlementFailureRecorder failureRecorder;

    public ReleasedTransactionSettlementProcessorImpl(
            TransactionDao transactionDao,
            SettlementService settlementService,
            SettlementFailureRecorder failureRecorder) {
        this.transactionDao = Objects.requireNonNull(transactionDao, "transactionDao is required");
        this.settlementService = Objects.requireNonNull(settlementService, "settlementService is required");
        this.failureRecorder = Objects.requireNonNull(failureRecorder, "failureRecorder is required");
    }

    @Override
    public int processDueTransactions(int batchSize) {
        if (batchSize < 1 || batchSize > SettlementProcessorProperties.MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batchSize must be between 1 and 25");
        }

        List<Long> candidateIds = transactionDao
                .findDueSettlementTransactionIds(batchSize);
        int settledCount = 0;

        for (Long transactionId : candidateIds) {
            String correlationId = CORRELATION_PREFIX + transactionId;
            try {
                SettlementAttemptOutcome outcome = settlementService
                        .settleIfReleased(transactionId, correlationId);
                if (outcome == SettlementAttemptOutcome.SETTLED) {
                    settledCount++;
                }
            } catch (RuntimeException failure) {
                try {
                    failureRecorder.recordFailure(transactionId, correlationId, failure);
                } catch (RuntimeException recordingFailure) {
                    LOGGER.error(
                            "Settlement and failure recording both failed for transactionId={}",
                            transactionId,
                            recordingFailure);
                }
            }
        }
        return settledCount;
    }
}
