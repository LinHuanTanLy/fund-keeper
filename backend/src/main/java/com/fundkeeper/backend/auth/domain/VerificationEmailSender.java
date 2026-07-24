package com.fundkeeper.backend.auth.domain;

import java.time.Duration;

public interface VerificationEmailSender {

    void send(
            String email,
            EmailCodePurpose purpose,
            String code,
            Duration validFor);
}
