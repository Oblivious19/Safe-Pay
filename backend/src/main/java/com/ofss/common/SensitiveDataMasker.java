package com.ofss.common;

public final class SensitiveDataMasker {

    private static final int ACCOUNT_VISIBLE_SUFFIX = 4;

    private SensitiveDataMasker() {
        // Utility class.
    }

    public static String maskAccountNumber(
            String accountNumber) {

        if (accountNumber == null
                || accountNumber.isBlank()) {

            throw new IllegalArgumentException(
                    "accountNumber is required");
        }

        if (accountNumber.length()
                <= ACCOUNT_VISIBLE_SUFFIX) {

            return "*".repeat(accountNumber.length());
        }

        int maskedLength = accountNumber.length()
                - ACCOUNT_VISIBLE_SUFFIX;

        return "*".repeat(maskedLength)
                + accountNumber.substring(maskedLength);
    }

    public static String maskUpiId(String upiId) {
        if (upiId == null || upiId.isBlank()) {
            throw new IllegalArgumentException(
                    "upiId is required");
        }

        String normalizedValue = upiId.trim();
        int separatorIndex = normalizedValue.indexOf('@');

        if (separatorIndex <= 0
                || separatorIndex
                        != normalizedValue.lastIndexOf('@')
                || separatorIndex
                        == normalizedValue.length() - 1) {

            return "*".repeat(normalizedValue.length());
        }

        String handle = normalizedValue.substring(
                0,
                separatorIndex);

        String provider = normalizedValue.substring(
                separatorIndex + 1);

        String maskedHandle;

        if (handle.length() <= 2) {
            maskedHandle = "*".repeat(handle.length());
        } else {
            maskedHandle = handle.substring(0, 1)
                    + "*".repeat(handle.length() - 2)
                    + handle.substring(handle.length() - 1);
        }

        return maskedHandle + "@" + provider;
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "email is required");
        }

        String normalizedValue = email.trim();
        int separatorIndex = normalizedValue.indexOf('@');

        if (separatorIndex <= 0
                || separatorIndex
                        != normalizedValue.lastIndexOf('@')
                || separatorIndex
                        == normalizedValue.length() - 1) {
            throw new IllegalArgumentException(
                    "email is invalid");
        }

        String localPart = normalizedValue.substring(
                0,
                separatorIndex);
        String domain = normalizedValue.substring(
                separatorIndex);

        return localPart.substring(0, 1)
                + "*".repeat(Math.max(1, localPart.length() - 1))
                + domain;
    }
}
