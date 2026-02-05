package com.example.chillgram.domain.advertising.engine;

import com.example.chillgram.domain.advertising.repository.EventCalendarRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 광고 트렌드 규칙 엔진 기본 구현
 */
@Component
public class DefaultTrendRuleEngine implements TrendRuleEngine {

    @Override
    public TrendResult analyze(long productId, LocalDate baseDate, List<EventCalendarRepository.EventRow> events) {
        Set<TrendKeyword> keywords = new LinkedHashSet<>();
        Set<String> hashtags = new LinkedHashSet<>();

        for (var e : events) {
            String n = e.name() == null ? "" : e.name();

            if (containsAny(n, "설", "추석")) {
                keywords.add(new TrendKeyword("선물", "명절 선물/세트 수요"));
                keywords.add(new TrendKeyword("프리미엄", "세트 고급화 트렌드"));
                hashtags.add("#선물세트");
                hashtags.add("#프리미엄");
            }
            if (containsAny(n, "크리스마스", "연말")) {
                keywords.add(new TrendKeyword("파티", "연말 모임/파티 수요"));
                hashtags.add("#크리스마스");
                hashtags.add("#연말");
            }
            if (containsAny(n, "어린이날")) {
                keywords.add(new TrendKeyword("키즈", "가족/키즈 타겟 수요"));
                hashtags.add("#어린이날");
                hashtags.add("#키즈");
            }
            if (containsAny(n, "새해")) {
                keywords.add(new TrendKeyword("건강", "새해 다짐/헬시 트렌드"));
                hashtags.add("#새해다짐");
                hashtags.add("#건강");
            }
        }

        // 화면이 비면 실패다. 최소 기본 세트는 보장한다.
        if (keywords.isEmpty()) {
            keywords.add(new TrendKeyword("건강", "건강을 중시하는 트렌드"));
            keywords.add(new TrendKeyword("친환경", "지속가능 소비 트렌드"));
            keywords.add(new TrendKeyword("프리미엄", "고급화 트렌드"));
            hashtags.add("#건강");
            hashtags.add("#친환경");
            hashtags.add("#프리미엄");
        }

        String styleSummary = "미니멀 구성과 자연친화 톤이 강세입니다.";

        return new TrendResult(List.copyOf(keywords), List.copyOf(hashtags), styleSummary);
    }

    private static boolean containsAny(String s, String... tokens) {
        for (String t : tokens) {
            if (s.contains(t)) return true;
        }
        return false;
    }
}
