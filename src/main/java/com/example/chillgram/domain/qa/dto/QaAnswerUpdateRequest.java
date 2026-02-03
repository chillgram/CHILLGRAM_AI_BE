package com.example.chillgram.domain.qa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 답변 수정 요청 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "답변 수정 요청")
public class QaAnswerUpdateRequest {

    @NotBlank(message = "답변 내용을 입력해주세요")
    @Size(max = 5000, message = "답변은 5000자 이내로 입력해주세요")
    @Schema(description = "수정할 답변 내용", example = "수정된 답변 내용입니다.")
    private String body;
}
