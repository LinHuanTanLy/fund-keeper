package com.fundkeeper.backend.auth.infrastructure.mail;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.fundkeeper.backend.auth.application.AuthProperties;
import com.fundkeeper.backend.auth.domain.EmailCodePurpose;
import com.fundkeeper.backend.auth.domain.VerificationEmailSender;

@Component
@ConditionalOnProperty(
        name = "fundkeeper.auth.mail-sender",
        havingValue = "smtp",
        matchIfMissing = true)
public class SmtpVerificationEmailSender implements VerificationEmailSender {

    private final JavaMailSender mailSender;
    private final AuthProperties properties;

    public SmtpVerificationEmailSender(
            JavaMailSender mailSender,
            AuthProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(
            String email,
            EmailCodePurpose purpose,
            String code,
            Duration validFor) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(email);
        message.setSubject(subject(purpose));
        message.setText("""
                您的 Fund Keeper 验证码是：%s

                验证码有效期为 %d 分钟。若非本人操作，请忽略此邮件。
                """.formatted(code, validFor.toMinutes()));
        mailSender.send(message);
    }

    private String subject(EmailCodePurpose purpose) {
        return purpose == EmailCodePurpose.REGISTER
                ? "Fund Keeper 注册验证码"
                : "Fund Keeper 密码重置验证码";
    }
}
