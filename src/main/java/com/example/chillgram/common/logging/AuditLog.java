package com.example.chillgram.common.logging;

import java.lang.annotation.*;

/**
 * 감사(Audit) 로그 대상 API를 표시하는 애노테이션.
 * - 컨트롤러 메서드에 붙이면 RequestLoggingWebFilter가 실행 종료 시점에
 *   해당 요청을 "감사 로그"로 추가 기록한다.
 * - value: 기능/행위명(옵션). 미지정 시 메서드명을 사용한다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {
    String value() default "";
}
