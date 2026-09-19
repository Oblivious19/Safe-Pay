package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import com.ofss.beans.AuditActorType;
import com.ofss.beans.AuditLog;
import com.ofss.beans.AuditOutcome;
import com.ofss.beans.RoleName;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.User;
import com.ofss.beans.UserStatus;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.audit.AuditLogResponse;
import com.ofss.dto.audit.TransactionAuditResponse;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AuditLogDao;
import com.ofss.repository.RiskReviewDao;
import com.ofss.repository.TransactionDao;
import com.ofss.repository.UserDao;
import com.ofss.repository.UserRoleDao;

@ExtendWith(MockitoExtension.class)
class AuditQueryServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long TRANSACTION_ID = 101L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-17T05:00:00Z");

    @Mock private AuditLogDao auditLogDao;
    @Mock private TransactionDao transactionDao;
    @Mock private RiskReviewDao riskReviewDao;
    @Mock private UserDao userDao;
    @Mock private UserRoleDao userRoleDao;
    @Mock private User user;
    @Mock private TransactionDb transaction;
    @Mock private AuditLog event;

    private AuditQueryService service;

    @BeforeEach
    void setUp() {
        service = new AuditQueryServiceImpl(
                auditLogDao,
                transactionDao,
                riskReviewDao,
                userDao,
                userRoleDao,
                new AuditDetailSanitizer(
                        new com.fasterxml.jackson.databind.ObjectMapper()));

        org.mockito.Mockito.lenient()
                .when(userDao.findById(USER_ID))
                .thenReturn(Optional.of(user));
        org.mockito.Mockito.lenient()
                .when(user.getStatus())
                .thenReturn(UserStatus.ACTIVE);
        org.mockito.Mockito.lenient()
                .when(transaction.getTransactionId())
                .thenReturn(TRANSACTION_ID);
        arrangeEvent();
    }

    @Test
    void auditorSearchReturnsOnlySanitizedProjection() {
        grant(RoleName.AUDITOR);
        when(auditLogDao.search(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));

        PagedResponse<AuditLogResponse> response =
                service.searchGlobalAudit(
                        USER_ID,
                        TRANSACTION_ID,
                        " risk_review_approved ",
                        "success",
                        "user",
                        "CORR-1",
                        NOW.minusHours(1),
                        NOW.plusHours(1),
                        0,
                        20);

        AuditLogResponse item = response.items().getFirst();
        assertThat(item.actionCode())
                .isEqualTo("RISK_REVIEW_APPROVED");
        assertThat(item.details().reviewId()).isEqualTo("501");
        assertThat(item.details().internalNote())
                .isEqualTo("review note");
        assertThat(AuditLogResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain(
                        "detailsJson",
                        "idempotencyKey");
    }

    @Test
    void globalSearchRequiresLiveAuditorRole() {
        when(userRoleDao.existsByUser_UserIdAndRole_RoleCode(
                USER_ID,
                RoleName.AUDITOR))
                .thenReturn(false);

        assertThatThrownBy(() -> service.searchGlobalAudit(
                USER_ID,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                20))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(auditLogDao);
    }

    @Test
    void globalSearchRejectsInvalidFiltersBeforeQuery() {
        grant(RoleName.AUDITOR);

        assertThatThrownBy(() -> service.searchGlobalAudit(
                USER_ID,
                null,
                null,
                "UNKNOWN",
                null,
                null,
                null,
                null,
                0,
                20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outcome is invalid");
        assertThatThrownBy(() -> service.searchGlobalAudit(
                USER_ID,
                null,
                null,
                null,
                null,
                null,
                NOW,
                NOW.minusSeconds(1),
                0,
                20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("from must be before or equal to to");

        verifyNoInteractions(auditLogDao);
    }

    @Test
    void globalSearchEnforcesBoundedPagination() {
        grant(RoleName.AUDITOR);

        assertThatThrownBy(() -> service.searchGlobalAudit(
                USER_ID,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("size must be between 1 and 100");
    }

    @Test
    void customerReceivesOnlySafeOwnedTimeline() {
        grant(RoleName.CUSTOMER);
        when(transactionDao.findOwnedById(
                TRANSACTION_ID,
                USER_ID))
                .thenReturn(Optional.of(transaction));
        when(auditLogDao.findCustomerVisibleTimeline(
                org.mockito.ArgumentMatchers.eq(TRANSACTION_ID),
                any(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));

        TransactionAuditResponse item = service
                .getTransactionTimeline(
                        USER_ID,
                        Set.of(RoleName.CUSTOMER),
                        TRANSACTION_ID,
                        0,
                        20)
                .items()
                .getFirst();

        assertThat(item.actionCode())
                .isEqualTo("RISK_REVIEW_APPROVED");
        assertThat(item.eventReference()).isNull();
        assertThat(item.reasonCode()).isNull();
        assertThat(item.actorType()).isNull();
        assertThat(item.actorRoleCode()).isNull();
        assertThat(item.details()).isNull();
    }

    @Test
    void customerCannotReadForeignTransactionTimeline() {
        grant(RoleName.CUSTOMER);
        when(transactionDao.findOwnedById(
                TRANSACTION_ID,
                USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTransactionTimeline(
                USER_ID,
                Set.of(RoleName.CUSTOMER),
                TRANSACTION_ID,
                0,
                20))
                .isInstanceOf(ResourceNotFoundExcp.class)
                .extracting("errorCode")
                .isEqualTo("TRANSACTION_NOT_FOUND");

        verifyNoInteractions(auditLogDao);
    }

    @Test
    void riskOfficerTimelineRequiresReviewRelationship() {
        grant(RoleName.RISK_OFFICER);
        when(riskReviewDao.existsByTransaction_TransactionId(
                TRANSACTION_ID))
                .thenReturn(false);

        assertThatThrownBy(() -> service.getTransactionTimeline(
                USER_ID,
                Set.of(RoleName.RISK_OFFICER),
                TRANSACTION_ID,
                0,
                20))
                .isInstanceOf(ResourceNotFoundExcp.class);
    }

    @Test
    void riskOfficerReceivesRelevantInternalReviewEvidence() {
        grant(RoleName.RISK_OFFICER);
        when(riskReviewDao.existsByTransaction_TransactionId(
                TRANSACTION_ID))
                .thenReturn(true);
        arrangeTimelinePage();

        TransactionAuditResponse item = service
                .getTransactionTimeline(
                        USER_ID,
                        Set.of(RoleName.RISK_OFFICER),
                        TRANSACTION_ID,
                        0,
                        20)
                .items()
                .getFirst();

        assertThat(item.eventReference()).isEqualTo("EVENT-501");
        assertThat(item.reasonCode()).isEqualTo("APPROVED");
        assertThat(item.details().internalNote())
                .isEqualTo("review note");
    }

    @Test
    void auditorMayReadAnyExistingTransactionTimeline() {
        grant(RoleName.AUDITOR);
        when(transactionDao.existsByTransactionId(TRANSACTION_ID))
                .thenReturn(true);
        arrangeTimelinePage();

        assertThat(service.getTransactionTimeline(
                USER_ID,
                Set.of(RoleName.AUDITOR),
                TRANSACTION_ID,
                0,
                20).items())
                .hasSize(1);
    }

    @Test
    void systemAdministratorAloneCannotReadAuditTimeline() {
        assertThatThrownBy(() -> service.getTransactionTimeline(
                USER_ID,
                Set.of(RoleName.SYSTEM_ADMIN),
                TRANSACTION_ID,
                0,
                20))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(auditLogDao);
    }

    private void grant(RoleName role) {
        when(userRoleDao.existsByUser_UserIdAndRole_RoleCode(
                USER_ID,
                role))
                .thenReturn(true);
    }

    private void arrangeTimelinePage() {
        when(auditLogDao.findAllByTransaction_TransactionId(
                org.mockito.ArgumentMatchers.eq(TRANSACTION_ID),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));
    }

    private void arrangeEvent() {
        org.mockito.Mockito.lenient().when(event.getAuditLogId())
                .thenReturn(901L);
        org.mockito.Mockito.lenient().when(event.getEventReference())
                .thenReturn("EVENT-501");
        org.mockito.Mockito.lenient().when(event.getActorUser())
                .thenReturn(user);
        org.mockito.Mockito.lenient().when(user.getUserId())
                .thenReturn(USER_ID);
        org.mockito.Mockito.lenient().when(event.getActorType())
                .thenReturn(AuditActorType.USER);
        org.mockito.Mockito.lenient().when(event.getActorRoleCode())
                .thenReturn("RISK_OFFICER");
        org.mockito.Mockito.lenient().when(event.getActionCode())
                .thenReturn("RISK_REVIEW_APPROVED");
        org.mockito.Mockito.lenient().when(event.getEntityType())
                .thenReturn("RISK_REVIEW");
        org.mockito.Mockito.lenient().when(event.getEntityId())
                .thenReturn(501L);
        org.mockito.Mockito.lenient().when(event.getTransaction())
                .thenReturn(transaction);
        org.mockito.Mockito.lenient().when(event.getPreviousState())
                .thenReturn("PENDING_RISK_REVIEW");
        org.mockito.Mockito.lenient().when(event.getNewState())
                .thenReturn("RELEASED");
        org.mockito.Mockito.lenient().when(event.getOutcome())
                .thenReturn(AuditOutcome.SUCCESS);
        org.mockito.Mockito.lenient().when(event.getReasonCode())
                .thenReturn("APPROVED");
        org.mockito.Mockito.lenient().when(event.getCorrelationId())
                .thenReturn("CORR-1");
        org.mockito.Mockito.lenient().when(event.getDetailsJson())
                .thenReturn("""
                        {"reviewId":"501","reviewRound":1,
                         "reviewStatus":"APPROVED",
                         "note":"review note","otp":"123456"}
                        """);
        org.mockito.Mockito.lenient().when(event.getOccurredAt())
                .thenReturn(NOW);
    }

}
