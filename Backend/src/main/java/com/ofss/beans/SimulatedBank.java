package com.ofss.beans;

import java.util.Locale;

/** Bank identity derived from the existing IFSC field; no schema change. */
public enum SimulatedBank {
    SAFE_PAY("SAFE", "SafePay Simulated Bank", "SAFE0000001"),
    HDFC("HDFC", "HDFC Bank", null),
    ICICI("ICIC", "ICICI Bank", null),
    SBI("SBIN", "State Bank of India", null),
    AXIS("UTIB", "Axis Bank", null),
    OTHER("", "Other simulated bank", null);

    private final String ifscPrefix;
    private final String displayName;
    private final String standardIfsc;

    SimulatedBank(String ifscPrefix, String displayName, String standardIfsc) {
        this.ifscPrefix = ifscPrefix;
        this.displayName = displayName;
        this.standardIfsc = standardIfsc;
    }

    public String displayName() { return displayName; }
    public String standardIfsc() { return standardIfsc; }

    public static SimulatedBank fromIfsc(String ifsc) {
        String normalized = ifsc == null ? "" : ifsc.strip().toUpperCase(Locale.ROOT);
        for (SimulatedBank bank : values()) {
            if (!bank.ifscPrefix.isEmpty() && normalized.startsWith(bank.ifscPrefix)) return bank;
        }
        return OTHER;
    }
}
