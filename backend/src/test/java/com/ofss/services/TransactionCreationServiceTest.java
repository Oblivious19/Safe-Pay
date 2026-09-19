package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ofss.beans.Account;
import com.ofss.beans.AuditOutcome;
import com.ofss.beans.Beneficiary;
import com.ofss.beans.BeneficiaryPaymentMethod;
import com.ofss.beans.CurrencyCode;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.beans.RoleName;
import com.ofss.beans.User;
import com.ofss.beans.UserStatus;
import com.ofss.dto.transaction.CreateTransactionRequest;
import com.ofss.dto.transaction.TransactionResponse;
import com.ofss.excp.BusinessRuleException;
import com.ofss.repository.AccountDao;
import com.ofss.repository.OtpChallengeDao;
import com.ofss.repository.TransactionDao;
import com.ofss.repository.TransactionRiskFactorDao;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class TransactionCreationServiceTest {

    private static final Long CUSTOMER_ID = 7L;
    private static final Long ACCOUNT_ID = 70L;
    private static final Long BENEFICIARY_ID = 700L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-15T10:00:00.123456Z");

    @Mock private TransactionDao transactionDao;
    @Mock private TransactionRiskFactorDao riskFactorDao;
    @Mock private AccountDao accountDao;
    @Mock private OtpChallengeDao otpChallengeDao;
    @Mock private UserService userService;
    @Mock private AccountService accountService;
    @Mock private BeneficiaryService beneficiaryService;
    @Mock private AmountRiskEngine amountRiskEngine;
    @Mock private RiskReviewService riskReviewService;
    @Mock private TransactionLifecycleEvidenceService evidenceService;
    @Mock private EntityManager entityManager;
    @Mock private User customer;
    @Mock private Account sourceAccount;
    @Mock private Beneficiary beneficiary;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionServiceImpl(
                transactionDao,
                riskFactorDao,
                accountDao,
                otpChallengeDao,
                userService,
                accountService,
                beneficiaryService,
                amountRiskEngine,
                new TransactionStateServiceImpl(),
                riskReviewService,
                evidenceService,
                entityManager,
                Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
    }

    @Test
    void rejectsMissingHighValueCategoryBeforeResourceReadsOrWrites() {
        assertThatThrownBy(() -> service.createTransaction(CUSTOMER_ID, new CreateTransactionRequest(
                ACCOUNT_ID, BENEFICIARY_ID, new BigDecimal("100000.01"), null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("category is required");
        verifyNoInteractions(transactionDao, userService, accountService, beneficiaryService, entityManager);
    }

    @Test
    void createsOnlyAnUnassessedUnreservedInstruction() {
        arrangeEligibleEntities();
        when(transactionDao.save(any(TransactionDb.class)))
                .thenAnswer(invocation -> {
                    TransactionDb transaction = invocation.getArgument(0);
                    ReflectionTestUtils.setField(
                            transaction,
                            "transactionId",
                            1001L);
                    return transaction;
                });

        TransactionResponse response = service.createTransaction(
                CUSTOMER_ID,
                request());

        assertThat(response.transactionReference())
                .startsWith("SP-")
                .hasSize(35);
        assertThat(response.state()).isEqualTo(TransactionState.CREATED);
        assertThat(response.amount()).isEqualByComparingTo("2500.00");
        assertThat(response.reservedAmount()).isEqualByComparingTo("0.00");
        assertThat(response.riskTier()).isNull();
        assertThat(response.createdAt()).isEqualTo(NOW);
        verify(entityManager).flush();
        verify(evidenceService).appendUserEvent(
                eq(TransactionLifecycleEvent.PAYMENT_CREATED),
                any(TransactionDb.class),
                eq(customer),
                eq(RoleName.CUSTOMER),
                isNull(),
                eq(TransactionState.CREATED),
                eq(AuditOutcome.SUCCESS),
                isNull(),
                any(OperationContext.class),
                eq("CREATE"),
                eq(NOW));
        verifyNoInteractions(amountRiskEngine, accountDao, riskFactorDao);
    }

    @Test
    void normalizesOptionalTextThroughTheCanonicalEntityFactory() {
        arrangeEligibleEntities();
        when(transactionDao.save(any(TransactionDb.class)))
                .thenAnswer(invocation -> {
                    TransactionDb transaction = invocation.getArgument(0);
                    ReflectionTestUtils.setField(
                            transaction,
                            "transactionId",
                            1002L);
                    return transaction;
                });

        TransactionResponse response = service.createTransaction(
                CUSTOMER_ID,
                new CreateTransactionRequest(
                        ACCOUNT_ID,
                        BENEFICIARY_ID,
                        new BigDecimal("1.00"),
                        "   ",
                        null));

        assertThat(response.purpose()).isNull();
        assertThat(response.customerReference()).isNull();
    }

    @Test
    void rejectsInactiveCustomerBeforeReadingPaymentResources() {
        when(userService.getRequiredUser(CUSTOMER_ID))
                .thenReturn(customer);
        when(customer.getStatus()).thenReturn(UserStatus.DISABLED);

        assertThatThrownBy(() -> service.createTransaction(
                CUSTOMER_ID,
                request()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode")
                .isEqualTo("USER_INACTIVE");

        verifyNoInteractions(accountService, beneficiaryService);
        verify(transactionDao, never()).save(any());
    }

    @Test
    void propagatesOwnedAccountEligibilityFailureWithoutSaving() {
        when(userService.getRequiredUser(CUSTOMER_ID))
                .thenReturn(customer);
        when(customer.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(accountService.getRequiredActiveOwnedAccount(
                CUSTOMER_ID,
                ACCOUNT_ID))
                .thenThrow(new BusinessRuleException(
                        "ACCOUNT_INACTIVE",
                        "Account is inactive"));

        assertThatThrownBy(() -> service.createTransaction(
                CUSTOMER_ID,
                request()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode")
                .isEqualTo("ACCOUNT_INACTIVE");

        verifyNoInteractions(beneficiaryService);
        verify(transactionDao, never()).save(any());
    }

    @Test
    void rejectsInvalidBoundaryArgumentsBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.createTransaction(
                0L,
                request()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("customerUserId must be positive");

        assertThatThrownBy(() -> service.createTransaction(
                CUSTOMER_ID,
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request is required");

        verifyNoInteractions(
                transactionDao,
                userService,
                accountService,
                beneficiaryService);
    }

    private void arrangeEligibleEntities() {
        when(userService.getRequiredUser(CUSTOMER_ID))
                .thenReturn(customer);
        when(customer.getUserId()).thenReturn(CUSTOMER_ID);
        when(customer.getStatus()).thenReturn(UserStatus.ACTIVE);

        when(accountService.getRequiredActiveOwnedAccount(
                CUSTOMER_ID,
                ACCOUNT_ID))
                .thenReturn(sourceAccount);
        when(sourceAccount.getAccountId()).thenReturn(ACCOUNT_ID);
        when(sourceAccount.getOwner()).thenReturn(customer);
        when(sourceAccount.isCustomerOwnedAccount()).thenReturn(true);
        when(sourceAccount.getCurrencyCode()).thenReturn(CurrencyCode.INR);
        when(sourceAccount.getAccountNumber())
                .thenReturn("1234567890123456");

        when(beneficiaryService.getRequiredActiveOwnedBeneficiary(
                CUSTOMER_ID,
                BENEFICIARY_ID))
                .thenReturn(beneficiary);
        when(beneficiary.getBeneficiaryId())
                .thenReturn(BENEFICIARY_ID);
        when(beneficiary.getOwner()).thenReturn(customer);
        when(beneficiary.getBeneficiaryName())
                .thenReturn("Vendor One");
        when(beneficiary.getPaymentMethod())
                .thenReturn(BeneficiaryPaymentMethod.UPI);
        when(beneficiary.getUpiId()).thenReturn("vendor@upi");
    }

    private static CreateTransactionRequest request() {
        return new CreateTransactionRequest(
                ACCOUNT_ID,
                BENEFICIARY_ID,
                new BigDecimal("2500.00"),
                "Invoice payment",
                "INV-100");
    }
}
