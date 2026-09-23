package com.ofss.services;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.ofss.beans.Account;
import com.ofss.beans.AuditOutcome;
import com.ofss.beans.Beneficiary;
import com.ofss.beans.BeneficiaryPaymentMethod;
import com.ofss.beans.AccountStatus;
import com.ofss.beans.CurrencyCode;
import com.ofss.beans.OtpChallenge;
import com.ofss.beans.ProtectionPolicy;
import com.ofss.beans.ProtectionReleaseMode;
import com.ofss.beans.RiskPolicy;
import com.ofss.beans.RiskPolicyBand;
import com.ofss.beans.RoleName;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionRiskFactor;
import com.ofss.beans.TransactionRiskFactorCode;
import com.ofss.beans.TransactionState;
import com.ofss.beans.User;
import com.ofss.beans.UserStatus;
import com.ofss.dto.risk.RiskEvaluationResult;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.transaction.AuthorizeTransactionRequest;
import com.ofss.dto.transaction.CreateTransactionRequest;
import com.ofss.dto.transaction.TransactionRiskExplanationResponse;
import com.ofss.dto.transaction.TransactionResponse;
import com.ofss.dto.transaction.TransactionSummaryResponse;
import com.ofss.excp.BusinessRuleException;
import com.ofss.excp.InvalidStateTransitionException;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AccountDao;
import com.ofss.repository.OtpChallengeDao;
import com.ofss.repository.TransactionDao;
import com.ofss.repository.TransactionRiskFactorDao;
import org.springframework.security.crypto.password.PasswordEncoder;

import jakarta.persistence.EntityManager;

