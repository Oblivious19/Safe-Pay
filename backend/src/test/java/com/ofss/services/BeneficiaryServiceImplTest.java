package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.ofss.beans.Beneficiary;
import com.ofss.beans.BeneficiaryPaymentMethod;
import com.ofss.beans.BeneficiaryStatus;
import com.ofss.beans.User;
import com.ofss.dto.beneficiary.BeneficiaryResponse;
import com.ofss.dto.beneficiary.CreateBeneficiaryRequest;
import com.ofss.dto.beneficiary.UpdateBeneficiaryStatusRequest;
import com.ofss.excp.BusinessRuleException;
import com.ofss.excp.DuplicateResourceExcp;
import com.ofss.excp.ResourceNotFoundExcp;
import com.ofss.repository.BeneficiaryDao;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class BeneficiaryServiceImplTest {

    private static final Long OWNER_ID = 101L;
    private static final Long BENEFICIARY_ID = 501L;

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-14T13:00:00Z"),
            ZoneOffset.UTC);

    private static final OffsetDateTime FIXED_TIME =
            OffsetDateTime.now(FIXED_CLOCK);

    @Mock
    private BeneficiaryDao beneficiaryDao;

    @Mock
    private UserService userService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private User owner;

    private BeneficiaryService beneficiaryService;

    @BeforeEach
    void setUp() {
        beneficiaryService = new BeneficiaryServiceImpl(
                beneficiaryDao,
                userService,
                entityManager,
                FIXED_CLOCK);
    }

    @Test
    void createsOwnedBankAccountBeneficiary() {
        CreateBeneficiaryRequest request = bankRequest();
        Beneficiary persisted = bankBeneficiary();

        preparePersistedOwner();

        when(beneficiaryDao.save(any(Beneficiary.class)))
                .thenReturn(persisted);

        BeneficiaryResponse response = beneficiaryService
                .createOwnedBeneficiary(OWNER_ID, request);

        ArgumentCaptor<Beneficiary> captor =
                ArgumentCaptor.forClass(Beneficiary.class);

        verify(beneficiaryDao).save(captor.capture());
        verify(entityManager).flush();

        Beneficiary created = captor.getValue();

        assertThat(created.getOwner()).isSameAs(owner);
        assertThat(created.getBeneficiaryName())
                .isEqualTo("Demo Supplier");
        assertThat(created.getPaymentMethod())
                .isEqualTo(
                        BeneficiaryPaymentMethod.BANK_ACCOUNT);
        assertThat(created.getBankAccountNumber())
                .isEqualTo("123456789012");
        assertThat(created.getIfscCode())
                .isEqualTo("ABCD0123456");
        assertThat(created.getStatus())
                .isEqualTo(BeneficiaryStatus.ACTIVE);
        assertThat(created.getCreatedAt())
                .isEqualTo(FIXED_TIME);

        assertThat(response.beneficiaryId())
                .isEqualTo("501");
        assertThat(response.maskedDestinationIdentifier())
                .isEqualTo("********9012");
    }

    @Test
    void createsOwnedUpiBeneficiary() {
        CreateBeneficiaryRequest request = upiRequest();
        Beneficiary persisted = upiBeneficiary();

        preparePersistedOwner();

        when(beneficiaryDao.save(any(Beneficiary.class)))
                .thenReturn(persisted);

        BeneficiaryResponse response = beneficiaryService
                .createOwnedBeneficiary(OWNER_ID, request);

        ArgumentCaptor<Beneficiary> captor =
                ArgumentCaptor.forClass(Beneficiary.class);

        verify(beneficiaryDao).save(captor.capture());
        verify(entityManager).flush();

        Beneficiary created = captor.getValue();

        assertThat(created.getPaymentMethod())
                .isEqualTo(BeneficiaryPaymentMethod.UPI);
        assertThat(created.getUpiId())
                .isEqualTo("merchant.pay@examplebank");
        assertThat(created.getBankAccountNumber()).isNull();

        assertThat(response.maskedDestinationIdentifier())
                .isEqualTo("m**********y@examplebank");
    }

    @Test
    void rejectsExistingOwnedBankAccountBeneficiary() {
        preparePersistedOwner();

        when(beneficiaryDao
                .existsByOwner_UserIdAndBankAccountNumberAndIfscCode(
                        OWNER_ID,
                        "123456789012",
                        "ABCD0123456"))
                .thenReturn(true);

        assertDuplicateConflict(() -> beneficiaryService
                .createOwnedBeneficiary(
                        OWNER_ID,
                        bankRequest()));

        verify(beneficiaryDao, never())
                .save(any(Beneficiary.class));
        verifyNoInteractions(entityManager);
    }

    @Test
    void rejectsExistingOwnedUpiBeneficiary() {
        preparePersistedOwner();

        when(beneficiaryDao.existsByOwner_UserIdAndUpiId(
                OWNER_ID,
                "merchant.pay@examplebank"))
                .thenReturn(true);

        assertDuplicateConflict(() -> beneficiaryService
                .createOwnedBeneficiary(
                        OWNER_ID,
                        upiRequest()));

        verify(beneficiaryDao, never())
                .save(any(Beneficiary.class));
        verifyNoInteractions(entityManager);
    }

    @Test
    void translatesConcurrentOracleDuplicateConstraint() {
        preparePersistedOwner();

        when(beneficiaryDao.save(any(Beneficiary.class)))
                .thenReturn(mock(Beneficiary.class));

        DataIntegrityViolationException databaseFailure =
                new DataIntegrityViolationException(
                        "insert failed",
                        new RuntimeException(
                                "ORA-00001: unique constraint "
                                        + "(SAFEPAY_OWNER."
                                        + "UK_BEN_OWNER_UPI) violated"));

        doThrow(databaseFailure)
                .when(entityManager)
                .flush();

        assertThatThrownBy(() -> beneficiaryService
                .createOwnedBeneficiary(
                        OWNER_ID,
                        upiRequest()))
                .isInstanceOf(DuplicateResourceExcp.class)
                .satisfies(exception -> {
                    DuplicateResourceExcp conflict =
                            (DuplicateResourceExcp) exception;

                    assertThat(conflict.getErrorCode())
                            .isEqualTo(
                                    "BENEFICIARY_ALREADY_EXISTS");
                    assertThat(conflict.getCause())
                            .isSameAs(databaseFailure);
                });
    }

    @Test
    void doesNotMislabelUnrelatedIntegrityFailure() {
        preparePersistedOwner();

        when(beneficiaryDao.save(any(Beneficiary.class)))
                .thenReturn(mock(Beneficiary.class));

        DataIntegrityViolationException databaseFailure =
                new DataIntegrityViolationException(
                        "ORA-02291: foreign key violated");

        doThrow(databaseFailure)
                .when(entityManager)
                .flush();

        assertThatThrownBy(() -> beneficiaryService
                .createOwnedBeneficiary(
                        OWNER_ID,
                        upiRequest()))
                .isSameAs(databaseFailure);
    }

    @Test
    void listsOnlyBeneficiariesReturnedByOwnershipQuery() {
        Beneficiary bank = bankBeneficiary();
        Beneficiary upi = upiBeneficiary(502L);

        when(beneficiaryDao
                .findAllByOwner_UserIdOrderByBeneficiaryIdAsc(
                        OWNER_ID))
                .thenReturn(List.of(bank, upi));

        List<BeneficiaryResponse> responses =
                beneficiaryService.listOwnedBeneficiaries(
                        OWNER_ID);

        assertThat(responses)
                .extracting(BeneficiaryResponse::beneficiaryId)
                .containsExactly("501", "502");
    }

    @Test
    void retrievesOwnedBeneficiary() {
        Beneficiary beneficiary = bankBeneficiary();

        when(beneficiaryDao
                .findByBeneficiaryIdAndOwner_UserId(
                        BENEFICIARY_ID,
                        OWNER_ID))
                .thenReturn(Optional.of(beneficiary));

        BeneficiaryResponse response = beneficiaryService
                .getOwnedBeneficiary(
                        OWNER_ID,
                        BENEFICIARY_ID);

        assertThat(response.beneficiaryId())
                .isEqualTo("501");
        assertThat(response.maskedDestinationIdentifier())
                .isEqualTo("********9012");
    }

    @Test
    void hidesMissingOrUnownedBeneficiaryBehindNotFound() {
        when(beneficiaryDao
                .findByBeneficiaryIdAndOwner_UserId(
                        BENEFICIARY_ID,
                        OWNER_ID))
                .thenReturn(Optional.empty());

        assertBeneficiaryNotFound(() -> beneficiaryService
                .getOwnedBeneficiary(
                        OWNER_ID,
                        BENEFICIARY_ID));
    }

    @Test
    void disablesOwnedBeneficiaryUsingWriteLock() {
        Beneficiary beneficiary = bankBeneficiary();

        when(beneficiaryDao.findOwnedByIdForUpdate(
                BENEFICIARY_ID,
                OWNER_ID))
                .thenReturn(Optional.of(beneficiary));

        beneficiaryService.updateOwnedBeneficiaryStatus(
                OWNER_ID,
                BENEFICIARY_ID,
                new UpdateBeneficiaryStatusRequest(
                        BeneficiaryStatus.DISABLED));

        verify(beneficiary).disable(FIXED_TIME);
        verify(beneficiary, never()).enable(any());
    }

    @Test
    void enablesOwnedBeneficiaryUsingWriteLock() {
        Beneficiary beneficiary = upiBeneficiary();

        when(beneficiaryDao.findOwnedByIdForUpdate(
                BENEFICIARY_ID,
                OWNER_ID))
                .thenReturn(Optional.of(beneficiary));

        beneficiaryService.updateOwnedBeneficiaryStatus(
                OWNER_ID,
                BENEFICIARY_ID,
                new UpdateBeneficiaryStatusRequest(
                        BeneficiaryStatus.ACTIVE));

        verify(beneficiary).enable(FIXED_TIME);
        verify(beneficiary, never()).disable(any());
    }

    @Test
    void statusChangeHidesMissingOrUnownedBeneficiary() {
        when(beneficiaryDao.findOwnedByIdForUpdate(
                BENEFICIARY_ID,
                OWNER_ID))
                .thenReturn(Optional.empty());

        assertBeneficiaryNotFound(() -> beneficiaryService
                .updateOwnedBeneficiaryStatus(
                        OWNER_ID,
                        BENEFICIARY_ID,
                        new UpdateBeneficiaryStatusRequest(
                                BeneficiaryStatus.DISABLED)));
    }

    @Test
    void returnsActiveOwnedBeneficiaryForPaymentUse() {
        Beneficiary beneficiary = mock(Beneficiary.class);

        when(beneficiaryDao
                .findByBeneficiaryIdAndOwner_UserId(
                        BENEFICIARY_ID,
                        OWNER_ID))
                .thenReturn(Optional.of(beneficiary));
        when(beneficiary.canReceiveNewPayment())
                .thenReturn(true);

        assertThat(beneficiaryService
                .getRequiredActiveOwnedBeneficiary(
                        OWNER_ID,
                        BENEFICIARY_ID))
                .isSameAs(beneficiary);
    }

    @Test
    void rejectsDisabledOwnedBeneficiaryForPaymentUse() {
        Beneficiary beneficiary = mock(Beneficiary.class);

        when(beneficiaryDao
                .findByBeneficiaryIdAndOwner_UserId(
                        BENEFICIARY_ID,
                        OWNER_ID))
                .thenReturn(Optional.of(beneficiary));
        when(beneficiary.canReceiveNewPayment())
                .thenReturn(false);

        assertThatThrownBy(() -> beneficiaryService
                .getRequiredActiveOwnedBeneficiary(
                        OWNER_ID,
                        BENEFICIARY_ID))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(exception -> {
                    BusinessRuleException ruleFailure =
                            (BusinessRuleException) exception;

                    assertThat(ruleFailure.getErrorCode())
                            .isEqualTo("BENEFICIARY_DISABLED");
                });
    }

    @Test
    void rejectsInvalidOwnerIdBeforeCollaboratorAccess() {
        assertThatThrownBy(() -> beneficiaryService
                .listOwnedBeneficiaries(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ownerUserId must be positive");

        verifyNoInteractions(
                beneficiaryDao,
                userService,
                entityManager);
    }

    @Test
    void rejectsInvalidBeneficiaryIdBeforeRepositoryAccess() {
        assertThatThrownBy(() -> beneficiaryService
                .getOwnedBeneficiary(OWNER_ID, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("beneficiaryId must be positive");

        verifyNoInteractions(
                beneficiaryDao,
                userService,
                entityManager);
    }

    @Test
    void rejectsNullCreateRequestBeforeCollaboratorAccess() {
        assertThatThrownBy(() -> beneficiaryService
                .createOwnedBeneficiary(OWNER_ID, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request is required");

        verifyNoInteractions(
                beneficiaryDao,
                userService,
                entityManager);
    }

    @Test
    void rejectsMissingPaymentMethodAtServiceBoundary() {
        CreateBeneficiaryRequest invalidRequest =
                new CreateBeneficiaryRequest(
                        "Demo Beneficiary",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);

        assertThatThrownBy(() -> beneficiaryService
                .createOwnedBeneficiary(
                        OWNER_ID,
                        invalidRequest))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("paymentMethod is required");

        verifyNoInteractions(
                beneficiaryDao,
                userService,
                entityManager);
    }

    @Test
    void rejectsInvalidPaymentDetailCombinationAtServiceBoundary() {
        CreateBeneficiaryRequest invalidRequest =
                new CreateBeneficiaryRequest(
                        "Demo Supplier",
                        null,
                        BeneficiaryPaymentMethod.BANK_ACCOUNT,
                        "SafePay Demo Bank",
                        "123456789012",
                        "ABCD0123456",
                        "merchant@examplebank",
                        null,
                        null);

        assertThatThrownBy(() -> beneficiaryService
                .createOwnedBeneficiary(
                        OWNER_ID,
                        invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "payment details must match paymentMethod");

        verifyNoInteractions(
                beneficiaryDao,
                userService,
                entityManager);
    }

    @Test
    void rejectsMissingStatusAtServiceBoundary() {
        UpdateBeneficiaryStatusRequest invalidRequest =
                new UpdateBeneficiaryStatusRequest(null);

        assertThatThrownBy(() -> beneficiaryService
                .updateOwnedBeneficiaryStatus(
                        OWNER_ID,
                        BENEFICIARY_ID,
                        invalidRequest))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status is required");

        verifyNoInteractions(
                beneficiaryDao,
                userService,
                entityManager);
    }

    private void preparePersistedOwner() {
        when(userService.getRequiredUser(OWNER_ID))
                .thenReturn(owner);
        when(owner.getUserId()).thenReturn(OWNER_ID);
    }

    private CreateBeneficiaryRequest bankRequest() {
        return new CreateBeneficiaryRequest(
                "Demo Supplier",
                "Office Vendor",
                BeneficiaryPaymentMethod.BANK_ACCOUNT,
                "SafePay Demo Bank",
                "123456789012",
                "ABCD0123456",
                null,
                "Supplier",
                "Monthly invoice");
    }

    private CreateBeneficiaryRequest upiRequest() {
        return new CreateBeneficiaryRequest(
                "Demo Merchant",
                null,
                BeneficiaryPaymentMethod.UPI,
                null,
                null,
                null,
                "Merchant.Pay@ExampleBank",
                null,
                null);
    }

    private Beneficiary bankBeneficiary() {
        Beneficiary beneficiary = mock(Beneficiary.class);

        when(beneficiary.getBeneficiaryId())
                .thenReturn(BENEFICIARY_ID);
        when(beneficiary.getBeneficiaryName())
                .thenReturn("Demo Supplier");
        when(beneficiary.getPaymentMethod())
                .thenReturn(
                        BeneficiaryPaymentMethod.BANK_ACCOUNT);
        when(beneficiary.getBankName())
                .thenReturn("SafePay Demo Bank");
        when(beneficiary.getBankAccountNumber())
                .thenReturn("123456789012");
        when(beneficiary.getIfscCode())
                .thenReturn("ABCD0123456");
        when(beneficiary.getStatus())
                .thenReturn(BeneficiaryStatus.ACTIVE);
        when(beneficiary.getCreatedAt()).thenReturn(FIXED_TIME);
        when(beneficiary.getUpdatedAt()).thenReturn(FIXED_TIME);

        return beneficiary;
    }

    private Beneficiary upiBeneficiary() {
        return upiBeneficiary(BENEFICIARY_ID);
    }

    private Beneficiary upiBeneficiary(Long beneficiaryId) {
        Beneficiary beneficiary = mock(Beneficiary.class);

        when(beneficiary.getBeneficiaryId())
                .thenReturn(beneficiaryId);
        when(beneficiary.getBeneficiaryName())
                .thenReturn("Demo Merchant");
        when(beneficiary.getPaymentMethod())
                .thenReturn(BeneficiaryPaymentMethod.UPI);
        when(beneficiary.getUpiId())
                .thenReturn("merchant.pay@examplebank");
        when(beneficiary.getStatus())
                .thenReturn(BeneficiaryStatus.ACTIVE);
        when(beneficiary.getCreatedAt()).thenReturn(FIXED_TIME);
        when(beneficiary.getUpdatedAt()).thenReturn(FIXED_TIME);

        return beneficiary;
    }

    private void assertDuplicateConflict(
            Runnable operation) {

        assertThatThrownBy(operation::run)
                .isInstanceOf(DuplicateResourceExcp.class)
                .satisfies(exception -> {
                    DuplicateResourceExcp conflict =
                            (DuplicateResourceExcp) exception;

                    assertThat(conflict.getErrorCode())
                            .isEqualTo(
                                    "BENEFICIARY_ALREADY_EXISTS");
                });
    }

    private void assertBeneficiaryNotFound(
            Runnable operation) {

        assertThatThrownBy(operation::run)
                .isInstanceOf(ResourceNotFoundExcp.class)
                .satisfies(exception -> {
                    ResourceNotFoundExcp notFound =
                            (ResourceNotFoundExcp) exception;

                    assertThat(notFound.getErrorCode())
                            .isEqualTo("BENEFICIARY_NOT_FOUND");
                });
    }
}
