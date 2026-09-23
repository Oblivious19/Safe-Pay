package com.ofss.services;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.Beneficiary;
import com.ofss.beans.BeneficiaryPaymentMethod;
import com.ofss.beans.BeneficiaryStatus;
import com.ofss.beans.Account;
import com.ofss.beans.AccountStatus;
import com.ofss.beans.AccountType;
import com.ofss.beans.User;
import com.ofss.dto.beneficiary.BeneficiaryResponse;
import com.ofss.dto.beneficiary.CreateBeneficiaryRequest;
import com.ofss.dto.beneficiary.UpdateBeneficiaryStatusRequest;
import com.ofss.excp.BusinessRuleException;
import com.ofss.excp.DuplicateResourceExcp;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.BeneficiaryDao;
import com.ofss.repository.AccountDao;

import jakarta.persistence.EntityManager;

@Service
@Transactional(readOnly = true)
public class BeneficiaryServiceImpl
        implements BeneficiaryService {

    private static final String BANK_DUPLICATE_CONSTRAINT =
            "UX_BEN_OWNER_BANK_DEST";

    private static final String UPI_DUPLICATE_CONSTRAINT =
            "UX_BEN_OWNER_UPI_DEST";

    private final BeneficiaryDao beneficiaryDao;
    private final AccountDao accountDao;
    private final UserService userService;
    private final EntityManager entityManager;
    private final Clock clock;

    public BeneficiaryServiceImpl(
            BeneficiaryDao beneficiaryDao,
            AccountDao accountDao,
            UserService userService,
            EntityManager entityManager,
            Clock clock) {

        this.beneficiaryDao = beneficiaryDao;
        this.accountDao = accountDao;
        this.userService = userService;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Override
    @Transactional
    public BeneficiaryResponse createOwnedBeneficiary(
            Long ownerUserId,
            CreateBeneficiaryRequest request) {

        requirePositiveId(ownerUserId, "ownerUserId");
        validateCreateRequest(request);

        User owner = userService.getRequiredUser(ownerUserId);
        Account destinationAccount = resolveVerifiedDestinationAccount(request);

        Beneficiary beneficiary = createBeneficiary(
                owner,
                request,
                destinationAccount,
                currentUtcTime());

        rejectExistingBeneficiary(
                ownerUserId,
                beneficiary);

        Beneficiary persistedBeneficiary =
                persistAndDetectDuplicateRace(beneficiary);

        return BeneficiaryResponse.from(
                persistedBeneficiary);
    }

    @Override
    public List<BeneficiaryResponse> listOwnedBeneficiaries(
            Long ownerUserId) {

        requirePositiveId(ownerUserId, "ownerUserId");

        return beneficiaryDao
                .findAllByOwner_UserIdOrderByBeneficiaryIdAsc(
                        ownerUserId)
                .stream()
                .map(BeneficiaryResponse::from)
                .toList();
    }

    @Override
    public BeneficiaryResponse getOwnedBeneficiary(
            Long ownerUserId,
            Long beneficiaryId) {

        return BeneficiaryResponse.from(
                getRequiredOwnedBeneficiary(
                        ownerUserId,
                        beneficiaryId));
    }

    @Override
    @Transactional
    public BeneficiaryResponse updateOwnedBeneficiaryStatus(
            Long ownerUserId,
            Long beneficiaryId,
            UpdateBeneficiaryStatusRequest request) {

        requirePositiveId(ownerUserId, "ownerUserId");
        requirePositiveId(beneficiaryId, "beneficiaryId");
        Objects.requireNonNull(request, "request is required");

        BeneficiaryStatus requestedStatus =
                Objects.requireNonNull(
                        request.status(),
                        "status is required");

        Beneficiary beneficiary = beneficiaryDao
                .findOwnedByIdForUpdate(
                        beneficiaryId,
                        ownerUserId)
                .orElseThrow(
                        BeneficiaryServiceImpl::beneficiaryNotFound);

        OffsetDateTime changedAt = currentUtcTime();

        switch (requestedStatus) {
            case ACTIVE -> beneficiary.enable(changedAt);
            case DISABLED -> beneficiary.disable(changedAt);
        }

        return BeneficiaryResponse.from(beneficiary);
    }

    @Override
    public Beneficiary getRequiredActiveOwnedBeneficiary(
            Long ownerUserId,
            Long beneficiaryId) {

        Beneficiary beneficiary = getRequiredOwnedBeneficiary(
                ownerUserId,
                beneficiaryId);

        if (!beneficiary.canReceiveNewPayment()) {
            throw new BusinessRuleException(
                    "BENEFICIARY_DISABLED",
                    "Beneficiary is disabled");
        }

        return beneficiary;
    }

    private Beneficiary createBeneficiary(
            User owner,
            CreateBeneficiaryRequest request,
            Account destinationAccount,
            OffsetDateTime createdAt) {

        return switch (request.paymentMethod()) {
            case BANK_ACCOUNT ->
                    Beneficiary.createVerifiedBankAccountBeneficiary(
                            owner,
                            request.beneficiaryName(),
                            request.nickname(),
                            request.bankName(),
                            request.bankAccountNumber(),
                            request.ifscCode(),
                            request.relationshipLabel(),
                            request.purposeNote(),
                            destinationAccount,
                            createdAt);

            case UPI -> Beneficiary.createUpiBeneficiary(
                    owner,
                    request.beneficiaryName(),
                    request.nickname(),
                    request.upiId(),
                    request.relationshipLabel(),
                    request.purposeNote(),
                    createdAt);
        };
    }

    private Account resolveVerifiedDestinationAccount(
            CreateBeneficiaryRequest request) {

        if (request.paymentMethod() != BeneficiaryPaymentMethod.BANK_ACCOUNT) {
            return null;
        }

        return accountDao.findByAccountNumber(request.bankAccountNumber())
                .filter(account -> matchesVerifiedSafePayRecipient(account, request))
                .orElseThrow(BeneficiaryServiceImpl::safePayRecipientNotFound);
    }

    private static boolean matchesVerifiedSafePayRecipient(
            Account account,
            CreateBeneficiaryRequest request) {

        return account.getAccountType() != null
                && (account.getAccountType() == AccountType.SAVINGS
                        || account.getAccountType() == AccountType.CURRENT)
                && account.getStatus() == AccountStatus.ACTIVE
                && account.getOwner() != null
                && account.getOwner().getFullName() != null
                && normalizeForComparison(account.getOwner().getFullName())
                        .equals(normalizeForComparison(request.beneficiaryName()))
                && normalizeForComparison(account.getBankName())
                        .equals(normalizeForComparison(request.bankName()))
                && Objects.equals(account.getIfscCode(), request.ifscCode());
    }

    private static String normalizeForComparison(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private void rejectExistingBeneficiary(
            Long ownerUserId,
            Beneficiary beneficiary) {

        boolean alreadyExists = switch (
                beneficiary.getPaymentMethod()) {

            case BANK_ACCOUNT -> beneficiaryDao
                    .existsByOwner_UserIdAndBankAccountNumberAndIfscCode(
                            ownerUserId,
                            beneficiary.getBankAccountNumber(),
                            beneficiary.getIfscCode());

            case UPI -> beneficiaryDao
                    .existsByOwner_UserIdAndUpiId(
                            ownerUserId,
                            beneficiary.getUpiId());
        };

        if (alreadyExists) {
            throw duplicateBeneficiary(null);
        }
    }

    private Beneficiary persistAndDetectDuplicateRace(
            Beneficiary beneficiary) {

        try {
            Beneficiary persistedBeneficiary =
                    beneficiaryDao.save(beneficiary);

            /*
             * Force the INSERT so either Oracle uniqueness failure
             * is translated inside this transaction boundary.
             */
            entityManager.flush();

            return persistedBeneficiary;
        } catch (DataIntegrityViolationException exception) {
            if (referencesDuplicateConstraint(exception)) {
                throw duplicateBeneficiary(exception);
            }

            throw exception;
        }
    }

    private boolean referencesDuplicateConstraint(
            Throwable exception) {

        Throwable current = exception;

        while (current != null) {
            String message = current.getMessage();

            if (message != null) {
                String normalizedMessage =
                        message.toUpperCase(Locale.ROOT);

                if (normalizedMessage.contains(
                        BANK_DUPLICATE_CONSTRAINT)
                        || normalizedMessage.contains(
                                UPI_DUPLICATE_CONSTRAINT)) {
                    return true;
                }
            }

            Throwable next = current.getCause();

            if (next == current) {
                break;
            }

            current = next;
        }

        return false;
    }

    private Beneficiary getRequiredOwnedBeneficiary(
            Long ownerUserId,
            Long beneficiaryId) {

        requirePositiveId(ownerUserId, "ownerUserId");
        requirePositiveId(beneficiaryId, "beneficiaryId");

        return beneficiaryDao
                .findByBeneficiaryIdAndOwner_UserId(
                        beneficiaryId,
                        ownerUserId)
                .orElseThrow(
                        BeneficiaryServiceImpl::beneficiaryNotFound);
    }

    private void validateCreateRequest(
            CreateBeneficiaryRequest request) {

        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(
                request.paymentMethod(),
                "paymentMethod is required");

        if (!request.isPaymentDetailsValid()) {
            throw new IllegalArgumentException(
                    "payment details must match paymentMethod");
        }
    }

    private OffsetDateTime currentUtcTime() {
        return OffsetDateTime
                .now(clock)
                .withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static DuplicateResourceExcp duplicateBeneficiary(
            Throwable cause) {

        return new DuplicateResourceExcp(
                "BENEFICIARY_ALREADY_EXISTS",
                "Beneficiary already exists",
                cause);
    }

    private static ResourceNotFoundExcp beneficiaryNotFound() {
        return new ResourceNotFoundExcp(
                "BENEFICIARY_NOT_FOUND",
                "Beneficiary was not found");
    }

    private static BusinessRuleException safePayRecipientNotFound() {
        return new BusinessRuleException(
                "SAFE_PAY_RECIPIENT_NOT_FOUND",
                "This account is not registered with SafePay, or its details do not match.");
    }

    private void requirePositiveId(
            Long value,
            String fieldName) {

        if (value == null || value <= 0L) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive");
        }
    }
}
