package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.Account;
import com.ofss.beans.Beneficiary;
import com.ofss.beans.CurrencyCode;
import com.ofss.beans.RiskReview;
import com.ofss.beans.RiskReviewStatus;
import com.ofss.beans.RiskTier;
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
import com.ofss.repository.RiskReviewDao;
import com.ofss.repository.TransactionDao;
import com.ofss.repository.UserDao;
import com.ofss.repository.UserRoleDao;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RiskReviewServiceImplTest {

    private static final Long TRANSACTION_ID = 101L;
    private static final Long REVIEW_ID = 501L;
    private static final Long CUSTOMER_ID = 11L;
    private static final Long OFFICER_ID = 22L;
    private static final Long ACCOUNT_ID = 71L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-16T16:00:00Z");

    @Mock private RiskReviewDao riskReviewDao;
    @Mock private TransactionDao transactionDao;
    @Mock private UserDao userDao;
    @Mock private UserRoleDao userRoleDao;
    @Mock private AccountFundsService accountFundsService;
    @Mock private TransactionStateService stateService;
    @Mock private RiskReviewAuditService auditService;
    @Mock private TransactionLifecycleEvidenceService evidenceService;
    @Mock private EntityManager entityManager;
    @Mock private TransactionDb transaction;
    @Mock private User customer;
    @Mock private User officer;
    @Mock private Account sourceAccount;
    @Mock private Beneficiary beneficiary;

    private RiskReviewService service;

    @BeforeEach
    void setUp() {
        service = new RiskReviewServiceImpl(
                riskReviewDao,
                transactionDao,
                userDao,
                userRoleDao,
                accountFundsService,
                stateService,
                auditService,
                evidenceService,
                entityManager);

        when(transaction.getTransactionId()).thenReturn(TRANSACTION_ID);
        when(transaction.getTransactionReference()).thenReturn("SP-101");
        when(transaction.getCustomer()).thenReturn(customer);
        when(transaction.getState()).thenReturn(
                TransactionState.PENDING_RISK_REVIEW);
        when(transaction.getRiskTier()).thenReturn(RiskTier.VERY_HIGH);
        when(transaction.getVerificationCompletedAt())
                .thenReturn(NOW.minusSeconds(30));
        when(transaction.getAmount())
                .thenReturn(new BigDecimal("125000.00"));
        when(transaction.getReservedAmount())
                .thenReturn(new BigDecimal("125000.00"));
        when(transaction.getReservedAt()).thenReturn(NOW.minusMinutes(2));
        when(transaction.getReservationEndedAt()).thenReturn(null);
        when(transaction.getSourceAccount()).thenReturn(sourceAccount);
        when(transaction.getBeneficiary()).thenReturn(beneficiary);
        when(transaction.getCurrencyCode()).thenReturn(CurrencyCode.INR);
        when(transaction.getPolicyVersion()).thenReturn("V1");
        when(transaction.getRiskExplanation())
                .thenReturn("Manual review required");

        when(customer.getUserId()).thenReturn(CUSTOMER_ID);
        when(customer.getFullName()).thenReturn("Customer One");
        when(customer.getEmail()).thenReturn("customer@example.com");
        when(officer.getUserId()).thenReturn(OFFICER_ID);
        when(officer.getFullName()).thenReturn("Risk Officer");
        when(officer.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(sourceAccount.getAccountId()).thenReturn(ACCOUNT_ID);
        when(sourceAccount.getAccountNumber())
                .thenReturn("1234567890123456");
        when(beneficiary.getBeneficiaryId()).thenReturn(81L);
        when(beneficiary.getBeneficiaryName()).thenReturn("Vendor One");

        when(userDao.findById(OFFICER_ID))
                .thenReturn(Optional.of(officer));
        when(userRoleDao.existsByUser_UserIdAndRole_RoleCode(
                OFFICER_ID,
                RoleName.RISK_OFFICER))
                .thenReturn(true);
        when(transactionDao.currentDatabaseTime()).thenReturn(NOW);
        when(transactionDao.findByIdForUpdate(TRANSACTION_ID))
                .thenReturn(Optional.of(transaction));
    }

    @Test
    void lifecycleHooksRequireAnExistingAtomicTransaction()
            throws Exception {

        Transactional open = RiskReviewServiceImpl.class
                .getMethod(
                        "openNextRound",
                        TransactionDb.class,
                        OffsetDateTime.class)
                .getAnnotation(Transactional.class);
        Transactional cancel = RiskReviewServiceImpl.class
                .getMethod(
                        "cancelPendingForCustomer",
                        TransactionDb.class,
                        OffsetDateTime.class)
                .getAnnotation(Transactional.class);

        assertThat(open.propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(cancel.propagation()).isEqualTo(Propagation.MANDATORY);
    }

    @Test
    void opensFirstRoundAfterOtpTransition() {
        when(riskReviewDao.findPendingByTransactionIdForUpdate(
                TRANSACTION_ID))
                .thenReturn(Optional.empty());
        when(riskReviewDao
                .findFirstByTransaction_TransactionIdOrderByReviewRoundDesc(
                        TRANSACTION_ID))
                .thenReturn(Optional.empty());

        service.openNextRound(transaction, NOW);

        ArgumentCaptor<RiskReview> captor =
                ArgumentCaptor.forClass(RiskReview.class);
        verify(riskReviewDao).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getReviewRound()).isEqualTo(1);
        assertThat(captor.getValue().getStatus())
                .isEqualTo(RiskReviewStatus.PENDING);
    }

    @Test
    void incrementsRoundWithoutMutatingPriorReview() {
        RiskReview prior = review(3);
        prior.requestReverification(
                officer,
                "Repeat verification",
                NOW.minusSeconds(1));
        when(riskReviewDao.findPendingByTransactionIdForUpdate(
                TRANSACTION_ID))
                .thenReturn(Optional.empty());
        when(riskReviewDao
                .findFirstByTransaction_TransactionIdOrderByReviewRoundDesc(
                        TRANSACTION_ID))
                .thenReturn(Optional.of(prior));

        service.openNextRound(transaction, NOW);

        ArgumentCaptor<RiskReview> captor =
                ArgumentCaptor.forClass(RiskReview.class);
        verify(riskReviewDao).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getReviewRound()).isEqualTo(4);
        assertThat(prior.getStatus())
                .isEqualTo(RiskReviewStatus.REVERIFICATION_REQUESTED);
    }

    @Test
    void refusesSecondOpenReviewForSameTransaction() {
        RiskReview existingReview = review(1);

        when(riskReviewDao.findPendingByTransactionIdForUpdate(
                TRANSACTION_ID))
                .thenReturn(Optional.of(existingReview));

        assertThatThrownBy(() -> service.openNextRound(
                transaction,
                NOW))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode")
                .isEqualTo("RISK_REVIEW_ALREADY_PENDING");

        verify(riskReviewDao, never()).saveAndFlush(any());
    }

    @Test
    void customerCancellationClosesPendingReview() {
        RiskReview review = review(1);
        when(riskReviewDao.findPendingByTransactionIdForUpdate(
                TRANSACTION_ID))
                .thenReturn(Optional.of(review));

        service.cancelPendingForCustomer(transaction, NOW);

        assertThat(review.getStatus()).isEqualTo(RiskReviewStatus.CANCELLED);
        assertThat(review.getDecidedByUser()).isSameAs(customer);
    }

    @Test
    void missingPendingReviewFailsClosedDuringCancellation() {
        when(riskReviewDao.findPendingByTransactionIdForUpdate(
                TRANSACTION_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelPendingForCustomer(
                transaction,
                NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "PENDING_RISK_REVIEW transaction has no pending review");
    }

    @Test
    void pendingQueueIsOfficerOnlyAndDefaultsToPriority() {
        RiskReview review = review(1);
        PageRequest pageable = PageRequest.of(0, 20);
        when(riskReviewDao.searchPending(null, true, pageable))
                .thenReturn(new PageImpl<>(
                        List.of(review),
                        pageable,
                        1));

        PagedResponse<RiskReviewSummaryResponse> response =
                service.listPending(OFFICER_ID, 0, 20);

        assertThat(response.items()).singleElement()
                .satisfies(item -> assertThat(item.reviewId())
                        .isEqualTo(REVIEW_ID.toString()));
        verify(riskReviewDao).searchPending(null, true, pageable);
    }

    @Test
    void categoryAndFifoFiltersArePassedToDatabaseAndSortIsAllowlisted() {
        PageRequest page = PageRequest.of(0, 20);
        when(riskReviewDao.searchPending(com.ofss.beans.PaymentCategory.MEDICAL, false, page))
                .thenReturn(org.springframework.data.domain.Page.empty(page));
        assertThat(service.listPending(OFFICER_ID, com.ofss.beans.PaymentCategory.MEDICAL,
                "OLDEST", 0, 20).items()).isEmpty();
        verify(riskReviewDao).searchPending(com.ofss.beans.PaymentCategory.MEDICAL, false, page);
        assertThatThrownBy(() -> service.listPending(OFFICER_ID, null, "amount desc", 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void systemAdminWithoutRiskOfficerRoleIsDenied() {
        when(userRoleDao.existsByUser_UserIdAndRole_RoleCode(
                OFFICER_ID,
                RoleName.RISK_OFFICER))
                .thenReturn(false);

        assertThatThrownBy(() -> service.listPending(
                OFFICER_ID,
                0,
                20))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Active RISK_OFFICER authority is required");

        verifyNoInteractions(riskReviewDao);
    }

    @Test
    void terminalReviewRemainsAvailableAsReadOnlyDetail() {
        RiskReview review = review(1);
        review.approve(officer, null, NOW);
        when(riskReviewDao.findByApprovalId(REVIEW_ID))
                .thenReturn(Optional.of(review));

        RiskReviewDetailResponse response = service.getReview(
                OFFICER_ID,
                REVIEW_ID);

        assertThat(response.review().status())
                .isEqualTo(RiskReviewStatus.APPROVED);
        assertThat(response.decidedByUserId())
                .isEqualTo(OFFICER_ID.toString());
    }

    @Test
    void noteUsesSameLocksAndAppendOnlyAuditBoundary() {
        RiskReview review = arrangeDecision(review(1));
        RiskReviewNoteResponse noteResponse =
                new RiskReviewNoteResponse(
                        REVIEW_ID.toString(),
                        TRANSACTION_ID.toString(),
                        "RISK-REVIEW-NOTE-1",
                        NOW);
        when(auditService.appendNote(
                review,
                officer,
                "Investigating beneficiary history",
                "corr-note",
                "key-note",
                NOW)).thenReturn(noteResponse);

        RiskReviewNoteResponse response = service.addNote(
                OFFICER_ID,
                REVIEW_ID,
                "Investigating beneficiary history",
                "corr-note",
                "key-note");

        assertThat(response).isEqualTo(noteResponse);
        assertThat(review.getStatus()).isEqualTo(RiskReviewStatus.PENDING);
        verify(auditService).appendNote(
                review,
                officer,
                "Investigating beneficiary history",
                "corr-note",
                "key-note",
                NOW);
        verify(entityManager).flush();
    }

    @Test
    void noteLosesWhenReviewIsAlreadyTerminal() {
        RiskReview review = review(1);
        review.approve(officer, null, NOW.minusSeconds(1));
        arrangeDecision(review);

        assertThatThrownBy(() -> service.addNote(
                OFFICER_ID,
                REVIEW_ID,
                "Too late",
                "corr-note",
                "key-note"))
                .isInstanceOf(InvalidStateTransitionException.class);

        verifyNoInteractions(auditService);
    }

    @Test
    void auditFailureStopsDecisionBeforeFlush() {
        RiskReview review = arrangeDecision(review(1));
        org.mockito.Mockito.doThrow(
                new IllegalStateException("audit unavailable"))
                .when(auditService)
                .appendDecision(
                        review,
                        officer,
                        TransactionState.PENDING_RISK_REVIEW,
                        TransactionState.RELEASED,
                        "corr-audit",
                        "key-audit",
                        NOW);

        assertThatThrownBy(() -> service.approve(
                OFFICER_ID,
                REVIEW_ID,
                null,
                "corr-audit",
                "key-audit"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit unavailable");

        verify(entityManager, never()).flush();
    }

    @Test
    void approvalReleasesTransactionButRetainsReservation() {
        RiskReview review = arrangeDecision(review(1));

        RiskReviewDetailResponse response = service.approve(
                OFFICER_ID,
                REVIEW_ID,
                null,
                "corr-approve",
                "key-approve");

        assertThat(response.review().status())
                .isEqualTo(RiskReviewStatus.APPROVED);
        verify(stateService).transition(
                transaction,
                TransactionState.RELEASED,
                NOW);
        verifyNoInteractions(accountFundsService);
        verify(transaction, never()).endReservation(any());
        verify(auditService).appendDecision(
                review,
                officer,
                TransactionState.PENDING_RISK_REVIEW,
                TransactionState.RELEASED,
                "corr-approve",
                "key-approve",
                NOW);
        verify(evidenceService).appendNotification(
                TransactionLifecycleEvent.RISK_REVIEW_APPROVED,
                transaction,
                new OperationContext("corr-approve", "key-approve"),
                "REVIEW-501",
                NOW);
    }

    @Test
    void rejectionReleasesReservationBeforeCancelling() {
        RiskReview review = arrangeDecision(review(1));

        RiskReviewDetailResponse response = service.reject(
                OFFICER_ID,
                REVIEW_ID,
                "Confirmed mule pattern",
                "corr-reject",
                "key-reject");

        assertThat(response.review().status())
                .isEqualTo(RiskReviewStatus.REJECTED);
        verify(accountFundsService).releaseReservedFunds(
                ACCOUNT_ID,
                new BigDecimal("125000.00"),
                NOW);
        verify(transaction).endReservation(NOW);
        verify(stateService).transition(
                transaction,
                TransactionState.CANCELLED,
                "RISK_REVIEW_REJECTED",
                NOW);
        verify(auditService).appendDecision(
                review,
                officer,
                TransactionState.PENDING_RISK_REVIEW,
                TransactionState.CANCELLED,
                "corr-reject",
                "key-reject",
                NOW);
    }

    @Test
    void reverificationRetainsReservationAndRequiresNewOtpState() {
        RiskReview review = arrangeDecision(review(1));

        RiskReviewDetailResponse response =
                service.requestReverification(
                        OFFICER_ID,
                        REVIEW_ID,
                        "Confirm beneficiary ownership",
                        "corr-reverify",
                        "key-reverify");

        assertThat(response.review().status())
                .isEqualTo(RiskReviewStatus.REVERIFICATION_REQUESTED);
        verify(stateService).transition(
                transaction,
                TransactionState.VERIFICATION_REQUIRED,
                NOW);
        verifyNoInteractions(accountFundsService);
        verify(transaction, never()).endReservation(any());
        verify(auditService).appendDecision(
                review,
                officer,
                TransactionState.PENDING_RISK_REVIEW,
                TransactionState.VERIFICATION_REQUIRED,
                "corr-reverify",
                "key-reverify",
                NOW);
    }

    @Test
    void secondOfficerDecisionLosesWithStateConflict() {
        RiskReview review = review(1);
        review.approve(officer, null, NOW.minusSeconds(1));
        arrangeDecision(review);

        assertThatThrownBy(() -> service.reject(
                OFFICER_ID,
                REVIEW_ID,
                "Late rejection",
                "corr-late",
                "key-late"))
                .isInstanceOf(InvalidStateTransitionException.class);

        verifyNoInteractions(accountFundsService);
        verify(stateService, never()).transition(
                any(),
                any(),
                any(String.class),
                any());
    }

    private RiskReview arrangeDecision(RiskReview review) {
        when(riskReviewDao.findTransactionIdByReviewId(REVIEW_ID))
                .thenReturn(Optional.of(TRANSACTION_ID));
        when(transactionDao.findByIdForUpdate(TRANSACTION_ID))
                .thenReturn(Optional.of(transaction));
        when(riskReviewDao.findByIdForUpdate(REVIEW_ID))
                .thenReturn(Optional.of(review));
        return review;
    }

    private RiskReview review(int round) {
        RiskReview review = RiskReview.open(transaction, round, NOW.minusMinutes(1));
        ReflectionTestUtils.setField(review, "approvalId", REVIEW_ID);
        return review;
    }
}
