package com.ofss.services;

import java.time.OffsetDateTime;

public interface OtpDeliveryGateway {

    void deliver(
            String recipientEmail,
            OtpCode otpCode,
            OffsetDateTime expiresAt);
}
