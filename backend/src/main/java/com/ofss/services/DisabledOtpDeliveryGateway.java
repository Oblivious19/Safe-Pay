package com.ofss.services;

import java.time.OffsetDateTime;

public final class DisabledOtpDeliveryGateway
        implements OtpDeliveryGateway {

    @Override
    public void deliver(
            String recipientEmail,
            OtpCode otpCode,
            OffsetDateTime expiresAt) {

        throw new OtpDeliveryException(
                "OTP email delivery is unavailable");
    }
}
