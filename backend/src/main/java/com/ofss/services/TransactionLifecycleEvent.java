package com.ofss.services;

import com.ofss.beans.NotificationSeverity;
import com.ofss.beans.NotificationType;

public enum TransactionLifecycleEvent {

    PAYMENT_CREATED(
            "PAYMENT_CREATED",
            null,
            null,
            null,
            null),
    PAYMENT_PROTECTED(
            "PAYMENT_PROTECTED",
            NotificationType.PAYMENT_PROTECTED,
            NotificationSeverity.WARNING,
            "Payment protected",
            "Your payment is inside its SafePay protection window."),
    PAYMENT_RELEASED(
            "PAYMENT_RELEASED",
            NotificationType.PAYMENT_RELEASED,
            NotificationSeverity.INFO,
            "Payment released",
            "Your payment was released for SafePay's simulated settlement process."),
    OTP_REQUIRED(
            "OTP_REQUIRED",
            NotificationType.OTP_REQUIRED,
            NotificationSeverity.CRITICAL,
            "Verification required",
            "Your payment requires OTP verification before Risk Officer review."),
    OTP_ISSUED(
            "OTP_ISSUED",
            null,
            null,
            null,
            null),
    OTP_RESENT(
            "OTP_RESENT",
            null,
            null,
            null,
            null),
    OTP_VERIFICATION_DENIED(
            "OTP_VERIFICATION_DENIED",
            null,
            null,
            null,
            null),
    OTP_VERIFIED(
            "OTP_VERIFIED",
            NotificationType.OTP_VERIFIED,
            NotificationSeverity.INFO,
            "OTP verified",
            "Your payment OTP was verified successfully."),
    RISK_REVIEW_PENDING(
            "RISK_REVIEW_PENDING",
            NotificationType.RISK_REVIEW_PENDING,
            NotificationSeverity.WARNING,
            "Risk review pending",
            "Your payment is waiting for Risk Officer review."),
    RISK_REVIEW_APPROVED(
            "RISK_REVIEW_APPROVED",
            NotificationType.RISK_REVIEW_APPROVED,
            NotificationSeverity.INFO,
            "Risk review approved",
            "Your payment passed Risk Officer review and was released."),
    RISK_REVIEW_REJECTED(
            "RISK_REVIEW_REJECTED",
            NotificationType.RISK_REVIEW_REJECTED,
            NotificationSeverity.CRITICAL,
            "Risk review rejected",
            "Your payment was rejected during Risk Officer review."),
    RISK_REVERIFICATION_REQUESTED(
            "RISK_REVIEW_REVERIFICATION_REQUESTED",
            NotificationType.OTP_REQUIRED,
            NotificationSeverity.CRITICAL,
            "Verification required again",
            "Risk review requested a new OTP verification for your payment."),
    PAYMENT_CANCELLED(
            "PAYMENT_CANCELLED",
            NotificationType.PAYMENT_CANCELLED,
            NotificationSeverity.WARNING,
            "Payment cancelled",
            "Your payment was cancelled before simulated settlement."),
    PAYMENT_FAILED(
            "PAYMENT_FAILED",
            NotificationType.PAYMENT_FAILED,
            NotificationSeverity.CRITICAL,
            "Payment failed",
            "Your payment could not complete SafePay's simulated processing."),
    PAYMENT_SETTLED(
            "PAYMENT_SETTLED",
            NotificationType.PAYMENT_SETTLED,
            NotificationSeverity.INFO,
            "Payment settled",
            "Your payment completed SafePay's simulated settlement process."),
    SETTLEMENT_MANUAL_REVIEW_REQUIRED(
            "SETTLEMENT_MANUAL_REVIEW_REQUIRED",
            null,
            null,
            null,
            null);

    private final String actionCode;
    private final NotificationType notificationType;
    private final NotificationSeverity severity;
    private final String title;
    private final String message;

    TransactionLifecycleEvent(
            String actionCode,
            NotificationType notificationType,
            NotificationSeverity severity,
            String title,
            String message) {
        this.actionCode = actionCode;
        this.notificationType = notificationType;
        this.severity = severity;
        this.title = title;
        this.message = message;
    }

    public String actionCode() { return actionCode; }
    public NotificationType notificationType() { return notificationType; }
    public NotificationSeverity severity() { return severity; }
    public String title() { return title; }
    public String message() { return message; }
    public boolean createsNotification() { return notificationType != null; }
}
