package com.example.chillgram.domain.qa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 질문 작성 요청 DTO
 * 
 * @NotBlank: null, "", " " 모두 거부
 * @Size: 길이 제한
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QaQuestionCreateRequest {

    @NotBlank(message = "제목을 입력해주세요")
    @Size(max = 200, message = "제목은 200자 이내로 입력해주세요")
    private String title;

    @NotBlank(message = "내용을 입력해주세요")
    @Size(max = 5000, message = "내용은 5000자 이내로 입력해주세요")
    private String content;

    private Long categoryId;
    private Long companyId;
    private Long createdBy;
}
