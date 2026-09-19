package com.ofss.services;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.ofss.beans.RiskPolicyBand;

public interface RiskPolicyService {

    RiskPolicyBand resolveRequiredBand(
            BigDecimal amount,
            OffsetDateTime evaluatedAt);
}
