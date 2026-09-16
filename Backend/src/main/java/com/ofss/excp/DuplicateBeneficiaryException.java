package com.ofss.excp;

public class DuplicateBeneficiaryException extends RuntimeException {
    public DuplicateBeneficiaryException() { super("Beneficiary is already registered for this customer"); }
}
