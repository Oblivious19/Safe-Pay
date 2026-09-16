package com.ofss.beans;

import java.math.BigDecimal;
import java.time.Instant;

/** Immutable trusted snapshot, not an HTTP request DTO. See PHASE1_SPEC.md section 2. */
public record RiskAssessmentInput(BigDecimal amount, Instant beneficiaryCreatedAt, Instant evaluatedAt,
        long settledPaymentsToBeneficiary, long recentSettledPaymentCount, BigDecimal recentAverageAmount,
        RiskContextSignal deviceSignal, RiskContextSignal contextSignal) {}
