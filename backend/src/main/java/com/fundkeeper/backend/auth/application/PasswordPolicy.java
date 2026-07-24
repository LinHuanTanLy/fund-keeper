package com.fundkeeper.backend.auth.application;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import com.fundkeeper.backend.shared.exception.BusinessException;
import com.fundkeeper.backend.shared.exception.ErrorCode;

@Component
public class PasswordPolicy {

    private static final int MINIMUM_CHARACTERS = 8;
    private static final int MAXIMUM_BCRYPT_BYTES = 72;

    public void validate(String password) {
        int encodedLength = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.length() < MINIMUM_CHARACTERS || encodedLength > MAXIMUM_BCRYPT_BYTES) {
            throw new BusinessException(
                    ErrorCode.PASSWORD_POLICY_VIOLATION,
                    "密码长度至少为8个字符，且编码后不能超过72字节");
        }
    }
}
