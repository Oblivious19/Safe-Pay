package com.safepay.risk;

import java.math.BigDecimal;

public record RiskContext(BigDecimal amount, long beneficiaryAgeHours,
                          boolean firstPaymentToBeneficiary, BigDecimal averageMonthlySpend) { }
