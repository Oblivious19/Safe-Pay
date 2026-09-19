package com.ofss.services;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.Account;
import com.ofss.beans.AccountType;
import com.ofss.beans.CurrencyCode;
import com.ofss.beans.LedgerPosting;
import com.ofss.beans.LedgerPostingStatus;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.repository.AccountDao;
import com.ofss.repository.LedgerEntryDao;
import com.ofss.repository.LedgerPostingDao;
import com.ofss.repository.TransactionDao;
import com.ofss.scheduler.SettlementProcessorProperties;

import jakarta.persistence.EntityManager;

@Service
public class SettlementServiceImpl implements SettlementService {

    private final TransactionDao transactionDao;
    private final AccountDao accountDao;
    private final LedgerPostingDao ledgerPostingDao;
    private final LedgerEntryDao ledgerEntryDao;
    private final SettlementPostingFactory postingFactory;
    private final SettlementEvidenceService evidenceService;
    private final TransactionStateService stateService;
    private final SettlementProcessorProperties properties;
    private final EntityManager entityManager;

    public SettlementServiceImpl(
            TransactionDao transactionDao,
            AccountDao accountDao,
            LedgerPostingDao ledgerPostingDao,
            LedgerEntryDao ledgerEntryDao,
            SettlementPostingFactory postingFactory,
            SettlementEvidenceService evidenceService,
            TransactionStateService stateService,
            SettlementProcessorProperties properties,
            EntityManager entityManager) {

        this.transactionDao = Objects.requireNonNull(transactionDao, "transactionDao is required");
        this.accountDao = Objects.requireNonNull(accountDao, "accountDao is required");
        this.ledgerPostingDao = Objects.requireNonNull(ledgerPostingDao, "ledgerPostingDao is required");
        this.ledgerEntryDao = Objects.requireNonNull(ledgerEntryDao, "ledgerEntryDao is required");
        this.postingFactory = Objects.requireNonNull(postingFactory, "postingFactory is required");
        this.evidenceService = Objects.requireNonNull(evidenceService, "evidenceService is required");
        this.stateService = Objects.requireNonNull(stateService, "stateService is required");
        this.properties = Objects.requireNonNull(properties, "properties is required");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager is required");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SettlementAttemptOutcome settleIfReleased(
            Long transactionId,
            String correlationId) {

        requirePositiveId(transactionId, "transactionId");
        requireText(correlationId, "correlationId");

        TransactionDb transaction = transactionDao
                .findByIdForUpdate(transactionId)
                .orElse(null);

        if (transaction == null) {
            return SettlementAttemptOutcome.NOT_FOUND;
        }

        if (transaction.getState() == TransactionState.SETTLED) {
            return requireCompletedPosting(transactionId);
        }

        if (transaction.getState() != TransactionState.RELEASED) {
            return SettlementAttemptOutcome.NOT_ELIGIBLE;
        }

        if (ledgerPostingDao.findByTransaction_TransactionId(transactionId).isPresent()) {
            throw new SettlementInvariantException(
                    "SETTLEMENT_POSTING_STATE_MISMATCH",
                    "A RELEASED transaction already has a settlement posting");
        }

        OffsetDateTime settlementTime = Objects.requireNonNull(
                transactionDao.currentDatabaseTime(),
                "database time is required");

        Account sourceAccount = transaction.getSourceAccount();
        requirePersistedAccount(sourceAccount, "source account");

        long clearingAccountId = properties.requireOutboundClearingAccountId();
        if (sourceAccount.getAccountId().equals(clearingAccountId)) {
            throw new SettlementInvariantException(
                    "CLEARING_ACCOUNT_COLLISION",
                    "Source and clearing accounts must be different");
        }

        Account[] lockedAccounts = lockAccountsInIdOrder(
                sourceAccount.getAccountId(),
                clearingAccountId);
        Account lockedSource = sourceAccount.getAccountId().equals(lockedAccounts[0].getAccountId())
                ? lockedAccounts[0] : lockedAccounts[1];
        Account lockedClearing = clearingAccountId == lockedAccounts[0].getAccountId()
                ? lockedAccounts[0] : lockedAccounts[1];

        validateSettlementState(transaction, lockedSource, lockedClearing);

        SettlementPostingPair pair = postingFactory.create(
                transaction,
                lockedClearing,
                settlementTime);

        ledgerPostingDao.saveAndFlush(pair.posting());
        ledgerEntryDao.saveAll(pair.entriesInPostingOrder());
        entityManager.flush();

        lockedSource.consumeReservedFunds(transaction.getAmount(), settlementTime);
        lockedClearing.creditSettlementFunds(transaction.getAmount(), settlementTime);
        transaction.endReservation(settlementTime);
        stateService.transition(transaction, TransactionState.SETTLED, settlementTime);

        pair.posting().markPosted(settlementTime);
        ledgerPostingDao.saveAndFlush(pair.posting());

        evidenceService.appendSuccessfulSettlement(
                transaction,
                pair.posting(),
                correlationId,
                settlementTime);
        entityManager.flush();

        return SettlementAttemptOutcome.SETTLED;
    }

