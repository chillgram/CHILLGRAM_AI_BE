package com.example.chillgram.domain.qa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 답변 작성 요청 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QaAnswerCreateRequest {

    @NotBlank(message = "답변 내용을 입력해주세요")
    @Size(max = 5000, message = "답변은 5000자 이내로 입력해주세요")
    private String body;

    private Long companyId;
    private Long answeredBy;
}
