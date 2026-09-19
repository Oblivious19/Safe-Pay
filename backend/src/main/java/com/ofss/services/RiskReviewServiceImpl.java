package com.ofss.services;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.RiskReview;
import com.ofss.beans.RiskReviewStatus;
import com.ofss.beans.RoleName;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.beans.User;
import com.ofss.beans.UserStatus;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.riskreview.RiskReviewDetailResponse;
import com.ofss.dto.riskreview.RiskReviewNoteResponse;
import com.ofss.dto.riskreview.RiskReviewSummaryResponse;
import com.ofss.excp.BusinessRuleException;
import com.ofss.excp.InvalidStateTransitionException;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.RiskReviewDao;
import com.ofss.repository.TransactionDao;
import com.ofss.repository.UserDao;
import com.ofss.repository.UserRoleDao;

import jakarta.persistence.EntityManager;

@Service
@Transactional(readOnly = true)
public class RiskReviewServiceImpl
        implements RiskReviewService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String RISK_REVIEW_REJECTED =
            "RISK_REVIEW_REJECTED";

    private final RiskReviewDao riskReviewDao;
    private final TransactionDao transactionDao;
    private final UserDao userDao;
    private final UserRoleDao userRoleDao;
    private final AccountFundsService accountFundsService;
    private final TransactionStateService stateService;
    private final RiskReviewAuditService auditService;
    private final TransactionLifecycleEvidenceService evidenceService;
    private final EntityManager entityManager;

    public RiskReviewServiceImpl(
            RiskReviewDao riskReviewDao,
            TransactionDao transactionDao,
            UserDao userDao,
            UserRoleDao userRoleDao,
            AccountFundsService accountFundsService,
            TransactionStateService stateService,
            RiskReviewAuditService auditService,
            TransactionLifecycleEvidenceService evidenceService,
            EntityManager entityManager) {

        this.riskReviewDao = Objects.requireNonNull(
                riskReviewDao,
                "riskReviewDao is required");
        this.transactionDao = Objects.requireNonNull(
                transactionDao,
                "transactionDao is required");
        this.userDao = Objects.requireNonNull(
                userDao,
                "userDao is required");
        this.userRoleDao = Objects.requireNonNull(
                userRoleDao,
                "userRoleDao is required");
        this.accountFundsService = Objects.requireNonNull(
                accountFundsService,
                "accountFundsService is required");
        this.stateService = Objects.requireNonNull(
                stateService,
                "stateService is required");
        this.auditService = Objects.requireNonNull(
                auditService,
                "auditService is required");
        this.evidenceService = Objects.requireNonNull(
                evidenceService,
                "evidenceService is required");
        this.entityManager = Objects.requireNonNull(
                entityManager,
                "entityManager is required");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void openNextRound(
            TransactionDb transaction,
            OffsetDateTime requestedAt) {

        TransactionDb supplied = requirePersistedTransaction(transaction);
        Long transactionId = supplied.getTransactionId();
        TransactionDb payment = transactionDao
                .findByIdForUpdate(transactionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Risk Review transaction was not found"));

        if (riskReviewDao
                .findPendingByTransactionIdForUpdate(transactionId)
                .isPresent()) {
            throw new BusinessRuleException(
                    "RISK_REVIEW_ALREADY_PENDING",
                    "A pending Risk Review already exists for the transaction");
        }

        int nextRound = riskReviewDao
                .findFirstByTransaction_TransactionIdOrderByReviewRoundDesc(
                        transactionId)
                .map(RiskReview::getReviewRound)
                .map(round -> Math.addExact(round, 1))
                .orElse(1);

        riskReviewDao.saveAndFlush(
                RiskReview.open(
                        payment,
                        nextRound,
                        requestedAt));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelPendingForCustomer(
            TransactionDb transaction,
            OffsetDateTime cancelledAt) {

        TransactionDb supplied = requirePersistedTransaction(transaction);
        TransactionDb payment = transactionDao
                .findByIdForUpdate(supplied.getTransactionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Risk Review transaction was not found"));

        if (payment.getState()
                != TransactionState.PENDING_RISK_REVIEW) {
            throw new InvalidStateTransitionException(
                    payment.getState(),
                    TransactionState.CANCELLED);
        }

        RiskReview review = riskReviewDao
                .findPendingByTransactionIdForUpdate(
                        payment.getTransactionId())
                .orElseThrow(() -> new IllegalStateException(
                        "PENDING_RISK_REVIEW transaction has no pending review"));

        review.cancelByCustomer(
                payment.getCustomer(),
                cancelledAt);
    }

    @Override
    public PagedResponse<RiskReviewSummaryResponse> listPending(
            Long riskOfficerUserId,
            int page,
            int size) {

        return listPending(riskOfficerUserId, null, "PRIORITY", page, size);
    }

    @Override
    public PagedResponse<RiskReviewSummaryResponse> listPending(
            Long riskOfficerUserId, com.ofss.beans.PaymentCategory category, String sort, int page, int size) {

        requireActiveRiskOfficer(riskOfficerUserId);
        requireValidPage(page, size);
        if (!"PRIORITY".equals(sort) && !"OLDEST".equals(sort)) {
            throw new IllegalArgumentException("sort must be PRIORITY or OLDEST");
        }

        return PagedResponse.from(
                riskReviewDao.searchPending(category, "PRIORITY".equals(sort), PageRequest.of(page, size)),
                RiskReviewSummaryResponse::from);
    }

    @Override
    public RiskReviewDetailResponse getReview(
            Long riskOfficerUserId,
            Long reviewId) {

        requireActiveRiskOfficer(riskOfficerUserId);
        requirePositiveId(reviewId, "reviewId");

        return RiskReviewDetailResponse.from(
                riskReviewDao.findByApprovalId(reviewId)
                        .orElseThrow(
                                RiskReviewServiceImpl::reviewNotFound));
    }

    @Override
    @Transactional
    public RiskReviewDetailResponse approve(
            Long riskOfficerUserId,
            Long reviewId,
            String optionalReason,
            String correlationId,
            String idempotencyKey) {

        return decide(
                riskOfficerUserId,
                reviewId,
                Decision.APPROVE,
                optionalReason,
                correlationId,
                idempotencyKey);
    }

    @Override
    @Transactional
    public RiskReviewDetailResponse reject(
            Long riskOfficerUserId,
            Long reviewId,
            String reason,
            String correlationId,
            String idempotencyKey) {

        return decide(
                riskOfficerUserId,
                reviewId,
                Decision.REJECT,
                reason,
                correlationId,
                idempotencyKey);
    }

    @Override
    @Transactional
    public RiskReviewDetailResponse requestReverification(
            Long riskOfficerUserId,
            Long reviewId,
            String reason,
            String correlationId,
            String idempotencyKey) {

        return decide(
                riskOfficerUserId,
                reviewId,
                Decision.REQUEST_REVERIFICATION,
                reason,
                correlationId,
                idempotencyKey);
    }

    @Override
    @Transactional
    public RiskReviewNoteResponse addNote(
            Long riskOfficerUserId,
            Long reviewId,
            String note,
            String correlationId,
            String idempotencyKey) {

        User officer = requireActiveRiskOfficer(riskOfficerUserId);
        requirePositiveId(reviewId, "reviewId");

        Long transactionId = riskReviewDao
                .findTransactionIdByReviewId(reviewId)
                .orElseThrow(RiskReviewServiceImpl::reviewNotFound);

        TransactionDb transaction = transactionDao
                .findByIdForUpdate(transactionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Risk Review references a missing transaction"));
        RiskReview review = riskReviewDao
                .findByIdForUpdate(reviewId)
                .orElseThrow(RiskReviewServiceImpl::reviewNotFound);

        if (!Objects.equals(
                review.getTransaction().getTransactionId(),
                transaction.getTransactionId())) {
            throw new IllegalStateException(
                    "Risk Review transaction changed while adding a note");
        }

        if (review.getStatus() != RiskReviewStatus.PENDING
                || transaction.getState()
                        != TransactionState.PENDING_RISK_REVIEW) {
            throw new InvalidStateTransitionException(
                    transaction.getState(),
                    TransactionState.PENDING_RISK_REVIEW);
        }

        OffsetDateTime occurredAt = currentDatabaseTime();
        RiskReviewNoteResponse response = auditService.appendNote(
                review,
                officer,
                note,
                correlationId,
                idempotencyKey,
                occurredAt);
        entityManager.flush();
        return response;
    }

    private RiskReviewDetailResponse decide(
            Long riskOfficerUserId,
            Long reviewId,
            Decision decision,
            String reason,
            String correlationId,
            String idempotencyKey) {

        User officer = requireActiveRiskOfficer(riskOfficerUserId);
        requirePositiveId(reviewId, "reviewId");

        Long transactionId = riskReviewDao
                .findTransactionIdByReviewId(reviewId)
                .orElseThrow(RiskReviewServiceImpl::reviewNotFound);

        /*
         * All competing review/customer actions lock in one order:
         * transaction, review, then account (only when funds are released).
         */
        TransactionDb transaction = transactionDao
                .findByIdForUpdate(transactionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Risk Review references a missing transaction"));

        RiskReview review = riskReviewDao
                .findByIdForUpdate(reviewId)
                .orElseThrow(RiskReviewServiceImpl::reviewNotFound);

        if (!Objects.equals(
                review.getTransaction().getTransactionId(),
                transaction.getTransactionId())) {
            throw new IllegalStateException(
                    "Risk Review transaction changed during decision");
        }

        TransactionState targetState = decision.targetState();

        if (review.getStatus() != RiskReviewStatus.PENDING
                || transaction.getState()
                        != TransactionState.PENDING_RISK_REVIEW) {
            throw new InvalidStateTransitionException(
                    transaction.getState(),
                    targetState);
        }

        OffsetDateTime decidedAt = currentDatabaseTime();

        switch (decision) {
            case APPROVE -> {
                review.approve(officer, reason, decidedAt);
                stateService.transition(
                        transaction,
                        TransactionState.RELEASED,
                        decidedAt);
            }
            case REJECT -> {
                review.reject(officer, reason, decidedAt);
                releaseReservation(transaction, decidedAt);
                stateService.transition(
                        transaction,
                        TransactionState.CANCELLED,
                        RISK_REVIEW_REJECTED,
                        decidedAt);
            }
            case REQUEST_REVERIFICATION -> {
                review.requestReverification(
                        officer,
                        reason,
                        decidedAt);
                stateService.transition(
                        transaction,
                        TransactionState.VERIFICATION_REQUIRED,
                        decidedAt);
            }
        }

        auditService.appendDecision(
                review,
                officer,
                TransactionState.PENDING_RISK_REVIEW,
                targetState,
                correlationId,
                idempotencyKey,
                decidedAt);

        evidenceService.appendNotification(
                decision.lifecycleEvent(),
                transaction,
                OperationContext.request(
                        correlationId,
                        idempotencyKey),
                "REVIEW-" + review.getApprovalId(),
                decidedAt);

        entityManager.flush();
        return RiskReviewDetailResponse.from(review);
    }

    private void releaseReservation(
            TransactionDb transaction,
            OffsetDateTime releasedAt) {

        var account = Objects.requireNonNull(
                transaction.getSourceAccount(),
                "transaction sourceAccount is required");
        requirePositiveId(account.getAccountId(), "sourceAccountId");

        accountFundsService.releaseReservedFunds(
                account.getAccountId(),
                transaction.getAmount(),
                releasedAt);
        transaction.endReservation(releasedAt);
    }

    private User requireActiveRiskOfficer(Long userId) {
        requirePositiveId(userId, "riskOfficerUserId");

        User user = userDao.findById(userId)
                .orElseThrow(RiskReviewServiceImpl::accessDenied);

        if (user.getStatus() != UserStatus.ACTIVE
                || !userRoleDao
                        .existsByUser_UserIdAndRole_RoleCode(
                                userId,
                                RoleName.RISK_OFFICER)) {
            throw accessDenied();
        }

        return user;
    }

    private static TransactionDb requirePersistedTransaction(
            TransactionDb transaction) {

        TransactionDb payment = Objects.requireNonNull(
                transaction,
                "transaction is required");
        requirePositiveId(
                payment.getTransactionId(),
                "transactionId");
        return payment;
    }

    private OffsetDateTime currentDatabaseTime() {
        return Objects.requireNonNull(
                        transactionDao.currentDatabaseTime(),
                        "database time is required")
                .withOffsetSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);
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

    private static void requirePositiveId(
            Long value,
            String fieldName) {

        if (value == null || value <= 0L) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive");
        }
    }

    private static ResourceNotFoundExcp reviewNotFound() {
        return new ResourceNotFoundExcp(
                "RISK_REVIEW_NOT_FOUND",
                "Risk Review was not found");
    }

    private static AccessDeniedException accessDenied() {
        return new AccessDeniedException(
                "Active RISK_OFFICER authority is required");
    }

    private enum Decision {
        APPROVE(TransactionState.RELEASED),
        REJECT(TransactionState.CANCELLED),
        REQUEST_REVERIFICATION(
                TransactionState.VERIFICATION_REQUIRED);

        private final TransactionState targetState;

        Decision(TransactionState targetState) {
            this.targetState = targetState;
        }

        TransactionState targetState() {
            return targetState;
        }

        TransactionLifecycleEvent lifecycleEvent() {
            return switch (this) {
                case APPROVE ->
                        TransactionLifecycleEvent.RISK_REVIEW_APPROVED;
                case REJECT ->
                        TransactionLifecycleEvent.RISK_REVIEW_REJECTED;
                case REQUEST_REVERIFICATION ->
                        TransactionLifecycleEvent
                                .RISK_REVERIFICATION_REQUESTED;
            };
        }
    }
}
