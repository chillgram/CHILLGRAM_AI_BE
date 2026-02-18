package com.example.chillgram.domain.advertising.dto.jobs;

import java.util.Locale;
import java.util.Set;

public final class JobEnums {
    private JobEnums() {}

    public enum JobType {
        BASIC(Set.of("BASIC", "BASE", "BASE_IMAGE", "PRODUCT_IMAGE"), true),
        BANNER(Set.of("BANNER"), true),
        SNS(Set.of("SNS", "SNS_IMAGE", "INSTAGRAM"), true),
        VIDEO(Set.of("VIDEO", "SHORT", "SHORTS", "YOUTUBE"), true),
        DIELINE(Set.of("DIELINE", "PACKAGE", "PACKAGING"), true);

        /** 외부(프론트/요청)에서 들어올 수 있는 별칭들 */
        private final Set<String> aliases;

        /** 결과로 파일(이미지/비디오)을 생성하는 작업인지 */
        private final boolean producesAsset;

        JobType(Set<String> aliases, boolean producesAsset) {
            this.aliases = aliases;
            this.producesAsset = producesAsset;
        }

        public boolean producesAsset() {
            return producesAsset;
        }

        /**
         * 프론트 selectedTypes 같은 문자열을 안전하게 JobType으로 변환.
         * - null/빈값: 기본 SNS
         * - 알 수 없는 값: IllegalArgumentException(원하면 기본값으로 바꿀 수도 있음)
         */
        public static JobType from(String raw) {
            if (raw == null || raw.isBlank()) return SNS;

            String t = raw.trim().toUpperCase(Locale.ROOT);

            // 정확히 일치 우선
            for (JobType jt : values()) {
                if (jt.name().equals(t)) return jt;
            }

            // 별칭 매칭
            for (JobType jt : values()) {
                if (jt.aliases.contains(t)) return jt;
            }

            // 포함 매칭(너희가 "BANNER_1_1" 같이 보낼 때 대비)
            for (JobType jt : values()) {
                for (String a : jt.aliases) {
                    if (t.contains(a)) return jt;
                }
            }

            throw new IllegalArgumentException("Unknown job type: " + raw);
        }
    }

    public enum JobStatus {
        REQUESTED, RUNNING, SUCCEEDED, FAILED;

        public boolean isDone() {
            return this == SUCCEEDED || this == FAILED;
        }
    }
}