    private SettlementAttemptOutcome requireCompletedPosting(Long transactionId) {
        LedgerPosting posting = ledgerPostingDao
                .findByTransaction_TransactionId(transactionId)
                .orElseThrow(() -> new SettlementInvariantException(
                        "SETTLED_POSTING_MISSING",
                        "A SETTLED transaction must have one posting"));

        if (posting.getStatus() != LedgerPostingStatus.POSTED) {
            throw new SettlementInvariantException(
                    "SETTLED_POSTING_INCOMPLETE",
                    "A SETTLED transaction must have a POSTED posting");
        }
        return SettlementAttemptOutcome.ALREADY_SETTLED;
    }

    private Account[] lockAccountsInIdOrder(Long sourceAccountId, Long clearingAccountId) {
        Long firstId = Math.min(sourceAccountId, clearingAccountId);
        Long secondId = Math.max(sourceAccountId, clearingAccountId);
        Account first = requiredLockedAccount(firstId);
        Account second = requiredLockedAccount(secondId);
        return new Account[]{first, second};
    }

    private Account requiredLockedAccount(Long accountId) {
        return accountDao.findByIdForUpdate(accountId)
                .orElseThrow(() -> new SettlementInvariantException(
                        "SETTLEMENT_ACCOUNT_NOT_FOUND",
                        "A required settlement account was not found"));
    }

    private static void validateSettlementState(
            TransactionDb transaction,
            Account source,
            Account clearing) {

        if (!Objects.equals(transaction.getSourceAccount().getAccountId(), source.getAccountId())
                || source.getOwner() == null
                || !Objects.equals(source.getOwner().getUserId(), transaction.getCustomer().getUserId())
                || !source.isCustomerOwnedAccount()) {
            throw new SettlementInvariantException(
                    "INVALID_SETTLEMENT_SOURCE",
                    "Transaction source account is inconsistent");
        }
        if (!source.isActive()) {
            throw new SettlementInvariantException("SOURCE_ACCOUNT_INACTIVE", "Source account is inactive");
        }
        if (clearing.getAccountType() != AccountType.OUTBOUND_CLEARING
                || clearing.getOwner() != null
                || !clearing.isActive()
                || clearing.getCurrencyCode() != CurrencyCode.INR) {
            throw new SettlementInvariantException(
                    "INVALID_OUTBOUND_CLEARING_ACCOUNT",
                    "Configured clearing account is not an active ownerless INR outbound-clearing account");
        }
        if (source.getCurrencyCode() != transaction.getCurrencyCode()
                || source.getCurrencyCode() != clearing.getCurrencyCode()) {
            throw new SettlementInvariantException("SETTLEMENT_CURRENCY_MISMATCH", "Settlement accounts must use transaction currency");
        }

        BigDecimal amount = transaction.getAmount();
        if (transaction.getReservedAmount() == null
                || transaction.getReservedAmount().compareTo(amount) != 0
                || transaction.getReservedAt() == null
                || transaction.getReservationEndedAt() != null
                || source.getReservedAmount().compareTo(amount) < 0
                || source.getCurrentBalance().compareTo(amount) < 0) {
            throw new SettlementInvariantException(
                    "SETTLEMENT_RESERVATION_INVALID",
                    "Settlement requires a complete source reservation");
        }
    }

    private static void requirePersistedAccount(Account account, String fieldName) {
        if (account == null || account.getAccountId() == null || account.getAccountId() <= 0L) {
            throw new SettlementInvariantException(
                    "SETTLEMENT_ACCOUNT_NOT_FOUND",
                    fieldName + " must already be persisted");
        }
    }

    private static void requirePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
