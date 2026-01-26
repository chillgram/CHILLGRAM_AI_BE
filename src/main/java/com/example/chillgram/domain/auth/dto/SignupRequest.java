package com.example.chillgram.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.*;

public record SignupRequest(
        @NotNull(message = "companyId는 필수입니다.")
        @Positive(message = "companyId는 양수여야 합니다.")
        Long companyId,

        @NotBlank(message = "email은 필수입니다.")
        @Email(message = "email 형식이 올바르지 않습니다.")
        @Size(max = 30, message = "email 길이가 너무 깁니다.")
        String email,

        @NotBlank(message = "name은 필수입니다.")
        @Size(max = 8, message = "name 길이가 너무 깁니다.")
        String name,

        @NotBlank(message = "password는 필수입니다.")
        @Size(min = 8, max = 25, message = "password 길이가 올바르지 않습니다.")
        String password,

        @NotNull(message = "privacyConsent는 필수입니다.")
        @AssertTrue(message = "개인정보 수집·이용 동의는 필수입니다.")
        Boolean privacyConsent
) {}