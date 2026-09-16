package com.ofss.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import com.ofss.beans.Beneficiary;
import com.ofss.beans.RiskAssessmentInput;
import com.ofss.beans.RiskContextSignal;
import com.ofss.repository.TransactionDao;

/** Builds evidence only; all risk scoring belongs to RiskAssessmentEngine. */
public class TransactionRiskInputBuilder {
    private final TransactionDao transactions;
    private final Clock clock;
    private final ZoneId databaseZone;

    public TransactionRiskInputBuilder(TransactionDao transactions) {
        // Existing entities write LocalDateTime.now() into timezone-less Oracle TIMESTAMP.
        this(transactions, Clock.systemUTC(), ZoneId.systemDefault());
    }

    TransactionRiskInputBuilder(TransactionDao transactions, Clock clock, ZoneId databaseZone) {
        this.transactions = transactions;
        this.clock = clock;
        this.databaseZone = databaseZone;
    }

    public RiskAssessmentInput build(Long callerId, Beneficiary beneficiary, BigDecimal amount) {
        var evaluatedAt = clock.instant();
        var createdAt = Objects.requireNonNull(beneficiary.getCreatedAt(), "Beneficiary creation time is missing")
                .atZone(databaseZone).toInstant();
        long prior = transactions.settledPaymentsToBeneficiary(callerId, beneficiary.getBeneficiaryId());
        var amounts = Objects.requireNonNull(transactions.recentSettledAmounts(callerId,
                LocalDateTime.ofInstant(evaluatedAt.minus(Duration.ofDays(30)), databaseZone),
                LocalDateTime.ofInstant(evaluatedAt, databaseZone)), "Payment history is unavailable");
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : amounts) {
            if (value == null || value.signum() <= 0) {
                throw new IllegalStateException("Invalid settled-payment history");
            }
            sum = sum.add(value);
        }
        // Decimal arithmetic, not SQL AVG (which Hibernate can expose as Double).
        // Round upward at 18 fractional places so a repeating mean does not turn
        // exact 3x equality into an unusual amount. With cent-valued amounts and
        // a Java List's bounded size, this cannot hide a genuine cent-level excess.
        BigDecimal average = amounts.isEmpty() ? null
                : sum.divide(BigDecimal.valueOf(amounts.size()), 18, RoundingMode.CEILING);
        return new RiskAssessmentInput(amount, createdAt, evaluatedAt, prior, amounts.size(), average,
                RiskContextSignal.UNKNOWN, RiskContextSignal.UNKNOWN);
    }
}
