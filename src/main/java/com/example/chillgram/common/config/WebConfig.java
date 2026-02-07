package com.example.chillgram.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * WebFlux 설정 클래스
 * 정적 리소스 핸들링 등을 설정합니다.
 */
@Configuration
public class WebConfig implements WebFluxConfigurer {

    /**
     * 정적 리소스 핸들러 추가
     * 외부 경로에 저장된 Q&A 이미지를 서빙하기 위한 설정입니다.
     * 
     * @param registry 리소스 핸들러 레지스트리
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 클라이언트가 /qna/** 로 요청하면
        // 서버의 /app/uploads/qna/ 디렉토리에서 해당 파일을 찾아 반환합니다.
        // file:/// 접두어는 로컬 파일 시스템을 의미합니다.
        registry.addResourceHandler("/qna/**")
                .addResourceLocations("file:///app/uploads/qna/");

        // 프로젝트 이미지 정적 파일 서빙
        registry.addResourceHandler("/projects/**")
                .addResourceLocations("file:///app/uploads/projects/");
    }
}
