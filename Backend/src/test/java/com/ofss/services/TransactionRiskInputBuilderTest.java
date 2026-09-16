package com.ofss.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.ofss.beans.*;
import com.ofss.repository.TransactionDao;

class TransactionRiskInputBuilderTest {
    private final TransactionDao dao = mock(TransactionDao.class);
    private final Instant now = Instant.parse("2026-09-13T06:00:00Z");
    private final ZoneId zone = ZoneId.of("Asia/Kolkata");
    private final TransactionRiskInputBuilder builder = new TransactionRiskInputBuilder(
            dao, Clock.fixed(now, ZoneOffset.UTC), zone);

    private Beneficiary beneficiary() {
        Beneficiary result = new Beneficiary(); result.setBeneficiaryId(2001L);
        result.setCreatedAt(LocalDateTime.ofInstant(now.minus(Duration.ofDays(2)), zone));
        return result;
    }

    @Test
    void ownerScopedHistoryAndDatabaseTimesBuildTrustedSnapshot() {
        when(dao.settledPaymentsToBeneficiary(103L, 2001L)).thenReturn(7L);
        when(dao.recentSettledAmounts(eq(103L), any(), any()))
                .thenReturn(List.of(new BigDecimal("100.01"), new BigDecimal("200.03")));
        var input = builder.build(103L, beneficiary(), new BigDecimal("5000"));
        assertEquals(7, input.settledPaymentsToBeneficiary());
        assertEquals(2, input.recentSettledPaymentCount());
        assertEquals(0, new BigDecimal("150.02").compareTo(input.recentAverageAmount()));
        assertEquals(now, input.evaluatedAt());
        assertEquals(now.minus(Duration.ofDays(2)), input.beneficiaryCreatedAt());
        assertEquals(RiskContextSignal.UNKNOWN, input.deviceSignal());
        assertEquals(RiskContextSignal.UNKNOWN, input.contextSignal());
        verify(dao).recentSettledAmounts(103L, LocalDateTime.ofInstant(now.minus(Duration.ofDays(30)), zone),
                LocalDateTime.ofInstant(now, zone));
        verify(dao).settledPaymentsToBeneficiary(103L, 2001L);
        verifyNoMoreInteractions(dao);
    }

    @Test
    void emptyHistoryRemainsExplicitlyMissingInCompatibilitySnapshot() {
        var input = builder.build(103L, beneficiary(), new BigDecimal("5000"));
        assertEquals(0, input.recentSettledPaymentCount());
        assertNull(input.recentAverageAmount());
        assertEquals(RiskContextSignal.UNKNOWN, input.deviceSignal());
        assertEquals(RiskContextSignal.UNKNOWN, input.contextSignal());
    }

    @Test
    void repeatingMeanPreservesConservativeDecimalSnapshot() {
        when(dao.recentSettledAmounts(anyLong(), any(), any())).thenReturn(
                List.of(new BigDecimal("0.33"), new BigDecimal("0.33"), new BigDecimal("0.34")));
        var first = builder.build(103L, beneficiary(), new BigDecimal("1.00"));
        var second = builder.build(103L, beneficiary(), new BigDecimal("1.01"));
        assertEquals(new BigDecimal("0.333333333333333334"), first.recentAverageAmount());
        assertEquals(first.recentAverageAmount(), second.recentAverageAmount());
        assertEquals(3, first.recentSettledPaymentCount());
        assertEquals(new BigDecimal("1.00"), first.amount());
        assertEquals(new BigDecimal("1.01"), second.amount());
    }

    @Test
    void compatibilityBuilderStillRejectsInvalidStoredHistory() {
        when(dao.recentSettledAmounts(anyLong(), any(), any())).thenReturn(List.of(BigDecimal.ZERO));
        assertThrows(IllegalStateException.class, () -> builder.build(103L, beneficiary(), BigDecimal.ONE));
    }

    @Test
    void missingCreationDateFailsInsteadOfFabricatingAge() {
        Beneficiary beneficiary = beneficiary(); beneficiary.setCreatedAt(null);
        assertThrows(NullPointerException.class, () -> builder.build(103L, beneficiary, BigDecimal.ONE));
        verifyNoInteractions(dao);
    }
}
