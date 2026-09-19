package com.ofss.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ofss.repository.TransactionDao;
import com.ofss.scheduler.ProtectionSchedulerProperties;

@Service
public class ProtectedTransactionReleaseServiceImpl
        implements ProtectedTransactionReleaseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ProtectedTransactionReleaseServiceImpl.class);

    private final TransactionDao transactionDao;
    private final ProtectedTransactionReleaseWorker releaseWorker;

    public ProtectedTransactionReleaseServiceImpl(
            TransactionDao transactionDao,
            ProtectedTransactionReleaseWorker releaseWorker) {

        this.transactionDao = transactionDao;
        this.releaseWorker = releaseWorker;
    }

    @Override
    public int releaseDueTransactions(int batchSize) {
        requireValidBatchSize(batchSize);

        List<Long> candidateIds = transactionDao
                .findExpiredProtectedTransactionIds(batchSize);

        int releasedCount = 0;

        for (Long candidateId : candidateIds) {
            try {
                ProtectedTransactionReleaseOutcome outcome =
                        releaseWorker.releaseIfExpired(candidateId);

                if (outcome
                        == ProtectedTransactionReleaseOutcome.RELEASED) {
                    releasedCount++;
                }
            } catch (RuntimeException releaseFailure) {
                /*
                 * Each worker owns a REQUIRES_NEW transaction. A malformed or
                 * temporarily failing candidate must not block the remaining
                 * backlog; the next poll may safely retry it.
                 */
                LOGGER.error(
                        "Protected transaction release failed for transactionId={}",
                        candidateId,
                        releaseFailure);
            }
        }

        return releasedCount;
    }

    private static void requireValidBatchSize(int batchSize) {
        if (batchSize < 1
                || batchSize
                        > ProtectionSchedulerProperties.MAX_BATCH_SIZE) {

            throw new IllegalArgumentException(
                    "batchSize must be between 1 and "
                            + ProtectionSchedulerProperties.MAX_BATCH_SIZE);
        }
    }
}
