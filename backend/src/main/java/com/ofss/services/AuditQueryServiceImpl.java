package com.ofss.services;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.AuditActorType;
import com.ofss.beans.AuditLog;
import com.ofss.beans.AuditOutcome;
import com.ofss.beans.RoleName;
import com.ofss.beans.User;
import com.ofss.beans.UserStatus;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.audit.AuditLogResponse;
import com.ofss.dto.audit.AuditSafeDetails;
import com.ofss.dto.audit.TransactionAuditResponse;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.AuditLogDao;
import com.ofss.repository.RiskReviewDao;
import com.ofss.repository.TransactionDao;
import com.ofss.repository.UserDao;
import com.ofss.repository.UserRoleDao;

@Service
@Transactional(readOnly = true)
public class AuditQueryServiceImpl implements AuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort NEWEST_FIRST = Sort.by(
            Sort.Order.desc("occurredAt"),
            Sort.Order.desc("auditLogId"));
    private static final Set<String> CUSTOMER_SAFE_ACTIONS = Set.of(
            "PAYMENT_CREATED",
            "PAYMENT_PROTECTED",
            "PAYMENT_RELEASED",
            "OTP_REQUIRED",
            "OTP_ISSUED",
            "OTP_RESENT",
            "OTP_VERIFICATION_DENIED",
            "OTP_VERIFIED",
            "RISK_REVIEW_PENDING",
            "RISK_REVIEW_APPROVED",
            "RISK_REVIEW_REJECTED",
            "RISK_REVIEW_REVERIFICATION_REQUESTED",
            "PAYMENT_CANCELLED",
            "PAYMENT_FAILED",
            "PAYMENT_SETTLED");

    private final AuditLogDao auditLogDao;
    private final TransactionDao transactionDao;
    private final RiskReviewDao riskReviewDao;
    private final UserDao userDao;
    private final UserRoleDao userRoleDao;
    private final AuditDetailSanitizer detailSanitizer;

    public AuditQueryServiceImpl(
            AuditLogDao auditLogDao,
            TransactionDao transactionDao,
            RiskReviewDao riskReviewDao,
            UserDao userDao,
            UserRoleDao userRoleDao,
            AuditDetailSanitizer detailSanitizer) {
        this.auditLogDao = Objects.requireNonNull(
                auditLogDao,
                "auditLogDao is required");
        this.transactionDao = Objects.requireNonNull(
                transactionDao,
                "transactionDao is required");
        this.riskReviewDao = Objects.requireNonNull(
                riskReviewDao,
                "riskReviewDao is required");
        this.userDao = Objects.requireNonNull(
                userDao,
                "userDao is required");
        this.userRoleDao = Objects.requireNonNull(
                userRoleDao,
                "userRoleDao is required");
        this.detailSanitizer = Objects.requireNonNull(
                detailSanitizer,
                "detailSanitizer is required");
    }

    @Override
    public PagedResponse<AuditLogResponse> searchGlobalAudit(
            Long auditorUserId,
            Long transactionId,
            String actionCode,
            String outcome,
            String actorType,
            String correlationId,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int size) {

        requireActiveRole(auditorUserId, RoleName.AUDITOR);
        requirePage(page, size);
        requireOptionalPositiveId(transactionId, "transactionId");

        String normalizedAction = normalizeUppercase(
                actionCode,
                "actionCode",
                100);
        AuditOutcome normalizedOutcome = parseEnum(
                outcome,
                AuditOutcome.class,
                "outcome");
        AuditActorType normalizedActorType = parseEnum(
                actorType,
                AuditActorType.class,
                "actorType");
        String normalizedCorrelation = normalizeText(
                correlationId,
                "correlationId",
                64);
        OffsetDateTime normalizedFrom = utc(from);
        OffsetDateTime normalizedTo = utc(to);
        if (normalizedFrom != null
                && normalizedTo != null
                && normalizedFrom.isAfter(normalizedTo)) {
            throw new IllegalArgumentException(
                    "from must be before or equal to to");
        }

        return PagedResponse.from(
                auditLogDao.search(
                        transactionId,
                        normalizedAction,
                        normalizedOutcome,
                        normalizedActorType,
                        normalizedCorrelation,
                        normalizedFrom,
                        normalizedTo,
                        PageRequest.of(page, size, NEWEST_FIRST)),
                this::globalResponse);
    }

    @Override
    public PagedResponse<TransactionAuditResponse> getTransactionTimeline(
            Long requestingUserId,
            Set<RoleName> authorities,
            Long transactionId,
            int page,
            int size) {

        requirePositiveId(requestingUserId, "requestingUserId");
        requirePositiveId(transactionId, "transactionId");
        requirePage(page, size);
        Set<RoleName> roles = Set.copyOf(
                Objects.requireNonNull(
                        authorities,
                        "authorities are required"));

        boolean privileged;
        if (roles.contains(RoleName.AUDITOR)) {
            requireActiveRole(requestingUserId, RoleName.AUDITOR);
            requireTransaction(transactionId);
            privileged = true;
        } else if (roles.contains(RoleName.RISK_OFFICER)) {
            requireActiveRole(requestingUserId, RoleName.RISK_OFFICER);
            if (!riskReviewDao.existsByTransaction_TransactionId(
                    transactionId)) {
                throw transactionNotFound();
            }
            privileged = true;
        } else if (roles.contains(RoleName.CUSTOMER)) {
            requireActiveRole(requestingUserId, RoleName.CUSTOMER);
            transactionDao.findOwnedById(
                    transactionId,
                    requestingUserId)
                    .orElseThrow(
                            AuditQueryServiceImpl::transactionNotFound);
            privileged = false;
        } else {
            throw accessDenied();
        }

        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                NEWEST_FIRST);
        Page<AuditLog> events = privileged
                ? auditLogDao.findAllByTransaction_TransactionId(
                        transactionId,
                        pageRequest)
                : auditLogDao.findCustomerVisibleTimeline(
                        transactionId,
                        CUSTOMER_SAFE_ACTIONS.stream().sorted().toList(),
                        pageRequest);

        boolean includePrivileged = privileged;
        return PagedResponse.from(
                events,
                event -> timelineResponse(
                        event,
                        includePrivileged));
    }

    private AuditLogResponse globalResponse(AuditLog event) {
        return new AuditLogResponse(
                id(event.getAuditLogId()),
                event.getEventReference(),
                event.getActorUser() == null
                        ? null
                        : id(event.getActorUser().getUserId()),
                event.getActorType(),
                event.getActorRoleCode(),
                event.getActionCode(),
                event.getEntityType(),
                id(event.getEntityId()),
                event.getTransaction() == null
                        ? null
                        : id(event.getTransaction()
                                .getTransactionId()),
                event.getPreviousState(),
                event.getNewState(),
                event.getOutcome(),
                event.getReasonCode(),
                event.getCorrelationId(),
                detailSanitizer.sanitize(event.getDetailsJson()),
                event.getOccurredAt());
    }

    private TransactionAuditResponse timelineResponse(
            AuditLog event,
            boolean privileged) {
        AuditSafeDetails details = privileged
                ? detailSanitizer.sanitize(event.getDetailsJson())
                : null;

        return new TransactionAuditResponse(
                id(event.getAuditLogId()),
                privileged ? event.getEventReference() : null,
                event.getActionCode(),
                event.getPreviousState(),
                event.getNewState(),
                event.getOutcome(),
                privileged ? event.getReasonCode() : null,
                privileged ? event.getActorType() : null,
                privileged ? event.getActorRoleCode() : null,
                details,
                event.getOccurredAt());
    }

    private User requireActiveRole(Long userId, RoleName role) {
        requirePositiveId(userId, "userId");
        User user = userDao.findById(userId)
                .orElseThrow(AuditQueryServiceImpl::accessDenied);
        if (user.getStatus() != UserStatus.ACTIVE
                || !userRoleDao
                        .existsByUser_UserIdAndRole_RoleCode(
                                userId,
                                role)) {
            throw accessDenied();
        }
        return user;
    }

    private void requireTransaction(Long transactionId) {
        if (!transactionDao.existsByTransactionId(transactionId)) {
            throw transactionNotFound();
        }
    }

    private static <E extends Enum<E>> E parseEnum(
            String value,
            Class<E> enumType,
            String fieldName) {
        String normalized = normalizeUppercase(
                value,
                fieldName,
                40);
        if (normalized == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    fieldName + " is invalid");
        }
    }

    private static String normalizeUppercase(
            String value,
            String fieldName,
            int maximumLength) {
        String normalized = normalizeText(
                value,
                fieldName,
                maximumLength);
        return normalized == null
                ? null
                : normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalizeText(
            String value,
            String fieldName,
            int maximumLength) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " has an invalid length");
        }
        return normalized;
    }

    private static OffsetDateTime utc(OffsetDateTime value) {
        return value == null
                ? null
                : value.withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static String id(Long value) {
        return value == null ? null : value.toString();
    }

    private static void requirePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "size must be between 1 and "
                            + MAX_PAGE_SIZE);
        }
    }

    private static void requirePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive");
        }
    }

    private static void requireOptionalPositiveId(
            Long value,
            String fieldName) {
        if (value != null) {
            requirePositiveId(value, fieldName);
        }
    }

    private static AccessDeniedException accessDenied() {
        return new AccessDeniedException(
                "The audit resource is not available to this user");
    }

    private static ResourceNotFoundExcp transactionNotFound() {
        return new ResourceNotFoundExcp(
                "TRANSACTION_NOT_FOUND",
                "Transaction was not found");
    }
}