@Service
@Transactional(readOnly = true)
public class TransactionServiceImpl
        implements TransactionService {

    private static final String REFERENCE_PREFIX = "SP-";
    private static final String CUSTOMER_CANCELLED =
            "CUSTOMER_CANCELLED";
    private static final String RISK_EVALUATION_FAILED =
            "RISK_EVALUATION_FAILED";
    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionDao transactionDao;
    private final TransactionRiskFactorDao riskFactorDao;
    private final AccountDao accountDao;
    private final OtpChallengeDao otpChallengeDao;
    private final UserService userService;
    private final AccountService accountService;
    private final BeneficiaryService beneficiaryService;
    private final AmountRiskEngine amountRiskEngine;
    private final TransactionStateService stateService;
    private final RiskReviewService riskReviewService;
    private final TransactionLifecycleEvidenceService evidenceService;
    private final EntityManager entityManager;
    private final Clock clock;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public TransactionServiceImpl(
            TransactionDao transactionDao,
            TransactionRiskFactorDao riskFactorDao,
            AccountDao accountDao,
            OtpChallengeDao otpChallengeDao,
            UserService userService,
            AccountService accountService,
            BeneficiaryService beneficiaryService,
            AmountRiskEngine amountRiskEngine,
            TransactionStateService stateService,
            RiskReviewService riskReviewService,
            TransactionLifecycleEvidenceService evidenceService,
            EntityManager entityManager,
            Clock clock,
            PasswordEncoder passwordEncoder) {

        this.transactionDao = transactionDao;
        this.riskFactorDao = riskFactorDao;
        this.accountDao = accountDao;
        this.otpChallengeDao = Objects.requireNonNull(
                otpChallengeDao,
                "otpChallengeDao is required");
        this.userService = userService;
        this.accountService = accountService;
        this.beneficiaryService = beneficiaryService;
        this.amountRiskEngine = amountRiskEngine;
        this.stateService = stateService;
        this.riskReviewService = Objects.requireNonNull(
                riskReviewService,
                "riskReviewService is required");
        this.evidenceService = Objects.requireNonNull(
                evidenceService,
                "evidenceService is required");
        this.entityManager = entityManager;
        this.clock = clock;
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder is required");
    }

    /** Compatibility constructor retained for existing focused service tests. */
    public TransactionServiceImpl(
            TransactionDao transactionDao,
            TransactionRiskFactorDao riskFactorDao,
            AccountDao accountDao,
            OtpChallengeDao otpChallengeDao,
            UserService userService,
            AccountService accountService,
            BeneficiaryService beneficiaryService,
            AmountRiskEngine amountRiskEngine,
            TransactionStateService stateService,
            RiskReviewService riskReviewService,
            TransactionLifecycleEvidenceService evidenceService,
            EntityManager entityManager,
            Clock clock) {
        this(transactionDao, riskFactorDao, accountDao, otpChallengeDao, userService,
                accountService, beneficiaryService, amountRiskEngine, stateService,
                riskReviewService, evidenceService, entityManager, clock,
                new BCryptPasswordEncoder());
    }

    @Override
    @Transactional
    public TransactionResponse createTransaction(
            Long customerUserId,
            CreateTransactionRequest request) {

        return createTransaction(
                customerUserId,
                request,
                OperationContext.internal());
    }

    @Override
    @Transactional
    public TransactionResponse createTransaction(
            Long customerUserId,
            CreateTransactionRequest request,
            OperationContext context) {

        requirePositiveId(customerUserId, "customerUserId");
        Objects.requireNonNull(request, "request is required");

        request.validateCategory();
        User customer = requireActiveCustomer(customerUserId);
        Account sourceAccount = accountService
                .getRequiredActiveOwnedAccount(
                        customerUserId,
                        request.sourceAccountId());
        Beneficiary beneficiary = beneficiaryService
                .getRequiredActiveOwnedBeneficiary(
                        customerUserId,
                        request.beneficiaryId());
        Account destinationAccount = resolveActiveDestination(
                beneficiary,
                sourceAccount);
        requireSafePayPin(customer, request.safePayPin());

        TransactionDb transaction =
                TransactionDb.createPaymentInstruction(
                        newTransactionReference(),
                        customer,
                        sourceAccount,
                        beneficiary,
                        destinationAccount,
                        request.amount(),
                        request.purpose(),
                        request.customerReference(),
                        currentUtcTime(),
                        request.category());

        TransactionDb persisted = transactionDao.save(transaction);
        entityManager.flush();

        evidenceService.appendUserEvent(
                TransactionLifecycleEvent.PAYMENT_CREATED,
                persisted,
                customer,
                RoleName.CUSTOMER,
                null,
                TransactionState.CREATED,
                AuditOutcome.SUCCESS,
                null,
                requireContext(context),
                "CREATE",
                persisted.getCreatedAt());

        return TransactionResponse.from(persisted);
    }

    @Override
    @Transactional
    public TransactionResponse authorizeTransaction(
            Long customerUserId,
            Long transactionId,
            AuthorizeTransactionRequest request) {

        return authorizeTransaction(
                customerUserId,
                transactionId,
                request,
                OperationContext.internal());
    }

    @Override
    @Transactional
    public TransactionResponse authorizeTransaction(
            Long customerUserId,
            Long transactionId,
            AuthorizeTransactionRequest request,
            OperationContext context) {

        requirePositiveId(customerUserId, "customerUserId");
        requirePositiveId(transactionId, "transactionId");
        Objects.requireNonNull(request, "request is required");

        if (!request.confirmed()) {
            throw new IllegalArgumentException(
                    "confirmed must be true to authorize the payment");
        }

        TransactionDb transaction = getOwnedForUpdate(
                customerUserId,
                transactionId);

        User customer = requireActiveCustomer(customerUserId);
        Account sourceAccount = lockAndValidateSourceAccount(
                transaction,
                customerUserId);
        validateBeneficiaryStillEligible(
                transaction,
                customerUserId);

        OffsetDateTime authorizedAt = currentUtcTime();
        stateService.transition(
                transaction,
                TransactionState.AUTHORIZED,
                authorizedAt);

        RiskEvaluationResult evaluation;

        try {
            evaluation = amountRiskEngine.evaluate(
                    transaction.getAmount());
        } catch (IllegalStateException configurationFailure) {
            stateService.transition(
                    transaction,
                    TransactionState.FAILED,
                    RISK_EVALUATION_FAILED,
                    currentUtcTime());
            evidenceService.appendUserEvent(
                    TransactionLifecycleEvent.PAYMENT_FAILED,
                    transaction,
                    customer,
                    RoleName.CUSTOMER,
                    TransactionState.CREATED,
                    TransactionState.FAILED,
                    AuditOutcome.FAILED,
                    RISK_EVALUATION_FAILED,
                    requireContext(context),
                    "AUTHORIZE",
                    transaction.getFailedAt());
            entityManager.flush();
            return TransactionResponse.from(transaction);
        }

        RiskPolicy assessedPolicy = entityManager.getReference(
                RiskPolicy.class,
                evaluation.riskPolicyId());
        RiskPolicyBand assessedBand = entityManager.getReference(
                RiskPolicyBand.class,
                evaluation.riskPolicyBandId());
        ProtectionPolicy assessedProtection =
                entityManager.getReference(
                        ProtectionPolicy.class,
                        evaluation.protectionPolicyId());

        transaction.recordRiskAssessment(
                evaluation,
                assessedPolicy,
                assessedBand,
                assessedProtection);

        try {
            /*
             * This managed-entity mutation deliberately occurs in the same
             * transaction and after the transaction-row lock. Catching here
             * lets a definitive funds failure be persisted as FAILED without
             * an inner transactional proxy marking the unit of work rollback-only.
             */
            sourceAccount.reserveFunds(
                    transaction.getAmount(),
                    evaluation.evaluatedAt());
        } catch (BusinessRuleException fundsFailure) {
            stateService.transition(
                    transaction,
                    TransactionState.FAILED,
                    fundsFailure.getErrorCode(),
                    currentUtcTime());
            persistAmountEvidence(transaction, assessedBand);
            evidenceService.appendUserEvent(
                    TransactionLifecycleEvent.PAYMENT_FAILED,
                    transaction,
                    customer,
                    RoleName.CUSTOMER,
                    TransactionState.CREATED,
                    TransactionState.FAILED,
                    AuditOutcome.FAILED,
                    fundsFailure.getErrorCode(),
                    requireContext(context),
                    "AUTHORIZE",
                    transaction.getFailedAt());
            return TransactionResponse.from(transaction);
        }

        stateService.transition(
                transaction,
                TransactionState.RISK_ASSESSED,
                evaluation.evaluatedAt());

        transaction.recordReservation(evaluation.evaluatedAt());
        routeAssessedTransaction(transaction, evaluation);
        persistAmountEvidence(transaction, assessedBand);

        evidenceService.appendUserEvent(
                authorizationEvent(transaction.getState()),
                transaction,
                customer,
                RoleName.CUSTOMER,
                TransactionState.CREATED,
                transaction.getState(),
                AuditOutcome.SUCCESS,
                null,
                requireContext(context),
                "AUTHORIZE",
                evaluation.evaluatedAt());

        return TransactionResponse.from(transaction);
    }

    @Override
    @Transactional
    public TransactionResponse cancelTransaction(
            Long customerUserId,
            Long transactionId) {

        return cancelTransaction(
                customerUserId,
                transactionId,
                OperationContext.internal());
    }

    @Override
    @Transactional
    public TransactionResponse cancelTransaction(
            Long customerUserId,
            Long transactionId,
            OperationContext context) {

        requirePositiveId(customerUserId, "customerUserId");
        requirePositiveId(transactionId, "transactionId");

        TransactionDb transaction = getOwnedForUpdate(
                customerUserId,
                transactionId);

        TransactionState currentState = transaction.getState();
        OffsetDateTime cancelledAt =
                currentState == TransactionState.PROTECTED
                        || currentState
                                == TransactionState.PENDING_RISK_REVIEW
                        ? currentDatabaseTime()
                        : currentUtcTime();

        /*
         * Reject an expired protection window before touching account funds.
         * The state service repeats this invariant at the transition boundary.
         */
        if (currentState == TransactionState.PROTECTED
                && (transaction.getProtectedUntil() == null
                        || !cancelledAt.isBefore(
                                transaction.getProtectedUntil()))) {

            throw new InvalidStateTransitionException(
                    currentState,
                    TransactionState.CANCELLED);
        }

        if (!currentState.allowsCustomerCancellation()) {
            throw new InvalidStateTransitionException(
                    currentState,
                    TransactionState.CANCELLED);
        }

        if (currentState == TransactionState.VERIFICATION_REQUIRED) {
            Optional<OtpChallenge> pendingChallenge = otpChallengeDao
                    .findPendingOwnedForUpdate(
                            transactionId,
                            customerUserId);

            pendingChallenge.ifPresent(
                    challenge -> challenge.cancel(cancelledAt));
        }

        if (currentState == TransactionState.PENDING_RISK_REVIEW) {
            riskReviewService.cancelPendingForCustomer(
                    transaction,
                    cancelledAt);
        }

        if (currentState.holdsReservation()) {
            Account sourceAccount = lockAndValidateSourceAccountOwnership(
                    transaction,
                    customerUserId);

            sourceAccount.releaseReservedFunds(
                    transaction.getAmount(),
                    cancelledAt);
            transaction.endReservation(cancelledAt);
        }

        stateService.transition(
                transaction,
                TransactionState.CANCELLED,
                CUSTOMER_CANCELLED,
                cancelledAt);
        evidenceService.appendUserEvent(
                TransactionLifecycleEvent.PAYMENT_CANCELLED,
                transaction,
                transaction.getCustomer(),
                RoleName.CUSTOMER,
                currentState,
                TransactionState.CANCELLED,
                AuditOutcome.SUCCESS,
                CUSTOMER_CANCELLED,
                requireContext(context),
                "CUSTOMER-CANCEL",
                cancelledAt);
        entityManager.flush();

        return TransactionResponse.from(transaction);
    }

    @Override
    public PagedResponse<TransactionSummaryResponse> listTransactions(
            Long customerUserId,
            int page,
            int size) {

        requirePositiveId(customerUserId, "customerUserId");
        requireValidPage(page, size);

        return PagedResponse.from(
                transactionDao.findAllOwned(
                        customerUserId,
                        PageRequest.of(page, size)),
                transaction -> TransactionSummaryResponse.from(transaction, customerUserId));
    }

    @Override
    public PagedResponse<TransactionSummaryResponse> listTransactions(
            Long customerUserId, OffsetDateTime from, OffsetDateTime to,
            TransactionState state, Long sourceAccountId, int page, int size) {
        requirePositiveId(customerUserId, "customerUserId");
        requireValidPage(page, size);
        if (sourceAccountId != null) {
            requirePositiveId(sourceAccountId, "sourceAccountId");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be earlier than to");
        }
        if (from == null && to == null && state == null && sourceAccountId == null) {
            return listTransactions(customerUserId, page, size);
        }
        return PagedResponse.from(transactionDao.searchOwned(customerUserId,
                from == null ? null : from.withOffsetSameInstant(ZoneOffset.UTC),
                to == null ? null : to.withOffsetSameInstant(ZoneOffset.UTC),
                state, sourceAccountId, PageRequest.of(page, size)),
                transaction -> TransactionSummaryResponse.from(transaction, customerUserId));
    }

    @Override
    public TransactionResponse getTransaction(
            Long customerUserId,
            Long transactionId) {

        requirePositiveId(customerUserId, "customerUserId");
        requirePositiveId(transactionId, "transactionId");

        TransactionDb transaction = getOwned(customerUserId, transactionId);
        return TransactionResponse.from(transaction)
                .withObservation(currentDatabaseTime());
    }

    @Override
    public TransactionRiskExplanationResponse getRiskExplanation(
            Long customerUserId,
            Long transactionId) {

        requirePositiveId(customerUserId, "customerUserId");
        requirePositiveId(transactionId, "transactionId");

        TransactionDb transaction = getOwned(
                customerUserId,
                transactionId);

        if (transaction.getRiskAssessedAt() == null) {
            throw new ResourceNotFoundExcp(
                    "RISK_EXPLANATION_NOT_FOUND",
                    "Risk explanation was not found");
        }

        TransactionRiskFactor evidence = riskFactorDao
                .findOwnedByFactorCode(
                        transactionId,
                        customerUserId,
                        TransactionRiskFactorCode.PAYMENT_AMOUNT)
                .orElseThrow(() -> new IllegalStateException(
                        "Assessed transaction is missing PAYMENT_AMOUNT evidence"));

        return TransactionRiskExplanationResponse.from(
                transaction,
                evidence);
    }

    private void routeAssessedTransaction(
            TransactionDb transaction,
            RiskEvaluationResult evaluation) {

        TransactionState targetState = switch (evaluation.riskTier()) {
            case LOW -> {
                requireReleaseMode(
                        evaluation,
                        ProtectionReleaseMode.IMMEDIATE);
                yield TransactionState.RELEASED;
            }
            case MEDIUM, HIGH -> {
                requireReleaseMode(
                        evaluation,
                        ProtectionReleaseMode.AFTER_TIMER);
                yield TransactionState.PROTECTED;
            }
            case VERY_HIGH -> {
                requireReleaseMode(
                        evaluation,
                        ProtectionReleaseMode.AFTER_REVIEW);
                yield TransactionState.VERIFICATION_REQUIRED;
            }
        };

        stateService.transition(
                transaction,
                targetState,
                evaluation.evaluatedAt());
    }

    private static TransactionLifecycleEvent authorizationEvent(
            TransactionState state) {
        return switch (state) {
            case RELEASED -> TransactionLifecycleEvent.PAYMENT_RELEASED;
            case PROTECTED -> TransactionLifecycleEvent.PAYMENT_PROTECTED;
            case VERIFICATION_REQUIRED ->
                    TransactionLifecycleEvent.OTP_REQUIRED;
            default -> throw new IllegalStateException(
                    "Authorization produced an unsupported state: "
                            + state);
        };
    }

    private static OperationContext requireContext(
            OperationContext context) {
        return Objects.requireNonNull(context, "context is required");
    }

    private void persistAmountEvidence(
            TransactionDb transaction,
            RiskPolicyBand assessedBand) {

        /*
         * Flush the transaction snapshot first. Oracle's composite foreign
         * key then guarantees that append-only evidence references the exact
         * band stored on the transaction.
         */
        entityManager.flush();
        riskFactorDao.save(
                TransactionRiskFactor.createPaymentAmountEvidence(
                        transaction,
                        assessedBand));
        entityManager.flush();
    }

    private User requireActiveCustomer(Long customerUserId) {
        User customer = userService.getRequiredUser(customerUserId);

        if (customer.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "USER_INACTIVE",
                    "Customer is not active");
        }

        return customer;
    }

    private Account lockAndValidateSourceAccount(
            TransactionDb transaction,
            Long customerUserId) {

        Account sourceAccount = lockAndValidateSourceAccountOwnership(
                transaction,
                customerUserId);

        if (!sourceAccount.isActive()) {
            throw new BusinessRuleException(
                    "ACCOUNT_INACTIVE",
                    "Account is inactive");
        }

        return sourceAccount;
    }

    private Account lockAndValidateSourceAccountOwnership(
            TransactionDb transaction,
            Long customerUserId) {

        Long sourceAccountId = Objects.requireNonNull(
                transaction.getSourceAccount(),
                "transaction sourceAccount is required")
                .getAccountId();

        Account sourceAccount = accountDao
                .findByIdForUpdate(sourceAccountId)
                .orElseThrow(
                        TransactionServiceImpl::transactionNotFound);

        if (!sourceAccount.isCustomerOwnedAccount()
                || sourceAccount.getOwner() == null
                || !Objects.equals(
                        sourceAccount.getOwner().getUserId(),
                        customerUserId)) {

            throw transactionNotFound();
        }

        return sourceAccount;
    }

    private void validateBeneficiaryStillEligible(
            TransactionDb transaction,
            Long customerUserId) {

        Long beneficiaryId = Objects.requireNonNull(
                transaction.getBeneficiary(),
                "transaction beneficiary is required")
                .getBeneficiaryId();

        Beneficiary beneficiary = beneficiaryService
                .getRequiredActiveOwnedBeneficiary(
                        customerUserId,
                        beneficiaryId);

        if (!Objects.equals(
                beneficiary.getBeneficiaryId(),
                beneficiaryId)) {
            throw transactionNotFound();
        }
    }

    private void requireSafePayPin(User customer, String pin) {
        if (!customer.hasSafePayPin()) {
            throw new BusinessRuleException("SAFE_PAY_PIN_NOT_SET",
                    "Set your SafePay PIN before making a payment.");
        }
        if (pin == null || !pin.matches("\\d{6}")
                || !customer.matchesSafePayPin(pin, passwordEncoder)) {
            throw new BusinessRuleException("SAFE_PAY_PIN_INVALID",
                    "The SafePay PIN is incorrect. Please try again.");
        }
    }

    private Account resolveActiveDestination(
            Beneficiary beneficiary,
            Account sourceAccount) {

        if (beneficiary.getPaymentMethod() != BeneficiaryPaymentMethod.BANK_ACCOUNT) {
            throw verifiedBeneficiaryRequired();
        }

        if (beneficiary.getDestinationAccount() == null) {
            return null;
        }

        if (beneficiary.getDestinationAccount().getAccountId() == null) {
            throw verifiedBeneficiaryRequired();
        }

        Long destinationAccountId = beneficiary
                .getDestinationAccount()
                .getAccountId();

        Account destinationAccount = accountDao
                .findById(destinationAccountId)
                .orElseThrow(TransactionServiceImpl::verifiedBeneficiaryRequired);

        if (!destinationAccount.isCustomerOwnedAccount()
                || destinationAccount.getStatus() != AccountStatus.ACTIVE
                || destinationAccount.getCurrencyCode() != CurrencyCode.INR) {
            throw verifiedBeneficiaryRequired();
        }

        if (Objects.equals(
                sourceAccount.getAccountId(),
                destinationAccount.getAccountId())) {
            throw new BusinessRuleException(
                    "SOURCE_AND_DESTINATION_SAME",
                    "Choose a different beneficiary account.");
        }

        return destinationAccount;
    }

    private TransactionDb getOwnedForUpdate(
            Long customerUserId,
            Long transactionId) {

        return transactionDao
                .findOwnedByIdForUpdate(
                        transactionId,
                        customerUserId)
                .orElseThrow(
                        TransactionServiceImpl::transactionNotFound);
    }

    private TransactionDb getOwned(
            Long customerUserId,
            Long transactionId) {

        return transactionDao
                .findOwnedById(
                        transactionId,
                        customerUserId)
                .orElseThrow(
                        TransactionServiceImpl::transactionNotFound);
    }

    private static void requireReleaseMode(
            RiskEvaluationResult evaluation,
            ProtectionReleaseMode expectedMode) {

        if (evaluation.releaseMode() != expectedMode) {
            throw new IllegalStateException(
                    "Risk tier and protection release mode are inconsistent");
        }
    }

    private static ResourceNotFoundExcp transactionNotFound() {
        return new ResourceNotFoundExcp(
                "TRANSACTION_NOT_FOUND",
                "Transaction was not found");
    }

    private static BusinessRuleException verifiedBeneficiaryRequired() {
        return new BusinessRuleException(
                "BENEFICIARY_NOT_VERIFIED",
                "This beneficiary is not verified for SafePay payments.");
    }

    private static void requirePositiveId(
            Long value,
            String fieldName) {

        if (value == null || value <= 0L) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive");
        }
    }

    private static void requireValidPage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must be zero or greater");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private static String newTransactionReference() {
        return REFERENCE_PREFIX
                + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .toUpperCase(Locale.ROOT);
    }

    private OffsetDateTime currentUtcTime() {
        return OffsetDateTime
                .now(clock)
                .withOffsetSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);
    }

    private OffsetDateTime currentDatabaseTime() {
        return Objects.requireNonNull(
                        transactionDao.currentDatabaseTime(),
                        "database time is required")
                .withOffsetSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);
    }
}
