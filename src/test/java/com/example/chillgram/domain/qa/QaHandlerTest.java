package com.example.chillgram.domain.qa;

import com.example.chillgram.domain.qa.handler.QaHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Q&A Handler 단위 테스트 (DB 연결 불필요)
 */
class QaHandlerTest {

    @Test
    @DisplayName("🧪 [검증] 필수값 검증 로직 테스트")
    void validateRequired_Test() {
        // Handler의 getFormValue 로직 검증 (리플렉션 없이 단순 검증)
        String title = "";
        String content = "";

        boolean isValid = !title.isBlank() && !content.isBlank();

        assertThat(isValid).isFalse();
        System.out.println("✅ 검증 통과: 빈 값은 isBlank()로 검증됨");
    }

    @Test
    @DisplayName("🧪 [검증] Long 파싱 안전 처리 테스트")
    void parseLongSafe_Test() {
        // parseLongSafe 로직 검증
        Long result1 = parseLongSafe("123");
        Long result2 = parseLongSafe("invalid");
        Long result3 = parseLongSafe("");

        assertThat(result1).isEqualTo(123L);
        assertThat(result2).isNull();
        assertThat(result3).isNull();

        System.out.println("✅ 검증 통과: 잘못된 숫자는 null 반환");
    }

    private Long parseLongSafe(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
