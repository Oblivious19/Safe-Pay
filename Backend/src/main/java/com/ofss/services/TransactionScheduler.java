package com.ofss.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TransactionScheduler {

    private final TransactionService transactionService;

    public TransactionScheduler(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Scheduled(fixedRate = 5000)
    public void releaseExpiredTransactions() {
        transactionService.releaseExpiredTransactions();
    }
}
