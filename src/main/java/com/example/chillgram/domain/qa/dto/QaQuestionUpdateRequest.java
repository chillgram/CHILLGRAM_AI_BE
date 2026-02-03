package com.example.chillgram.domain.qa.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 질문 수정 요청 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "질문 수정 요청")
public class QaQuestionUpdateRequest {

    @NotBlank(message = "제목을 입력해주세요")
    @Size(max = 200, message = "제목은 200자 이내로 입력해주세요")
    @Schema(description = "수정할 제목", example = "수정된 질문 제목")
    private String title;

    @NotBlank(message = "내용을 입력해주세요")
    @Size(max = 5000, message = "내용은 5000자 이내로 입력해주세요")
    @Schema(description = "수정할 내용", example = "수정된 질문 내용입니다.")
    private String content;
}
